package com.smartgym.membership.profile;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.smartgym.membership.assistant.RenewalFact;

public interface MemberSubscriptionRepository
        extends JpaRepository<MemberSubscription, UUID>, JpaSpecificationExecutor<MemberSubscription> {
    boolean existsByMemberIdAndStatus(UUID memberId, MemberSubscription.Status status);

    boolean existsByPlanId(UUID planId);

    boolean existsByMemberIdAndStatusAndEndsAtAfter(
            UUID memberId, MemberSubscription.Status status, java.time.Instant now);

    java.util.Optional<MemberSubscription> findByIdAndMemberId(UUID id, UUID memberId);

    List<MemberSubscription> findAllByMemberId(UUID memberId);

    @Query("select subscription from MemberSubscription subscription "
            + "where subscription.status = com.smartgym.membership.profile.MemberSubscription.Status.ACTIVE "
            + "and subscription.endsAt <= :now")
    List<MemberSubscription> findActiveSubscriptionsDueForExpiration(@Param("now") Instant now);

    @Query("select new com.smartgym.membership.assistant.RenewalFact("
            + "member.firstName, plan.name, subscription.endsAt, plan.priceCents, plan.currency) "
            + "from MemberSubscription subscription "
            + "join Member member on member.id = subscription.memberId "
            + "join SubscriptionPlan plan on plan.id = subscription.planId "
            + "where subscription.status = com.smartgym.membership.profile.MemberSubscription.Status.ACTIVE "
            + "and subscription.endsAt >= :from and subscription.endsAt <= :to "
            + "order by subscription.endsAt")
    List<RenewalFact> findRenewalsBetween(@Param("from") Instant from, @Param("to") Instant to);
}
