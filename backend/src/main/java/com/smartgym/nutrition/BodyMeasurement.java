package com.smartgym.nutrition;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "nutrition_body_measurements")
public class BodyMeasurement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String subject;

    @Column(nullable = false)
    private LocalDate measurementDate;

    @Column(nullable = false)
    private double weightKg;

    @Column(nullable = false)
    private double waistCm;

    @Column(nullable = false)
    private double hipCm;

    @Column(nullable = false)
    private double chestCm;

    @Column(nullable = false)
    private double armCm;

    @Column(nullable = false)
    private double thighCm;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected BodyMeasurement() {
    }

    public BodyMeasurement(String subject, LocalDate measurementDate, double weightKg,
                          double waistCm, double hipCm, double chestCm, double armCm,
                          double thighCm, String notes) {
        this.subject = subject;
        this.measurementDate = measurementDate;
        this.weightKg = weightKg;
        this.waistCm = waistCm;
        this.hipCm = hipCm;
        this.chestCm = chestCm;
        this.armCm = armCm;
        this.thighCm = thighCm;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public LocalDate getMeasurementDate() { return measurementDate; }
    public double getWeightKg() { return weightKg; }
    public double getWaistCm() { return waistCm; }
    public double getHipCm() { return hipCm; }
    public double getChestCm() { return chestCm; }
    public double getArmCm() { return armCm; }
    public double getThighCm() { return thighCm; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }

    public void update(LocalDate measurementDate, double weightKg, double waistCm, double hipCm,
                      double chestCm, double armCm, double thighCm, String notes) {
        this.measurementDate = measurementDate;
        this.weightKg = weightKg;
        this.waistCm = waistCm;
        this.hipCm = hipCm;
        this.chestCm = chestCm;
        this.armCm = armCm;
        this.thighCm = thighCm;
        this.notes = notes;
    }
}
