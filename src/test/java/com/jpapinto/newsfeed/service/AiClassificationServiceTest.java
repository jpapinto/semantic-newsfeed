package com.jpapinto.newsfeed.service;

import com.jpapinto.newsfeed.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiClassificationServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec; 

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private AiClassificationService aiClassificationService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiClassificationService = new AiClassificationService(chatClientBuilder);
    }

    @Test
    @DisplayName("Should successfully map a clean AI response to the correct Enum")
    void classifyNews_Success() {
        // Arrange: Mock the fluent ChatClient API
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        
        // AI returns exact match with extra spaces and quotes just to test our cleanup logic
        when(callResponseSpec.content()).thenReturn(" \"CYBERSECURITY\"  ");

        // Act
        Category result = aiClassificationService.classifyNews("Test Title", "Test Summary");

        // Assert
        assertEquals(Category.CYBERSECURITY, result);
    }

    @Test
    @DisplayName("Should gracefully fallback to OTHER when AI returns invalid format")
    void classifyNews_FallbackToOtherOnInvalidFormat() {
        // Arrange: Mock the fluent ChatClient API
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        
        // AI disobeys instructions and returns a full sentence instead of just the enum
        when(callResponseSpec.content()).thenReturn("I think this article is about hacking.");

        // Act
        Category result = aiClassificationService.classifyNews("Hack Title", "Hack Summary");

        // Assert
        assertEquals(Category.OTHER, result);
    }
}