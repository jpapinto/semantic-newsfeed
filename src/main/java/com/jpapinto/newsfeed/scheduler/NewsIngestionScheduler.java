package com.jpapinto.newsfeed.scheduler;

import com.jpapinto.newsfeed.dto.RawNewsItem;
import com.jpapinto.newsfeed.model.Article;
import com.jpapinto.newsfeed.model.Category;
import com.jpapinto.newsfeed.repository.ArticleRepository;
import com.jpapinto.newsfeed.service.AiClassificationService;
import com.jpapinto.newsfeed.service.RssFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsIngestionScheduler {

    private final RssFeedService rssFeedService;
    private final AiClassificationService aiClassificationService;
    private final ArticleRepository articleRepository;
    private final VectorStore vectorStore;

    private static final List<String> TARGET_FEEDS = List.of(
            "https://www.reddit.com/r/technology/top/.rss?t=day",
            "https://feeds.arstechnica.com/arstechnica/technology-lab"
    );

    // 14,400,000 milliseconds = 4 hours to avoid hitting API rate limits. Initial delay of 5 seconds to allow app startup.
    @Scheduled(initialDelay = 5000, fixedDelay = 14400000)
    public void runIngestionPipeline() {
        log.info("Starting scheduled news ingestion pipeline...");

        for (String feedUrl : TARGET_FEEDS) {
            try {
                List<RawNewsItem> allItems = rssFeedService.fetchFeed(feedUrl);
                int savedCount = 0;
                
                for (RawNewsItem item : allItems) {
                    if (savedCount >= 5) {
                        log.info("Reached the safety limit of 5 articles for {}. The rest will be processed in the next cycle.", feedUrl);
                        break; 
                    }
                    
                    if (articleRepository.existsByUrl(item.url())) {
                        log.info("Skipping duplicate article (already in DB): {}", item.title());
                        continue; 
                    }

                    log.info("Processing NEW article: {}", item.title());
                    
                    // 1. Classification
                    Category category = aiClassificationService.classifyNews(item.title(), item.summary());
                    log.info("--> AI Classified as: {}", category.name());

                    // 2. Save Relational Data
                    Article article = Article.builder()
                            .title(item.title())
                            .url(item.url())
                            .summary(item.summary())
                            .category(category)
                            .publishedAt(item.publishedAt())
                            .build();
                    
                    Article savedArticle = articleRepository.save(article);

                    // 3. Save Vector Embedding & The Circuit Breaker
                    try {
                        String vectorContent = item.title() + "\n" + item.summary();
                        Document vectorDoc = new Document(vectorContent, Map.of(
                                "articleId", savedArticle.getId().toString(),
                                "category", category.name()
                        ));
                        
                        vectorStore.add(List.of(vectorDoc)); 
                        log.info("--> Successfully generated and saved AI vector embedding.");
                        savedCount++;
                    } catch (Exception e) {
                        // Any embedding failure (typically HTTP 429 once the daily quota is used up) trips the breaker.
                        // Remove the article we just saved: otherwise the next cycle would skip it as a duplicate
                        // and it would stay in the database forever without an embedding, invisible to search.
                        articleRepository.delete(savedArticle);
                        log.error("--> Embedding API call failed ({}). Tripping the circuit breaker: halting this feed until the next cycle.",
                                e.getMessage());
                        break; // Stop calling the API and wait for the next scheduled run.
                    }
                }
                log.info("Processed {}. Found {} total, saved {} new articles.", feedUrl, allItems.size(), savedCount);
            } catch (Exception e) {
                log.error("Pipeline failed for source {}: {}", feedUrl, e.getMessage());
            }
        }
        log.info("Finished news ingestion pipeline.");
    }
}