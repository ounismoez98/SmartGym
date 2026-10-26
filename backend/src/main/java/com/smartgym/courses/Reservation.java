package com.smartgym.courses;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "reservations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_subject", "session_id"})
)
public class Reservation {
    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "member_subject", nullable = false, length = 64)
    private String memberSubject;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CourseSession session;

    @Column(nullable = false, length = 30)
    private String status = "CONFIRMED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private String memberUsername;

    protected Reservation() {
    }

    public Reservation(UUID reservationId, String memberSubject, CourseSession session, String status) {
        this.reservationId = reservationId;
        this.memberSubject = memberSubject;
        this.session = session;
        this.status = status;
    }

    public static Reservation create(String memberSubject, CourseSession session) {
        return new Reservation(UUID.randomUUID(), memberSubject, session, "CONFIRMED");
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.reservationId == null) {
            this.reservationId = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getMemberSubject() {
        return memberSubject;
    }

    public void setMemberSubject(String memberSubject) {
        this.memberSubject = memberSubject;
    }

    public CourseSession getSession() {
        return session;
    }

    public void setSession(CourseSession session) {
        this.session = session;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getMemberUsername() {
        return memberUsername == null || memberUsername.isBlank() ? "Member" : memberUsername;
    }

    public void setMemberUsername(String memberUsername) {
        this.memberUsername = memberUsername;
    }
}
