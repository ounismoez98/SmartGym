package com.smartgym.nutrition;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "nutrition_profiles")
public class NutritionProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String subject;

    @Column(nullable = false, length = 120)
    private String profileName;

    @Column(nullable = false, length = 50)
    private String goal;

    @Column(nullable = false, length = 80)
    private String dietaryPreference;

    @Column(nullable = false, length = 80)
    private String activityLevel;

    @Column
    private Integer age;

    @Column
    private Double heightCm;

    @Column
    private Double weightKg;

    @Column
    private Integer targetCalories;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CalorieTargetMode calorieTargetMode = CalorieTargetMode.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private EnergyEquationSex energyEquationSex;

    @Column(name = "target_weight_kg")
    private Double targetWeightKg;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "budget_preference", length = 40)
    private String budgetPreference;

    @Column(name = "max_preparation_minutes")
    private Integer maxPreparationMinutes;

    @Column(name = "cooking_preference", length = 500)
    private String cookingPreference;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nutrition_profile_allergies",
            joinColumns = @JoinColumn(name = "nutrition_profile_id"))
    @Column(name = "allergy", nullable = false, length = 100)
    private Set<String> allergies = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nutrition_profile_intolerances",
            joinColumns = @JoinColumn(name = "nutrition_profile_id"))
    @Column(name = "intolerance", nullable = false, length = 100)
    private Set<String> intolerances = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nutrition_profile_disliked_foods",
            joinColumns = @JoinColumn(name = "nutrition_profile_id"))
    @Column(name = "food", nullable = false, length = 100)
    private Set<String> dislikedFoods = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nutrition_profile_preferred_ingredients",
            joinColumns = @JoinColumn(name = "nutrition_profile_id"))
    @Column(name = "ingredient", nullable = false, length = 100)
    private Set<String> preferredIngredients = new LinkedHashSet<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected NutritionProfile() {
    }

    public NutritionProfile(String subject, String profileName, String goal,
                           String dietaryPreference, String activityLevel,
                           Integer age, Double heightCm, Double weightKg,
                           Integer targetCalories) {
        this.subject = subject;
        this.profileName = profileName;
        this.goal = goal;
        this.dietaryPreference = dietaryPreference;
        this.activityLevel = activityLevel;
        this.age = age;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.targetCalories = targetCalories;
    }

    public NutritionProfile(String subject, String profileName, String goal,
                            String dietaryPreference, String activityLevel,
                            Integer age, Double heightCm, Double weightKg,
                            Integer targetCalories, CalorieTargetMode calorieTargetMode,
                            EnergyEquationSex energyEquationSex) {
        this(subject, profileName, goal, dietaryPreference, activityLevel,
                age, heightCm, weightKg, targetCalories);
        this.calorieTargetMode = calorieTargetMode;
        this.energyEquationSex = energyEquationSex;
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getProfileName() { return profileName; }
    public String getGoal() { return goal; }
    public String getDietaryPreference() { return dietaryPreference; }
    public String getActivityLevel() { return activityLevel; }
    public Integer getAge() { return age; }
    public Double getHeightCm() { return heightCm; }
    public Double getWeightKg() { return weightKg; }
    public Integer getTargetCalories() { return targetCalories; }
    public CalorieTargetMode getCalorieTargetMode() { return calorieTargetMode; }
    public EnergyEquationSex getEnergyEquationSex() { return energyEquationSex; }
    public Double getTargetWeightKg() { return targetWeightKg; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getBudgetPreference() { return budgetPreference; }
    public Integer getMaxPreparationMinutes() { return maxPreparationMinutes; }
    public String getCookingPreference() { return cookingPreference; }
    public Set<String> getAllergies() { return Set.copyOf(allergies); }
    public Set<String> getIntolerances() { return Set.copyOf(intolerances); }
    public Set<String> getDislikedFoods() { return Set.copyOf(dislikedFoods); }
    public Set<String> getPreferredIngredients() { return Set.copyOf(preferredIngredients); }
    public Instant getCreatedAt() { return createdAt; }

    public void update(String profileName, String goal, String dietaryPreference,
                       String activityLevel, Integer age, Double heightCm,
                       Double weightKg, Integer targetCalories) {
        this.profileName = profileName;
        this.goal = goal;
        this.dietaryPreference = dietaryPreference;
        this.activityLevel = activityLevel;
        this.age = age;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.targetCalories = targetCalories;
    }

    public void update(String profileName, String goal, String dietaryPreference,
                       String activityLevel, Integer age, Double heightCm,
                       Double weightKg, Integer targetCalories,
                       CalorieTargetMode calorieTargetMode, EnergyEquationSex energyEquationSex) {
        update(profileName, goal, dietaryPreference, activityLevel, age, heightCm, weightKg,
                targetCalories, calorieTargetMode, energyEquationSex, null, null, null, null,
                null, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public void update(String profileName, String goal, String dietaryPreference,
                       String activityLevel, Integer age, Double heightCm,
                       Double weightKg, Integer targetCalories,
                       CalorieTargetMode calorieTargetMode, EnergyEquationSex energyEquationSex,
                       Double targetWeightKg, LocalDate targetDate, String budgetPreference,
                       Integer maxPreparationMinutes, String cookingPreference,
                       Set<String> allergies, Set<String> intolerances,
                       Set<String> dislikedFoods, Set<String> preferredIngredients) {
        update(profileName, goal, dietaryPreference, activityLevel,
                age, heightCm, weightKg, targetCalories);
        this.calorieTargetMode = calorieTargetMode;
        this.energyEquationSex = energyEquationSex;
        this.targetWeightKg = targetWeightKg;
        this.targetDate = targetDate;
        this.budgetPreference = budgetPreference;
        this.maxPreparationMinutes = maxPreparationMinutes;
        this.cookingPreference = cookingPreference;
        this.allergies = new LinkedHashSet<>(allergies);
        this.intolerances = new LinkedHashSet<>(intolerances);
        this.dislikedFoods = new LinkedHashSet<>(dislikedFoods);
        this.preferredIngredients = new LinkedHashSet<>(preferredIngredients);
    }

    public enum CalorieTargetMode {
        AUTO,
        MANUAL
    }

    public enum EnergyEquationSex {
        FEMALE,
        MALE
    }
}
