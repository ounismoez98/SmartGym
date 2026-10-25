package com.smartgym.membership.profile;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription")
public class MemberSubscription {
    public enum Status {
        ACTIVE,
        EXPIRED,
        CANCELLED
    }

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemberSubscription() {}

    public MemberSubscription(UUID id, UUID memberId, UUID planId, Instant startsAt, Instant endsAt) {
        this.id = id;
        this.memberId = memberId;
        this.planId = planId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void changePlan(UUID planId, Instant endsAt) {
        this.planId = planId;
        this.endsAt = endsAt;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason, Instant cancelledAt) {
        this.status = Status.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = cancelledAt;
        this.updatedAt = cancelledAt;
    }

    public void expire(Instant updatedAt) {
        this.status = Status.EXPIRED;
        this.updatedAt = updatedAt;
    }
}
