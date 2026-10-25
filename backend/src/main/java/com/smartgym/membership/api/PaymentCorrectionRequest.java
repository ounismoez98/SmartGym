package com.smartgym.membership.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentCorrectionRequest(
        @Positive long amountCents,
        @NotBlank @Size(max = 1000) String reason) {}
