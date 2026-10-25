package com.smartgym.training.messaging;

public record ProgrammeGenerationResult(String outputJson, String failureReason) {
    public boolean succeeded() {
        return outputJson != null && failureReason == null;
    }
}
