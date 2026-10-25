package com.smartgym.membership.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "\"member\"")
public class Member {
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

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 254)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Member() {}

    public Member(UUID id, String keycloakUserId, String username, String displayName) {
        this.id = id;
        this.keycloakUserId = keycloakUserId;
        this.username = username;
        this.displayName = displayName;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Member(
            UUID id,
            String keycloakUserId,
            String username,
            String displayName,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthDate) {
        this(id, keycloakUserId, username, displayName);
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
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

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateIdentity(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public void updateDetails(
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthDate) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.displayName = displayName(firstName, lastName, keycloakUserId);
        this.updatedAt = Instant.now();
    }

    public void updateContact(String email, String phone) {
        this.email = email;
        this.phone = phone;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = Status.INACTIVE;
        this.updatedAt = Instant.now();
    }

    private static String displayName(String firstName, String lastName, String fallback) {
        String name = ((firstName == null ? "" : firstName.trim())
                + " " + (lastName == null ? "" : lastName.trim())).trim();
        return name.isEmpty() ? fallback : name;
    }
}
