package com.smartgym.training.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.smartgym.training.domain.AiJobStatus;

public record ProgrammeGenerationAccepted(UUID jobId, AiJobStatus status, OffsetDateTime createdAt) {}
