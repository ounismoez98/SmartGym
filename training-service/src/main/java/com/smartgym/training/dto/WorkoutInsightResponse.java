package com.smartgym.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkoutInsightResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        long completedWorkouts,
        long recordedSets,
        long repetitions,
        BigDecimal loadedVolumeKgRepetitions,
        String answer,
        String encouragement) {}
