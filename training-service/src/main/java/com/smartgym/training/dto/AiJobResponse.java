package com.smartgym.training.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.smartgym.training.domain.AiJob;
import com.smartgym.training.domain.AiJobStatus;

public record AiJobResponse(
        UUID id,
        AiJobStatus status,
        UUID programmeId,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public static AiJobResponse from(AiJob job) {
        return new AiJobResponse(
                job.getId(),
                job.getStatus(),
                job.getProgrammeId(),
                job.getFailureReason(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
