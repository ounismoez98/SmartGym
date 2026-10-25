package com.smartgym.membership.feedback.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.smartgym.membership.feedback.Feedback;
import com.smartgym.membership.feedback.FeedbackCategory;
import com.smartgym.membership.feedback.FeedbackSentiment;

public record FeedbackResponse(
        UUID id,
        String subject,
        String memberName,
        int rating,
        FeedbackCategory category,
        String comment,
        FeedbackSentiment sentiment,
        String summary,
        List<String> themes,
        Instant createdAt
) {
    public static FeedbackResponse fromEntity(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getSubject(),
                feedback.getMemberName(),
                feedback.getRating(),
                feedback.getCategory(),
                feedback.getComment(),
                feedback.getSentiment(),
                feedback.getSummary(),
                feedback.getThemes(),
                feedback.getCreatedAt()
        );
    }
}
