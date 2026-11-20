package com.jpapinto.newsfeed.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Category {
    CYBERSECURITY("Cybersecurity"),
    AI_AND_EMERGING_TECH("Artificial Intelligence & Emerging Tech"),
    SOFTWARE_AND_DEVELOPMENT("Software & Development"),
    HARDWARE_AND_DEVICES("Hardware & Devices"),
    TECH_INDUSTRY_AND_BUSINESS("Tech Industry & Business"),
    OTHER("Other");

    private final String value;

    Category(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}