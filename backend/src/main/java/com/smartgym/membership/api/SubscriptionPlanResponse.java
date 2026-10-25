package com.smartgym.membership.api;

import java.time.Instant;
import java.util.UUID;

import com.smartgym.membership.profile.SubscriptionPlan;

public record SubscriptionPlanResponse(
        UUID id,
        String code,
        String name,
        String conditions,
        int durationDays,
        long priceCents,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getDurationDays(),
                plan.getPriceCents(),
                plan.getCurrency(),
                plan.isActive(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }
}
