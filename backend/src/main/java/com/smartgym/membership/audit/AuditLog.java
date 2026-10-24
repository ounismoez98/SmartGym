package com.smartgym.membership.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    private UUID id;

    @Column(name = "actor_user_id", nullable = false, length = 64)
    private String actorUserId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLog() {}

    public AuditLog(
            String actorUserId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> details) {
        this.id = UUID.randomUUID();
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = Map.copyOf(details);
        this.occurredAt = Instant.now();
    }

    public String getAction() {
        return action;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public UUID getEntityId() {
        return entityId;
    }
}
