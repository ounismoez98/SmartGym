package com.smartgym.membership.feedback;

public enum FeedbackUrgency {
    CRITICAL,
    MEDIUM,
    LOW;

    public static FeedbackUrgency fromString(String value) {
        if (value == null || value.isBlank()) {
            return LOW;
        }
        try {
            return FeedbackUrgency.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LOW;
        }
    }
}
