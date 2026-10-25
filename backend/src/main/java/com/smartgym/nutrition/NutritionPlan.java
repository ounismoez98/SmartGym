package com.smartgym.nutrition;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nutrition_plans")
public class NutritionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String subject;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 100)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 16)
    private PlanType planType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_context_id", nullable = false, updatable = false)
    private MealPlanGenerationContext generationContext;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NutritionPlanDay> days = new ArrayList<>();

    protected NutritionPlan() {
    }

    public NutritionPlan(String subject, String name, String goal, PlanType planType,
                         LocalDate startDate, MealPlanGenerationContext generationContext) {
        this.subject = subject;
        this.name = name;
        this.goal = goal;
        this.planType = planType;
        this.startDate = startDate;
        this.endDate = startDate.plusDays(planType == PlanType.WEEKLY ? 6 : 0);
        this.generationContext = generationContext;
    }

    public void addDay(NutritionPlanDay day) {
        day.attachTo(this);
        days.add(day);
    }

    public void markChanged() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getName() { return name; }
    public String getGoal() { return goal; }
    public PlanType getPlanType() { return planType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public MealPlanGenerationContext getGenerationContext() { return generationContext; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public List<NutritionPlanDay> getDays() { return days; }

    public enum PlanType {
        DAILY,
        WEEKLY
    }
}
