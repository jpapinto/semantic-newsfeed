package com.jpapinto.newsfeed.dto;

import java.time.OffsetDateTime;

public record RawNewsItem(
        String title,
        String url,
        String summary,
        OffsetDateTime publishedAt
) {}