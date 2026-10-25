package com.smartgym.membership.assistant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "gym_policy")
public class GymPolicy {
    @Id
    private UUID id;

    @Column(name = "policy_key", nullable = false, unique = true, length = 80)
    private String policyKey;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private boolean validated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GymPolicy() {}

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}
