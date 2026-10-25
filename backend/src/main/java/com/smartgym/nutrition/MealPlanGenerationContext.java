package com.smartgym.nutrition;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "nutrition_meal_plan_generation_contexts")
public class MealPlanGenerationContext {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String subject;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "nutrition_meal_plan_generation_restrictions",
            joinColumns = @JoinColumn(name = "generation_context_id")
    )
    @Column(name = "restriction", nullable = false, length = 100)
    private Set<String> restrictions = new LinkedHashSet<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private boolean consumed;

    protected MealPlanGenerationContext() {
    }

    public MealPlanGenerationContext(String subject, Collection<String> restrictions) {
        this.id = UUID.randomUUID();
        this.subject = subject;
        this.restrictions = restrictions == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(restrictions);
    }

    public UUID getId() { return id; }
    public String getSubject() { return subject; }
    public Set<String> getRestrictions() { return Collections.unmodifiableSet(restrictions); }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isConsumed() { return consumed; }

    public void consume() {
        this.consumed = true;
    }
}
