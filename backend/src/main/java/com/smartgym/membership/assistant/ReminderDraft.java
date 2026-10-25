package com.smartgym.membership.assistant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reminder_draft")
public class ReminderDraft {
    public enum Status {
        DRAFT,
        SENT,
        REJECTED
    }

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected ReminderDraft() {}

    public ReminderDraft(
            UUID id, UUID memberId, UUID subscriptionId, String subject, String body, String createdBy) {
        this.id = id;
        this.memberId = memberId;
        this.subscriptionId = subscriptionId;
        this.subject = subject;
        this.body = body;
        this.status = Status.DRAFT;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
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

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void edit(String subject, String body) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Only draft reminders can be edited.");
        }
        this.subject = subject;
        this.body = body;
    }

    public void markSent(Instant sentAt) {
        requireDraft();
        this.status = Status.SENT;
        this.confirmedAt = sentAt;
        this.sentAt = sentAt;
    }

    public void reject(Instant rejectedAt) {
        requireDraft();
        this.status = Status.REJECTED;
        this.rejectedAt = rejectedAt;
    }

    private void requireDraft() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Only draft reminders can be confirmed or rejected.");
        }
    }
}
