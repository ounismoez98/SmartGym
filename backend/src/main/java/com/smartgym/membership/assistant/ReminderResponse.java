package com.smartgym.membership.assistant;

import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        UUID memberId,
        UUID subscriptionId,
        String subject,
        String body,
        ReminderDraft.Status status,
        Instant createdAt,
        Instant sentAt) {
    public static ReminderResponse from(ReminderDraft draft) {
        return new ReminderResponse(
                draft.getId(),
                draft.getMemberId(),
                draft.getSubscriptionId(),
                draft.getSubject(),
                draft.getBody(),
                draft.getStatus(),
                draft.getCreatedAt(),
                draft.getSentAt());
    }
}
