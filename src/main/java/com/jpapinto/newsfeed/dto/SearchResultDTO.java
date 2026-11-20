package com.jpapinto.newsfeed.dto;

import com.jpapinto.newsfeed.model.Article;

/**
 * Data Transfer Object for returning an Article alongside its AI semantic similarity score.
 */
public record SearchResultDTO(
        Article article, 
        Double similarityScore
) {}