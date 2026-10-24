package com.smartgym.nutrition;

import jakarta.persistence.*;

import java.time.Instant;

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
}
