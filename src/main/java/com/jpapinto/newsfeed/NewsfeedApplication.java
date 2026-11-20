package com.jpapinto.newsfeed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Newsfeed Service.
 * * Includes @EnableScheduling to allow for periodic RSS polling
 * and @EnableRetry to handle transient network or API failures gracefully.
 */
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class NewsfeedApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsfeedApplication.class, args);
    }

}