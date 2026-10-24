package com.smartgym.training.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.Exercise;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.MuscleGroup;

public record ExerciseResponse(
        UUID id,
        String name,
        String description,
        String instructions,
        MuscleGroup primaryMuscleGroup,
        ExerciseDifficulty difficulty,
        EquipmentCategory equipmentCategory,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
    public static ExerciseResponse from(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getDescription(),
                exercise.getInstructions(),
                exercise.getPrimaryMuscleGroup(),
                exercise.getDifficulty(),
                exercise.getEquipmentCategory(),
                exercise.isActive(),
                exercise.getCreatedAt(),
                exercise.getUpdatedAt(),
                exercise.getVersion());
    }
}
