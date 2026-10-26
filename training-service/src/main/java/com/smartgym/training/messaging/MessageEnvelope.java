package com.smartgym.training.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageEnvelope<T>(
        UUID messageId,
        UUID correlationId,
        UUID jobId,
        String messageType,
        int version,
        OffsetDateTime occurredAt,
        T payload) {}
