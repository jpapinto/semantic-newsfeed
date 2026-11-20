package com.jpapinto.newsfeed.service;

import com.jpapinto.newsfeed.dto.RawNewsItem;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RssFeedService {

    private static final String USER_AGENT = "Newsfeed-Bot/1.0";

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public List<RawNewsItem> fetchFeed(String feedUrl) {
        List<RawNewsItem> items = new ArrayList<>();
        try {
            log.info("Fetching RSS feed: {}", feedUrl);
            URL url = URI.create(feedUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(connection));

            for (SyndEntry entry : feed.getEntries()) {
                String summary = entry.getDescription() != null ? entry.getDescription().getValue() : "";
                
                OffsetDateTime pubDate = entry.getPublishedDate() != null 
                        ? entry.getPublishedDate().toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime() 
                        : OffsetDateTime.now();

                // 1. Strip HTML tags
                String cleanSummary = summary.replaceAll("<[^>]*>", "").trim();
                
                // 2. Updated Limit: 2000 chars for better AI context
                if (cleanSummary.length() > 2000) {
                    cleanSummary = cleanSummary.substring(0, 2000) + "...";
                }
                
                items.add(new RawNewsItem(entry.getTitle(), entry.getLink(), cleanSummary, pubDate));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch from {}. Retrying... Error: {}", feedUrl, e.getMessage());
            throw new RuntimeException("RSS Fetch Failed", e);
        }
        return items;
    }
}