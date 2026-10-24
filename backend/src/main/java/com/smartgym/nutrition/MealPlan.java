package com.smartgym.nutrition;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nutrition_meal_plans")
public class MealPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String subject;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private MealType mealType;

    @Column(nullable = false)
    private double targetCalories;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "nutrition_meal_plan_items",
            joinColumns = @JoinColumn(name = "meal_plan_id")
    )
    private List<MealPlanItem> items = new ArrayList<>();

    protected MealPlan() {
    }

    public MealPlan(String subject, String name, String description, MealType mealType,
                    double targetCalories, List<MealPlanItem> items) {
        this.subject = subject;
        this.name = name;
        this.description = description;
        this.mealType = mealType;
        this.targetCalories = targetCalories;
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public MealType getMealType() { return mealType; }
    public double getTargetCalories() { return targetCalories; }
    public Instant getCreatedAt() { return createdAt; }
    public List<MealPlanItem> getItems() { return items; }

    public void update(String name, String description, MealType mealType, double targetCalories,
                       List<MealPlanItem> items) {
        this.name = name;
        this.description = description;
        this.mealType = mealType;
        this.targetCalories = targetCalories;
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
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

@Embeddable
class MealPlanItem {
    @Column(nullable = false)
    private Long foodId;

    @Column(nullable = false)
    private double servings;

    @Column(length = 150)
    private String foodName;

    protected MealPlanItem() {
    }

    public MealPlanItem(Long foodId, double servings, String foodName) {
        this.foodId = foodId;
        this.servings = servings;
        this.foodName = foodName;
    }

    public Long getFoodId() { return foodId; }
    public double getServings() { return servings; }
    public String getFoodName() { return foodName; }
}
