package com.smartgym.membership.payment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment")
public class Payment {
    public enum Method {
        CASH,
        CARD,
        TRANSFER
    }

    public enum Status {
        RECORDED,
        CORRECTED
    }

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "corrects_payment_id")
    private UUID correctsPaymentId;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    protected Payment() {}

    public Payment(
            UUID id,
            UUID memberId,
            UUID subscriptionId,
            long amountCents,
            String currency,
            Method method,
            UUID correctsPaymentId,
            String reason,
            String createdBy,
            Instant paidAt) {
        this.id = id;
        this.memberId = memberId;
        this.subscriptionId = subscriptionId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.method = method;
        this.status = Status.RECORDED;
        this.correctsPaymentId = correctsPaymentId;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.paidAt = paidAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public Method getMethod() {
        return method;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getCorrectsPaymentId() {
        return correctsPaymentId;
    }

    public String getReason() {
        return reason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void markCorrected() {
        this.status = Status.CORRECTED;
    }
}
