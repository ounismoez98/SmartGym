package com.smartgym.membership.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MembershipDomainEvent(UUID eventId, String type, Instant occurredAt, Map<String, Object> data) {
    public MembershipDomainEvent {
        data = Map.copyOf(data);
    }
}
