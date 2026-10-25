package com.smartgym.membership.feedback.dto;

import com.smartgym.membership.feedback.FeedbackCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackSubmitRequest(
        @Min(value = 1, message = "Rating must be at least 1 star")
        @Max(value = 5, message = "Rating cannot exceed 5 stars")
        int rating,

        @NotNull(message = "Category is required")
        FeedbackCategory category,

        @NotBlank(message = "Comment cannot be blank")
        @Size(max = 4000, message = "Comment cannot exceed 4000 characters")
        String comment
) {}
