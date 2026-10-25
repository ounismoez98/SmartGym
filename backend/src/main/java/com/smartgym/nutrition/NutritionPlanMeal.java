package com.smartgym.nutrition;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nutrition_plan_meals")
public class NutritionPlanMeal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nutrition_plan_day_id", nullable = false)
    private NutritionPlanDay day;

    @Column(name = "meal_position", nullable = false)
    private int position;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 40)
    private MealPlan.MealType mealType;

    @Column(name = "serving_size_grams", nullable = false)
    private int servingSizeGrams;

    @Column(name = "preparation_instructions", nullable = false, length = 2000)
    private String preparationInstructions;

    @Column(nullable = false)
    private double calories;

    @Column(name = "protein_grams", nullable = false)
    private double proteinGrams;

    @Column(name = "carbohydrate_grams", nullable = false)
    private double carbohydrateGrams;

    @Column(name = "fat_grams", nullable = false)
    private double fatGrams;

    @Column(name = "fiber_grams", nullable = false)
    private double fiberGrams;

    @Column(name = "sodium_mg", nullable = false)
    private double sodiumMg;

    @Column(name = "nutrition_source", nullable = false, length = 32)
    private String nutritionSource;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nutrition_plan_meal_items",
            joinColumns = @JoinColumn(name = "nutrition_plan_meal_id"))
    @OrderColumn(name = "item_position")
    private List<NutritionPlanMealItem> foods = new ArrayList<>();

    protected NutritionPlanMeal() {
    }

    public NutritionPlanMeal(String name, MealPlan.MealType mealType, int servingSizeGrams,
                             String preparationInstructions, NutritionService.NutritionEstimate nutrition,
                             String nutritionSource, List<NutritionPlanMealItem> foods) {
        this.name = name;
        this.mealType = mealType;
        this.servingSizeGrams = servingSizeGrams;
        this.preparationInstructions = preparationInstructions;
        this.calories = nutrition.calories();
        this.proteinGrams = nutrition.proteinGrams();
        this.carbohydrateGrams = nutrition.carbohydrateGrams();
        this.fatGrams = nutrition.fatGrams();
        this.fiberGrams = nutrition.fiberGrams();
        this.sodiumMg = nutrition.sodiumMg();
        this.nutritionSource = nutritionSource;
        this.foods = new ArrayList<>(foods);
    }

    void attachTo(NutritionPlanDay day) {
        this.day = day;
    }

    void setPosition(int position) {
        this.position = position;
    }

    public Long getId() { return id; }
    public NutritionPlanDay getDay() { return day; }
    public int getPosition() { return position; }
    public String getName() { return name; }
    public MealPlan.MealType getMealType() { return mealType; }
    public int getServingSizeGrams() { return servingSizeGrams; }
    public String getPreparationInstructions() { return preparationInstructions; }
    public double getCalories() { return calories; }
    public double getProteinGrams() { return proteinGrams; }
    public double getCarbohydrateGrams() { return carbohydrateGrams; }
    public double getFatGrams() { return fatGrams; }
    public double getFiberGrams() { return fiberGrams; }
    public double getSodiumMg() { return sodiumMg; }
    public String getNutritionSource() { return nutritionSource; }
    public List<NutritionPlanMealItem> getFoods() { return foods; }

    @Embeddable
    public static class NutritionPlanMealItem {
        @Column(name = "food_id")
        private Long foodId;

        @Column(name = "food_name", nullable = false, length = 150)
        private String foodName;

        @Column(nullable = false)
        private double servings;

        protected NutritionPlanMealItem() {
        }

        public NutritionPlanMealItem(Long foodId, String foodName, double servings) {
            this.foodId = foodId;
            this.foodName = foodName;
            this.servings = servings;
        }

        public Long getFoodId() { return foodId; }
        public String getFoodName() { return foodName; }
        public double getServings() { return servings; }
    }
}
