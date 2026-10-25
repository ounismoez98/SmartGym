package com.smartgym.membership.api;

import java.time.Instant;
import java.util.UUID;

import com.smartgym.membership.payment.Payment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PaymentCreateRequest(
        @NotNull UUID memberId,
        UUID subscriptionId,
        @Positive long amountCents,
        @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotNull Payment.Method method,
        Instant paidAt) {
    public PaymentCreateRequest {
        currency = currency == null ? "TND" : currency;
    }
}
