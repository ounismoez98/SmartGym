package com.smartgym.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkoutInsightRequest(
        @NotBlank @Size(max = 300) String question) {}
