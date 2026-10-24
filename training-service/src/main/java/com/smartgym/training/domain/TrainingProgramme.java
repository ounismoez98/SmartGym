package com.smartgym.training.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "training_programmes")
public class TrainingProgramme {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "coach_id")
    private UUID coachId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 24)
    private ExperienceLevel experienceLevel;

    @Column(name = "duration_weeks", nullable = false)
    private int durationWeeks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProgrammeStatus status = ProgrammeStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProgrammeOrigin origin = ProgrammeOrigin.MANUAL;

    @Column(nullable = false)
    private int revision = 1;

    @Column(name = "approved_revision")
    private Integer approvedRevision;

    @Column(name = "source_ai_job_id")
    private UUID sourceAiJobId;

    @Column(name = "supersedes_programme_id")
    private UUID supersedesProgrammeId;

    @Column(name = "created_by_subject", nullable = false, length = 64)
    private String createdBySubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "programme", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgrammeSession> sessions = new ArrayList<>();

    @OneToMany(mappedBy = "programme", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CoachReview> reviews = new ArrayList<>();

    protected TrainingProgramme() {}

    public TrainingProgramme(
            UUID memberId,
            UUID coachId,
            String title,
            String goal,
            ExperienceLevel experienceLevel,
            int durationWeeks,
            String createdBySubject) {
        this.memberId = memberId;
        this.coachId = coachId;
        this.title = title;
        this.goal = goal;
        this.experienceLevel = experienceLevel;
        this.durationWeeks = durationWeeks;
        this.createdBySubject = createdBySubject;
        this.status = ProgrammeStatus.DRAFT;
        this.origin = ProgrammeOrigin.MANUAL;
        this.revision = 1;
    }

    public TrainingProgramme(
            UUID memberId,
            UUID coachId,
            String title,
            String goal,
            ExperienceLevel experienceLevel,
            int durationWeeks,
            String createdBySubject,
            int revision,
            UUID supersedesProgrammeId) {
        this(memberId, coachId, title, goal, experienceLevel, durationWeeks, createdBySubject);
        this.revision = revision;
        this.supersedesProgrammeId = supersedesProgrammeId;
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

    public void updateDetails(String title, String goal, ExperienceLevel level, int durationWeeks) {
        this.title = title;
        this.goal = goal;
        this.experienceLevel = level;
        this.durationWeeks = durationWeeks;
        this.revision++;
        this.approvedRevision = null;
        this.status = ProgrammeStatus.DRAFT;
    }

    public void replaceSessions(List<ProgrammeSession> replacements) {
        clearSessions();
        addSessions(replacements);
    }

    public void clearSessions() {
        sessions.clear();
    }

    public void addSessions(List<ProgrammeSession> replacements) {
        replacements.forEach(session -> {
            session.attachTo(this);
            sessions.add(session);
        });
    }

    public void submitForReview() {
        status = ProgrammeStatus.PENDING_REVIEW;
    }

    public void recordReview(CoachReview review) {
        review.attachTo(this);
        reviews.add(review);
        if (review.getDecision() == ReviewDecision.APPROVED) {
            status = ProgrammeStatus.APPROVED;
            approvedRevision = revision;
        } else {
            status = ProgrammeStatus.CHANGES_REQUESTED;
            approvedRevision = null;
        }
    }

    public void publish() {
        status = ProgrammeStatus.PUBLISHED;
    }

    public void archive() {
        status = ProgrammeStatus.ARCHIVED;
    }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public UUID getCoachId() { return coachId; }
    public String getTitle() { return title; }
    public String getGoal() { return goal; }
    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public int getDurationWeeks() { return durationWeeks; }
    public ProgrammeStatus getStatus() { return status; }
    public ProgrammeOrigin getOrigin() { return origin; }
    public int getRevision() { return revision; }
    public Integer getApprovedRevision() { return approvedRevision; }
    public String getCreatedBySubject() { return createdBySubject; }
    public UUID getSupersedesProgrammeId() { return supersedesProgrammeId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<ProgrammeSession> getSessions() { return sessions; }
    public List<CoachReview> getReviews() { return reviews; }
}
