package com.jpapinto.newsfeed.controller;

import com.jpapinto.newsfeed.dto.SearchResultDTO;
import com.jpapinto.newsfeed.model.Article;
import com.jpapinto.newsfeed.model.Category;
import com.jpapinto.newsfeed.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final ArticleRepository articleRepository;
    private final VectorStore vectorStore;

    /**
     * Endpoint 1: Retrieve a specific article by its UUID
     * GET /api/news/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Article> getArticleById(@PathVariable UUID id) {
        log.info("Fetching article with ID: {}", id);
        return articleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Endpoint 2: Semantic Search with Optional Metadata Filtering
     * GET /api/news/search?query=latest AI trends&category=Cybersecurity
     */
    @GetMapping("/search")
    public ResponseEntity<List<SearchResultDTO>> searchNews(
            @RequestParam String query,
            @RequestParam(required = false) String category) { // Accepts String to fix the 400 error
        
        log.info("Performing semantic search for query: '{}', category filter: {}", query, category);

        // 1. Build the Search Request (Only Top 3 matches)
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(3);

        // 2. Safely parse the category and apply the filter
        if (category != null && !category.isBlank()) {
            try {
                // Forces "Cybersecurity" or "cybersecurity" to become "CYBERSECURITY"
                String cleanCategory = category.trim().toUpperCase().replace(" ", "_");
                Category enumCategory = Category.valueOf(cleanCategory);
                
                String filterExpression = String.format("category == '%s'", enumCategory.name());
                requestBuilder.filterExpression(filterExpression);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid category provided: {}. Returning Bad Request.", category);
                return ResponseEntity.badRequest().build();
            }
        }

        // 3. Execute the Vector Search
        List<Document> searchResults = vectorStore.similaritySearch(requestBuilder.build());

        // 4. Extract UUIDs
        List<UUID> articleIds = searchResults.stream()
                .map(doc -> UUID.fromString(doc.getMetadata().get("articleId").toString()))
                .collect(Collectors.toList());

        // Fetch from DB (Warning: findAllById ruins the AI's sorted order)
        List<Article> dbArticles = articleRepository.findAllById(articleIds);
        
        // Put DB articles in a Map for quick lookup so we can restore the correct order
        Map<UUID, Article> articleMap = dbArticles.stream()
                .collect(Collectors.toMap(Article::getId, a -> a));

        // 5. Build the final response, preserving the AI's exact sorted order and adding the score
        List<SearchResultDTO> finalResults = searchResults.stream()
                .filter(doc -> articleMap.containsKey(UUID.fromString(doc.getMetadata().get("articleId").toString())))
                .map(doc -> {
                    UUID id = UUID.fromString(doc.getMetadata().get("articleId").toString());
                    Article article = articleMap.get(id);
                    
                    // pgvector stores "distance". Similarity is (1 - distance).
                    Object distanceObj = doc.getMetadata().get("distance");
                    Double distance = distanceObj instanceof Number ? ((Number) distanceObj).doubleValue() : 0.0;
                    
                    // Calculate similarity score and round to 4 decimal places
                    Double similarityScore = Math.round((1.0 - distance) * 10000.0) / 10000.0;
                    
                    return new SearchResultDTO(article, similarityScore);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(finalResults);
    }
}