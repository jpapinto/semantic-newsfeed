package com.jpapinto.newsfeed.controller;

import com.jpapinto.newsfeed.model.Article;
import com.jpapinto.newsfeed.model.Category;
import com.jpapinto.newsfeed.repository.ArticleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NewsController.class)
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleRepository articleRepository;

    @MockitoBean
    private VectorStore vectorStore;

    @Test
    @DisplayName("GET /api/news/{id} - Should return 200 OK when article exists")
    void getArticleById_Success() throws Exception {
        // Arrange
        UUID articleId = UUID.randomUUID();
        Article mockArticle = Article.builder()
                .id(articleId)
                .title("Test Article")
                .summary("Test Summary")
                .url("http://test.com")
                .category(Category.CYBERSECURITY)
                .publishedAt(OffsetDateTime.now())
                .build();

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(mockArticle));

        // Act & Assert
        mockMvc.perform(get("/api/news/" + articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Article"))
                .andExpect(jsonPath("$.category").value("Cybersecurity")); 
    }

    @Test
    @DisplayName("GET /api/news/{id} - Should return 404 Not Found when article does not exist")
    void getArticleById_NotFound() throws Exception {
        // Arrange
        UUID fakeId = UUID.randomUUID();
        when(articleRepository.findById(fakeId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/news/" + fakeId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/news/search - Should calculate similarity score and return 200 OK")
    void searchNews_Success() throws Exception {
        // Arrange
        UUID articleId = UUID.randomUUID();
        
        // Mock a Vector Document returned by AI (Distance of 0.2 means 0.8 or 80% similarity)
        Document mockDoc = new Document("Test Content", Map.of(
                "articleId", articleId.toString(),
                "distance", 0.2 
        ));
        
        Article mockArticle = Article.builder()
                .id(articleId)
                .title("AI Search Result")
                .category(Category.AI_AND_EMERGING_TECH)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(mockDoc));
        when(articleRepository.findAllById(List.of(articleId))).thenReturn(List.of(mockArticle));

        // Act & Assert
        mockMvc.perform(get("/api/news/search?query=test ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].article.title").value("AI Search Result"))
                .andExpect(jsonPath("$[0].similarityScore").value(0.8000));
    }
}