package com.smartgym.training.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "training_outbox")
public class TrainingOutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(name = "routing_key", nullable = false, length = 160)
    private String routingKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected TrainingOutboxMessage() {}

    public TrainingOutboxMessage(UUID messageId, String routingKey, String payloadJson) {
        this.messageId = messageId;
        this.routingKey = routingKey;
        this.payloadJson = payloadJson;
    }

    @PrePersist
    void beforeInsert() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markPublished() {
        publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        lastError = null;
    }

    public void markPublishFailed(String error) {
        attemptCount++;
        lastError = error == null ? "Publisher confirm failed." : error.substring(0, Math.min(1000, error.length()));
    }

    public UUID getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public String getRoutingKey() { return routingKey; }
    public String getPayloadJson() { return payloadJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
}
