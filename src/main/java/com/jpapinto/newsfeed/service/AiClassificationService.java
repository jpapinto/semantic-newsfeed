package com.jpapinto.newsfeed.service;

import com.jpapinto.newsfeed.model.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiClassificationService {

    private final ChatClient chatClient;

    public AiClassificationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public Category classifyNews(String title, String summary) {
        // We explicitly tell the AI to return ONLY the exact enum string
        String prompt = String.format(
            "Analyze the following IT news article. Based on its title and summary, classify it strictly into ONE of these exact categories: " +
            "CYBERSECURITY, AI_AND_EMERGING_TECH, SOFTWARE_AND_DEVELOPMENT, HARDWARE_AND_DEVICES, TECH_INDUSTRY_AND_BUSINESS, OTHER. " +
            "Return ONLY the exact category name text and absolutely nothing else. \nTitle: %s\nSummary: %s",
            title, summary
        );

        try {
            // Call the AI and get standard text back instead of forcing JSON
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            // Clean up the response just in case the AI added spaces or quotes
            String cleanResponse = response.trim().replaceAll("\"", "").toUpperCase();
            
            return Category.valueOf(cleanResponse);
            
        } catch (IllegalArgumentException e) {
            log.warn("AI returned an invalid category format. Defaulting to OTHER.");
            return Category.OTHER;
        } catch (Exception e) {
            log.error("Failed to classify article: {}. Defaulting to OTHER.", title);
            return Category.OTHER;
        }
    }
}