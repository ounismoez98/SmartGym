package com.smartgym.membership.profile;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "subscription_plan")
public class SubscriptionPlan {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriptionPlan() {}

    public SubscriptionPlan(
            UUID id, String code, String name, String description,
            int durationDays, long priceCents, String currency) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.durationDays = durationDays;
        this.priceCents = priceCents;
        this.currency = currency;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String name, String description, int durationDays, long priceCents, String currency, boolean active) {
        this.name = name;
        this.description = description;
        this.durationDays = durationDays;
        this.priceCents = priceCents;
        this.currency = currency;
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
