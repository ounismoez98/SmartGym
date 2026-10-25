package com.smartgym.membership.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SubscriptionPlanUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String conditions,
        @Positive int durationDays,
        @PositiveOrZero long priceCents,
        @NotNull @Pattern(regexp = "[A-Za-z]{3}") String currency,
        boolean active) {}
