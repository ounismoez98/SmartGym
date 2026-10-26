package com.smartgym.training.ai;

public interface WorkoutInsightGenerator {
    String answer(String question, WorkoutInsightContext context);
}
