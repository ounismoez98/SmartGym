package com.smartgym.training.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "workout_sets")
public class WorkoutSet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_log_id", nullable = false)
    private WorkoutLog workoutLog;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    private Integer repetitions;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "load_kg", precision = 7, scale = 2)
    private BigDecimal loadKg;

    @Column(length = 500)
    private String notes;

    protected WorkoutSet() {}

    public WorkoutSet(
            UUID exerciseId,
            int setNumber,
            Integer repetitions,
            Integer durationSeconds,
            BigDecimal loadKg,
            String notes) {
        this.exerciseId = exerciseId;
        this.setNumber = setNumber;
        this.repetitions = repetitions;
        this.durationSeconds = durationSeconds;
        this.loadKg = loadKg;
        this.notes = notes;
    }

    void attachTo(WorkoutLog workoutLog) {
        this.workoutLog = workoutLog;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public int getSetNumber() { return setNumber; }
    public Integer getRepetitions() { return repetitions; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public BigDecimal getLoadKg() { return loadKg; }
    public String getNotes() { return notes; }
}
