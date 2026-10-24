package com.smartgym.membership.profile;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "coach_assignments")
public class CoachAssignment {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "coach_subject", nullable = false, length = 64)
    private String coachSubject;

    @Column(name = "member_subject", nullable = false, length = 64)
    private String memberSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected CoachAssignment() {}

    public CoachAssignment(String coachSubject, String memberSubject) {
        this.coachSubject = coachSubject;
        this.memberSubject = memberSubject;
        this.createdAt = Instant.now();
    }

    public String getCoachSubject() {
        return coachSubject;
    }

    public UUID getId() {
        return id;
    }

    public String getMemberSubject() {
        return memberSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void finish() {
        this.endedAt = Instant.now();
    }
}
