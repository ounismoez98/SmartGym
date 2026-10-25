package com.smartgym.membership.feedback;

public enum FeedbackSentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE;

    public static FeedbackSentiment fromString(String value) {
        if (value == null || value.isBlank()) {
            return NEUTRAL;
        }
        try {
            return FeedbackSentiment.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String clean = value.trim().toLowerCase();
            if (clean.contains("pos")) {
                return POSITIVE;
            } else if (clean.contains("neg")) {
                return NEGATIVE;
            }
            return NEUTRAL;
        }
    }
}
