package com.smartgym.training.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record WorkoutLogPatchRequest(
        OffsetDateTime performedAt,
        @Size(max = 2000) String notes,
        @Size(min = 1, max = 100) List<@Valid WorkoutSetRequest> sets,
        @Min(0) Long version) {}
