package com.smartgym.membership.api;

import java.time.Instant;
import java.util.UUID;

import com.smartgym.membership.profile.MemberSubscription;

public record SubscriptionResponse(
        UUID id,
        UUID memberId,
        UUID planId,
        Instant startsAt,
        Instant endsAt,
        MemberSubscription.Status status,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt) {
    public static SubscriptionResponse from(MemberSubscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getMemberId(),
                subscription.getPlanId(),
                subscription.getStartsAt(),
                subscription.getEndsAt(),
                subscription.getStatus(),
                subscription.getCancelledAt(),
                subscription.getCancelReason(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }
}
