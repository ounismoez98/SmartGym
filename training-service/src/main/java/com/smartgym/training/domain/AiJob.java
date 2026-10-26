package com.smartgym.training.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "ai_jobs")
public class AiJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "created_by_subject", nullable = false, length = 64)
    private String createdBySubject;

    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "candidate_exercises_json", nullable = false, columnDefinition = "TEXT")
    private String candidateExercisesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiJobStatus status = AiJobStatus.QUEUED;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AiJob() {}

    public AiJob(
            UUID memberId,
            UUID coachId,
            String createdBySubject,
            String requestJson,
            String candidateExercisesJson) {
        this.memberId = memberId;
        this.coachId = coachId;
        this.createdBySubject = createdBySubject;
        this.requestJson = requestJson;
        this.candidateExercisesJson = candidateExercisesJson;
        this.status = AiJobStatus.QUEUED;
    }

    @PrePersist
    void beforeInsert() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void complete(UUID programmeId) {
        this.programmeId = programmeId;
        this.failureReason = null;
        this.status = AiJobStatus.COMPLETED;
    }

    public void fail(String reason) {
        this.failureReason = reason;
        this.status = AiJobStatus.FAILED;
    }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public UUID getCoachId() { return coachId; }
    public String getCreatedBySubject() { return createdBySubject; }
    public String getRequestJson() { return requestJson; }
    public String getCandidateExercisesJson() { return candidateExercisesJson; }
    public AiJobStatus getStatus() { return status; }
    public UUID getProgrammeId() { return programmeId; }
    public String getFailureReason() { return failureReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
