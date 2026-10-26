package com.smartgym.training.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "coach_reviews")
public class CoachReview {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id", nullable = false)
    private TrainingProgramme programme;

    @Column(name = "reviewed_revision", nullable = false)
    private int reviewedRevision;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReviewDecision decision;

    @Column(length = 2000)
    private String comment;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private OffsetDateTime reviewedAt;

    protected CoachReview() {}

    public CoachReview(int reviewedRevision, UUID coachId, ReviewDecision decision, String comment) {
        this.reviewedRevision = reviewedRevision;
        this.coachId = coachId;
        this.decision = decision;
        this.comment = comment;
    }

    void attachTo(TrainingProgramme programme) {
        this.programme = programme;
    }

    @PrePersist
    void beforeInsert() {
        reviewedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public int getReviewedRevision() { return reviewedRevision; }
    public UUID getCoachId() { return coachId; }
    public ReviewDecision getDecision() { return decision; }
    public String getComment() { return comment; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
}
