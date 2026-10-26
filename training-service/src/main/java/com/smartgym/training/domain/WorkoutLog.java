package com.smartgym.training.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "workout_logs")
public class WorkoutLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "programme_session_id")
    private UUID programmeSessionId;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "workoutLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkoutSet> sets = new ArrayList<>();

    protected WorkoutLog() {}

    public WorkoutLog(
            UUID memberId,
            UUID programmeId,
            UUID programmeSessionId,
            OffsetDateTime performedAt,
            String notes,
            List<WorkoutSet> sets) {
        this.memberId = memberId;
        this.programmeId = programmeId;
        this.programmeSessionId = programmeSessionId;
        this.performedAt = performedAt.withOffsetSameInstant(ZoneOffset.UTC);
        this.notes = notes;
        replaceSets(sets);
    }

    public void update(OffsetDateTime performedAt, String notes, List<WorkoutSet> sets) {
        if (performedAt != null) this.performedAt = performedAt.withOffsetSameInstant(ZoneOffset.UTC);
        this.notes = notes;
        replaceSets(sets);
    }

    public void clearSets() {
        sets.clear();
    }

    public void addSets(List<WorkoutSet> replacements) {
        replacements.forEach(set -> {
            set.attachTo(this);
            sets.add(set);
        });
    }

    private void replaceSets(List<WorkoutSet> replacements) {
        this.sets.clear();
        addSets(replacements);
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

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeSessionId() { return programmeSessionId; }
    public OffsetDateTime getPerformedAt() { return performedAt; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<WorkoutSet> getSets() { return sets; }
}
