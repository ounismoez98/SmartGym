package com.smartgym.training.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkoutSetRequest(
        @NotNull UUID exerciseId,
        @Min(1) @Max(100) Integer repetitions,
        @Min(1) @Max(7200) Integer durationSeconds,
        @DecimalMin("0.01") @DecimalMax("99999.99") BigDecimal loadKg,
        @Size(max = 500) String notes) {}
