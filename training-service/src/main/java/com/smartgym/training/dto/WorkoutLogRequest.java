package com.smartgym.training.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkoutLogRequest(
        UUID programmeId,
        UUID programmeSessionId,
        @NotNull OffsetDateTime performedAt,
        @Size(max = 2000) String notes,
        @NotEmpty @Size(max = 100) List<@Valid WorkoutSetRequest> sets) {}
