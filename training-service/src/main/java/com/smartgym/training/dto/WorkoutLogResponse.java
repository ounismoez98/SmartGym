package com.smartgym.training.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.smartgym.training.domain.WorkoutLog;

public record WorkoutLogResponse(
        UUID id,
        UUID memberId,
        UUID programmeId,
        UUID programmeSessionId,
        OffsetDateTime performedAt,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version,
        List<Set> sets) {
    public static WorkoutLogResponse from(WorkoutLog log) {
        return new WorkoutLogResponse(
                log.getId(),
                log.getMemberId(),
                log.getProgrammeId(),
                log.getProgrammeSessionId(),
                log.getPerformedAt(),
                log.getNotes(),
                log.getCreatedAt(),
                log.getUpdatedAt(),
                log.getVersion(),
                log.getSets().stream()
                        .sorted(java.util.Comparator.comparingInt(
                                com.smartgym.training.domain.WorkoutSet::getSetNumber))
                        .map(set -> new Set(
                                set.getExerciseId(),
                                set.getSetNumber(),
                                set.getRepetitions(),
                                set.getDurationSeconds(),
                                set.getLoadKg(),
                                set.getNotes()))
                        .toList());
    }

    public record Set(
            UUID exerciseId,
            int setNumber,
            Integer repetitions,
            Integer durationSeconds,
            BigDecimal loadKg,
            String notes) {}
}
