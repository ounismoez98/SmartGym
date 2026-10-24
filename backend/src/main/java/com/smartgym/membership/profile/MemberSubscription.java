package com.smartgym.membership.profile;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription")
public class MemberSubscription {
    public enum Status {
        ACTIVE,
        EXPIRED,
        CANCELLED
    }

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    protected MemberSubscription() {}

    public MemberSubscription(UUID id, UUID memberId, Status status) {
        this.id = id;
        this.memberId = memberId;
        this.status = status;
    }
}
