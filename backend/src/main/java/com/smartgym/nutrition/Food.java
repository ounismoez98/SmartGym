package com.smartgym.nutrition;

import jakarta.persistence.*;

@Entity
@Table(name = "nutrition_foods")
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String brand;

    @Column(nullable = false)
    private int servingSizeGrams;

    @Column(nullable = false)
    private double calories;

    @Column(nullable = false)
    private double proteinGrams;

    @Column(nullable = false)
    private double carbohydrateGrams;

    @Column(nullable = false)
    private double fatGrams;

    @Column(nullable = false)
    private double fiberGrams;

    @Column(nullable = false)
    private double sodiumMg;

    protected Food() {
    }

    public Food(String subject, String name, String brand, int servingSizeGrams,
                double calories, double proteinGrams, double carbohydrateGrams,
                double fatGrams, double fiberGrams, double sodiumMg) {
        this.subject = subject;
        this.name = name;
        this.brand = brand;
        this.servingSizeGrams = servingSizeGrams;
        this.calories = calories;
        this.proteinGrams = proteinGrams;
        this.carbohydrateGrams = carbohydrateGrams;
        this.fatGrams = fatGrams;
        this.fiberGrams = fiberGrams;
        this.sodiumMg = sodiumMg;
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public int getServingSizeGrams() { return servingSizeGrams; }
    public double getCalories() { return calories; }
    public double getProteinGrams() { return proteinGrams; }
    public double getCarbohydrateGrams() { return carbohydrateGrams; }
    public double getFatGrams() { return fatGrams; }
    public double getFiberGrams() { return fiberGrams; }
    public double getSodiumMg() { return sodiumMg; }

    public void update(String name, String brand, int servingSizeGrams, double calories,
                       double proteinGrams, double carbohydrateGrams, double fatGrams,
                       double fiberGrams, double sodiumMg) {
        this.name = name;
        this.brand = brand;
        this.servingSizeGrams = servingSizeGrams;
        this.calories = calories;
        this.proteinGrams = proteinGrams;
        this.carbohydrateGrams = carbohydrateGrams;
        this.fatGrams = fatGrams;
        this.fiberGrams = fiberGrams;
        this.sodiumMg = sodiumMg;
    }
}
