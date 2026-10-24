package com.smartgym.training.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.smartgym.training.domain.CoachReview;
import com.smartgym.training.domain.ExperienceLevel;
import com.smartgym.training.domain.ProgrammeOrigin;
import com.smartgym.training.domain.ProgrammeStatus;
import com.smartgym.training.domain.TrainingProgramme;

public record ProgrammeResponse(
        UUID id,
        UUID memberId,
        UUID coachId,
        String title,
        String goal,
        ExperienceLevel experienceLevel,
        int durationWeeks,
        ProgrammeStatus status,
        ProgrammeOrigin origin,
        int revision,
        Integer approvedRevision,
        UUID supersedesProgrammeId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version,
        List<Session> sessions,
        List<Review> reviewHistory) {
    public static ProgrammeResponse from(TrainingProgramme programme, boolean includeReviewHistory) {
        List<Session> sessions = programme.getSessions().stream()
                .sorted(java.util.Comparator.comparingInt(
                        com.smartgym.training.domain.ProgrammeSession::getDayOrder))
                .map(session -> new Session(
                        session.getId(),
                        session.getTitle(),
                        session.getDayOrder(),
                        session.getNotes(),
                        session.getItems().stream()
                                .sorted(java.util.Comparator.comparingInt(
                                        com.smartgym.training.domain.ProgrammeItem::getOrderIndex))
                                .map(item -> new Item(
                                        item.getId(),
                                        item.getExerciseId(),
                                        item.getOrderIndex(),
                                        item.getSets(),
                                        item.getRepetitions(),
                                        item.getDurationSeconds(),
                                        item.getRestSeconds(),
                                        item.getSuggestedLoadKg(),
                                        item.getNotes()))
                                .toList()))
                .toList();
        List<Review> reviews = includeReviewHistory
                ? programme.getReviews().stream()
                        .sorted(java.util.Comparator.comparing(CoachReview::getReviewedAt))
                        .map(review -> new Review(
                                review.getReviewedRevision(),
                                review.getCoachId(),
                                review.getDecision(),
                                review.getComment(),
                                review.getReviewedAt()))
                        .toList()
                : List.of();
        return new ProgrammeResponse(
                programme.getId(),
                programme.getMemberId(),
                programme.getCoachId(),
                programme.getTitle(),
                programme.getGoal(),
                programme.getExperienceLevel(),
                programme.getDurationWeeks(),
                programme.getStatus(),
                programme.getOrigin(),
                programme.getRevision(),
                programme.getApprovedRevision(),
                programme.getSupersedesProgrammeId(),
                programme.getCreatedAt(),
                programme.getUpdatedAt(),
                programme.getVersion(),
                sessions,
                reviews);
    }

    public record Session(UUID id, String title, int dayOrder, String notes, List<Item> items) {}

    public record Item(
            UUID id,
            UUID exerciseId,
            int orderIndex,
            int sets,
            Integer repetitions,
            Integer durationSeconds,
            int restSeconds,
            BigDecimal suggestedLoadKg,
            String notes) {}

    public record Review(
            int reviewedRevision,
            UUID coachId,
            com.smartgym.training.domain.ReviewDecision decision,
            String comment,
            OffsetDateTime reviewedAt) {}
}
