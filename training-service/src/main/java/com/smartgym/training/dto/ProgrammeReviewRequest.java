package com.smartgym.training.dto;

import com.smartgym.training.domain.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProgrammeReviewRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 2000) String comment) {}
