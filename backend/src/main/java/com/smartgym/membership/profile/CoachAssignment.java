package com.smartgym.membership.profile;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
        name = "coach_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"coach_subject", "member_subject"}))
public class CoachAssignment {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "coach_subject", nullable = false, length = 64)
    private String coachSubject;

    @Column(name = "member_subject", nullable = false, length = 64)
    private String memberSubject;

    protected CoachAssignment() {}

    public CoachAssignment(String coachSubject, String memberSubject) {
        this.coachSubject = coachSubject;
        this.memberSubject = memberSubject;
    }

    public String getCoachSubject() {
        return coachSubject;
    }

    public String getMemberSubject() {
        return memberSubject;
    }
}
