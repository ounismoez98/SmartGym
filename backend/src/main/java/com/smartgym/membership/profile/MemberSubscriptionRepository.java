package com.smartgym.membership.profile;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberSubscriptionRepository extends JpaRepository<MemberSubscription, UUID> {
    boolean existsByMemberIdAndStatus(UUID memberId, MemberSubscription.Status status);
}
