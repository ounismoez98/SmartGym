package com.smartgym.membership.feedback;

public enum FeedbackCategory {
    CLEANLINESS("Cleanliness"),
    EQUIPMENT("Equipment"),
    CLASSES("Classes"),
    STAFF("Staff"),
    SERVICE("Service");

    private final String displayName;

    FeedbackCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
