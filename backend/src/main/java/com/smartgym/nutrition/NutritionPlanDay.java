package com.smartgym.nutrition;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nutrition_plan_days")
public class NutritionPlanDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nutrition_plan_id", nullable = false)
    private NutritionPlan plan;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NutritionPlanMeal> meals = new ArrayList<>();

    protected NutritionPlanDay() {
    }

    public NutritionPlanDay(LocalDate planDate) {
        this.planDate = planDate;
    }

    void attachTo(NutritionPlan plan) {
        this.plan = plan;
    }

    public void addMeal(NutritionPlanMeal meal) {
        meal.attachTo(this);
        meal.setPosition(meals.size());
        meals.add(meal);
    }

    public void replaceMeals(List<NutritionPlanMeal> replacements) {
        meals.clear();
        replacements.forEach(this::addMeal);
    }

    public void replaceMeal(Long mealId, NutritionPlanMeal replacement) {
        int index = -1;
        for (int i = 0; i < meals.size(); i++) {
            if (meals.get(i).getId().equals(mealId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Meal does not belong to this plan day.");
        }
        replacement.attachTo(this);
        replacement.setPosition(index);
        meals.set(index, replacement);
    }

    public Long getId() { return id; }
    public NutritionPlan getPlan() { return plan; }
    public LocalDate getPlanDate() { return planDate; }
    public List<NutritionPlanMeal> getMeals() { return meals; }
}
