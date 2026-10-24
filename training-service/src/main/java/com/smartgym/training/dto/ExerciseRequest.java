package com.smartgym.training.dto;

import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExerciseRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1200) String description,
        @NotBlank @Size(max = 3000) String instructions,
        @NotNull MuscleGroup primaryMuscleGroup,
        @NotNull ExerciseDifficulty difficulty,
        @NotNull EquipmentCategory equipmentCategory) {}
