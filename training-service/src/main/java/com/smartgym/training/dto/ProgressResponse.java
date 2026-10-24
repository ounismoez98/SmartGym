package com.smartgym.training.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ProgressResponse(
        UUID memberId,
        long completedWorkouts,
        long totalRepetitions,
        BigDecimal trainingVolumeKgRepetitions,
        Map<UUID, BigDecimal> maximumRecordedLoadKgByExercise,
        Map<String, Long> completedWorkoutsByIsoWeek) {}
