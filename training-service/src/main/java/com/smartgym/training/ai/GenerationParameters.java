package com.smartgym.training.ai;

import java.util.List;

import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ExperienceLevel;

public record GenerationParameters(
        String goal,
        ExperienceLevel experienceLevel,
        int trainingDaysPerWeek,
        int maxSessionDurationMinutes,
        List<EquipmentCategory> availableEquipment,
        String exercisePreferences,
        String nonMedicalConstraints) {
    public GenerationParameters {
        availableEquipment = availableEquipment == null ? List.of() : List.copyOf(availableEquipment);
    }
}
