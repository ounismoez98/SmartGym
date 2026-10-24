package com.smartgym.membership.api;

import java.time.Instant;
import java.util.UUID;

public record CoachAssignmentResponse(UUID id, UUID coachId, UUID memberId, Instant createdAt, Instant endedAt) {}
