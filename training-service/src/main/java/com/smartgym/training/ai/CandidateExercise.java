package com.smartgym.training.ai;

import java.util.UUID;

import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.MuscleGroup;

public record CandidateExercise(
        UUID id,
        String name,
        String instructions,
        MuscleGroup primaryMuscleGroup,
        ExerciseDifficulty difficulty,
        EquipmentCategory equipmentCategory) {}
