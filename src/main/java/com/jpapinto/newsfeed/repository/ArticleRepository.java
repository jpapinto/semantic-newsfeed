package com.jpapinto.newsfeed.repository;

import com.jpapinto.newsfeed.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID> {
    // This will be used by the scheduler to filter out duplicates
    boolean existsByUrl(String url);
}