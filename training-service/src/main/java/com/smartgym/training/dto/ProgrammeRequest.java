package com.smartgym.training.dto;

import java.util.List;
import java.util.UUID;

import com.smartgym.training.domain.ExperienceLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProgrammeRequest(
        @NotNull UUID memberId,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 500) String goal,
        @NotNull ExperienceLevel experienceLevel,
        @Min(1) @Max(52) int durationWeeks,
        @NotEmpty @Size(max = 14) List<@Valid ProgrammeSessionRequest> sessions,
        @Min(0) Long version) {}
