package com.smartgym.training.dto;

import java.util.List;
import java.util.UUID;

import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ExperienceLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProgrammeGenerationRequest(
        @NotNull UUID memberId,
        @NotBlank @Size(max = 500) String goal,
        @NotNull ExperienceLevel experienceLevel,
        @Min(1) @Max(7) int trainingDaysPerWeek,
        @Min(15) @Max(240) int maxSessionDurationMinutes,
        @Size(max = 8) List<@NotNull EquipmentCategory> availableEquipment,
        @Size(max = 500) String exercisePreferences,
        @Size(max = 500) String nonMedicalConstraints) {
    public ProgrammeGenerationRequest {
        availableEquipment = availableEquipment == null ? List.of() : List.copyOf(availableEquipment);
    }
}
