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
@Table(name = "employee")
public class Employee {
    public enum Status {
        ACTIVE,
        INACTIVE
    }

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false, unique = true, length = 64)
    private String keycloakUserId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {}

    public Employee(UUID id, String keycloakUserId, String username, String displayName) {
        this.id = id;
        this.keycloakUserId = keycloakUserId;
        this.username = username;
        this.displayName = displayName;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Status getStatus() {
        return status;
    }

    public void updateIdentity(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }
}
