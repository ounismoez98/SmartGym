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
@Table(name = "exercises")
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1200)
    private String description;

    @Column(nullable = false, length = 3000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_muscle_group", nullable = false, length = 32)
    private MuscleGroup primaryMuscleGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExerciseDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_category", nullable = false, length = 32)
    private EquipmentCategory equipmentCategory;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Exercise() {}

    public Exercise(
            String name,
            String description,
            String instructions,
            MuscleGroup primaryMuscleGroup,
            ExerciseDifficulty difficulty,
            EquipmentCategory equipmentCategory) {
        this.name = name;
        this.description = description;
        this.instructions = instructions;
        this.primaryMuscleGroup = primaryMuscleGroup;
        this.difficulty = difficulty;
        this.equipmentCategory = equipmentCategory;
        this.active = true;
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

    public void update(
            String name,
            String description,
            String instructions,
            MuscleGroup primaryMuscleGroup,
            ExerciseDifficulty difficulty,
            EquipmentCategory equipmentCategory) {
        this.name = name;
        this.description = description;
        this.instructions = instructions;
        this.primaryMuscleGroup = primaryMuscleGroup;
        this.difficulty = difficulty;
        this.equipmentCategory = equipmentCategory;
    }

    public void archive() {
        active = false;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getInstructions() { return instructions; }
    public MuscleGroup getPrimaryMuscleGroup() { return primaryMuscleGroup; }
    public ExerciseDifficulty getDifficulty() { return difficulty; }
    public EquipmentCategory getEquipmentCategory() { return equipmentCategory; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
