package com.smartgym.training.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkoutInsightContext(
        LocalDate periodStart,
        LocalDate periodEnd,
        long completedWorkouts,
        long recordedSets,
        long repetitions,
        BigDecimal loadedVolumeKgRepetitions) {}
