package com.jpapinto.newsfeed.scheduler;

import com.jpapinto.newsfeed.dto.RawNewsItem;
import com.jpapinto.newsfeed.model.Article;
import com.jpapinto.newsfeed.model.Category;
import com.jpapinto.newsfeed.repository.ArticleRepository;
import com.jpapinto.newsfeed.service.AiClassificationService;
import com.jpapinto.newsfeed.service.RssFeedService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsIngestionSchedulerTest {

    @Mock
    private RssFeedService rssFeedService;

    @Mock
    private AiClassificationService aiClassificationService;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private NewsIngestionScheduler scheduler;

    private static RawNewsItem item(String n) {
        return new RawNewsItem("Title " + n, "https://example.com/" + n, "Summary " + n, OffsetDateTime.now());
    }

    private void stubSaveWithId() {
        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> {
            Article a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
    }

    @Test
    @DisplayName("Embeds and keeps every new article when the embedding API works")
    void ingestion_Success() {
        when(rssFeedService.fetchFeed(anyString())).thenReturn(List.of(item("a"), item("b")));
        when(articleRepository.existsByUrl(anyString())).thenReturn(false);
        when(aiClassificationService.classifyNews(anyString(), anyString())).thenReturn(Category.CYBERSECURITY);
        stubSaveWithId();

        scheduler.runIngestionPipeline();

        // 2 feeds x 2 items each
        verify(vectorStore, times(4)).add(anyList());
        verify(articleRepository, never()).delete(any(Article.class));
    }

    @Test
    @DisplayName("Removes the saved article and stops the feed when the embedding call fails")
    void ingestion_EmbeddingFailure_RollsBackArticleAndTripsBreaker() {
        when(rssFeedService.fetchFeed(anyString())).thenReturn(List.of(item("a"), item("b")));
        when(articleRepository.existsByUrl(anyString())).thenReturn(false);
        when(aiClassificationService.classifyNews(anyString(), anyString())).thenReturn(Category.OTHER);
        stubSaveWithId();
        doThrow(new RuntimeException("429 Too Many Requests")).when(vectorStore).add(anyList());

        scheduler.runIngestionPipeline();

        // Each feed stops at its first failure: one save, one rollback, one embedding attempt per feed.
        verify(articleRepository, times(2)).save(any(Article.class));
        verify(articleRepository, times(2)).delete(any(Article.class));
        verify(vectorStore, times(2)).add(anyList());
    }
}
