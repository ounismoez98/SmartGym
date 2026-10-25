package com.smartgym.training.ai;

public class WorkoutInsightUnavailableException extends RuntimeException {
    public WorkoutInsightUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkoutInsightUnavailableException(String message) {
        super(message);
    }
}
