package com.smartgym.membership.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.smartgym.membership.payment.Payment;

public record PaymentResponse(
        UUID id,
        UUID memberId,
        UUID subscriptionId,
        long amountCents,
        String currency,
        Payment.Method method,
        Payment.Status status,
        UUID correctsPaymentId,
        String reason,
        Instant paidAt,
        Instant createdAt,
        List<PaymentResponse> corrections) {
    public static PaymentResponse from(Payment payment, List<PaymentResponse> corrections) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMemberId(),
                payment.getSubscriptionId(),
                payment.getAmountCents(),
                payment.getCurrency().trim(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getCorrectsPaymentId(),
                payment.getReason(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                List.copyOf(corrections));
    }
}
