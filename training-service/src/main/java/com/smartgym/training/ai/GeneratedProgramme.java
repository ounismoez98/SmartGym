package com.smartgym.training.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GeneratedProgramme(
        String title,
        String goal,
        int durationWeeks,
        List<GeneratedSession> sessions) {
    public record GeneratedSession(
            int dayOrder,
            String title,
            String notes,
            List<GeneratedItem> exercises) {}

    public record GeneratedItem(
            int orderIndex,
            UUID exerciseId,
            int sets,
            Integer repetitions,
            Integer durationSeconds,
            int restSeconds,
            BigDecimal suggestedLoadKg,
            String notes) {}
}
