package com.smartgym.training.dto;

import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.MuscleGroup;
import jakarta.validation.constraints.Size;

public record ExercisePatchRequest(
        @Size(max = 120) String name,
        @Size(max = 1200) String description,
        @Size(max = 3000) String instructions,
        MuscleGroup primaryMuscleGroup,
        ExerciseDifficulty difficulty,
        EquipmentCategory equipmentCategory,
        Long version) {}
