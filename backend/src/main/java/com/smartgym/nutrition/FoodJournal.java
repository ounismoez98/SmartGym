package com.smartgym.nutrition;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "nutrition_food_journal")
public class FoodJournal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String subject;

    @Column(nullable = false)
    private Long foodId;

    @Column(nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private MealType mealType;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false)
    private double servings;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected FoodJournal() {
    }

    public FoodJournal(String subject, Long foodId, MealType mealType, LocalDate entryDate,
                      double servings, String notes) {
        this.subject = subject;
        this.foodId = foodId;
        this.mealType = mealType;
        this.entryDate = entryDate;
        this.servings = servings;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public Long getFoodId() { return foodId; }
    public MealType getMealType() { return mealType; }
    public LocalDate getEntryDate() { return entryDate; }
    public double getServings() { return servings; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }

    public void update(Long foodId, MealType mealType, LocalDate entryDate, double servings, String notes) {
        this.foodId = foodId;
        this.mealType = mealType;
        this.entryDate = entryDate;
        this.servings = servings;
        this.notes = notes;
    }

    public enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER,
        SNACK,
        PRE_WORKOUT,
        POST_WORKOUT
    }
}
