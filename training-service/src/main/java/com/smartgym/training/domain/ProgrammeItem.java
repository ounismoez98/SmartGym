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
@Table(name = "programme_items")
public class ProgrammeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ProgrammeSession session;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private int sets;

    private Integer repetitions;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "rest_seconds", nullable = false)
    private int restSeconds;

    @Column(name = "suggested_load_kg", precision = 7, scale = 2)
    private BigDecimal suggestedLoadKg;

    @Column(length = 500)
    private String notes;

    protected ProgrammeItem() {}

    public ProgrammeItem(
            UUID exerciseId,
            int orderIndex,
            int sets,
            Integer repetitions,
            Integer durationSeconds,
            int restSeconds,
            BigDecimal suggestedLoadKg,
            String notes) {
        this.exerciseId = exerciseId;
        this.orderIndex = orderIndex;
        this.sets = sets;
        this.repetitions = repetitions;
        this.durationSeconds = durationSeconds;
        this.restSeconds = restSeconds;
        this.suggestedLoadKg = suggestedLoadKg;
        this.notes = notes;
    }

    void attachTo(ProgrammeSession session) {
        this.session = session;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public int getOrderIndex() { return orderIndex; }
    public int getSets() { return sets; }
    public Integer getRepetitions() { return repetitions; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public int getRestSeconds() { return restSeconds; }
    public BigDecimal getSuggestedLoadKg() { return suggestedLoadKg; }
    public String getNotes() { return notes; }
}
