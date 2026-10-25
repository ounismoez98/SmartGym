package com.smartgym.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "nutrition_hydration_logs")
public class HydrationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String subject;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    @Column(name = "water_ml", nullable = false)
    private int waterMl;

    protected HydrationLog() {
    }

    public HydrationLog(String subject, Instant consumedAt, int waterMl) {
        this.subject = subject;
        this.consumedAt = consumedAt;
        this.waterMl = waterMl;
    }

    public void correct(Instant consumedAt, int waterMl) {
        this.consumedAt = consumedAt;
        this.waterMl = waterMl;
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public Instant getConsumedAt() { return consumedAt; }
    public int getWaterMl() { return waterMl; }
}
