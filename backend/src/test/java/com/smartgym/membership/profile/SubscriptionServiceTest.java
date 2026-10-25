package com.smartgym.membership.profile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartgym.membership.api.SubscriptionCancelRequest;
import com.smartgym.membership.api.SubscriptionCreateRequest;
import com.smartgym.membership.api.SubscriptionUpdateRequest;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.events.MembershipEventPublisher;
import com.smartgym.membership.security.AccessChecker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final SubscriptionPlanRepository planRepository = mock(SubscriptionPlanRepository.class);
    private final MemberSubscriptionRepository subscriptionRepository = mock(MemberSubscriptionRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AccessChecker accessChecker = mock(AccessChecker.class);
    private final MembershipEventPublisher eventPublisher = mock(MembershipEventPublisher.class);
    private final SubscriptionService service = new SubscriptionService(
            memberRepository, planRepository, subscriptionRepository, auditLogRepository, accessChecker, eventPublisher);

    @Test
    void createCalculatesEndDateAndAudits() {
        UUID memberId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Member member = new Member(memberId, UUID.randomUUID().toString(), "member", "Member");
        SubscriptionPlan plan = plan(planId, 30, true);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByMemberIdAndStatus(memberId, MemberSubscription.Status.ACTIVE))
                .thenReturn(false);
        when(subscriptionRepository.save(any(MemberSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                new SubscriptionCreateRequest(memberId, planId), authentication("admin", "ADMIN"));

        assertThat(response.status()).isEqualTo(MemberSubscription.Status.ACTIVE);
        assertThat(response.endsAt()).isEqualTo(response.startsAt().plusSeconds(30L * 24 * 60 * 60));
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(
                audit -> audit.getAction().equals("SUBSCRIPTION_CREATED")
                        && audit.getActorUserId().equals("admin")));
    }

    @Test
    void createRejectsSecondActiveSubscription() {
        UUID memberId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(memberRepository.findById(memberId))
                .thenReturn(Optional.of(new Member(memberId, "member-sub", "member", "Member")));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId, 30, true)));
        when(subscriptionRepository.existsByMemberIdAndStatus(memberId, MemberSubscription.Status.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        new SubscriptionCreateRequest(memberId, planId), authentication("admin", "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(subscriptionRepository, never()).save(any(MemberSubscription.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void createRejectsInactiveMember() {
        UUID memberId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Member member = new Member(memberId, "member-sub", "member", "Member");
        member.deactivate();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.create(
                        new SubscriptionCreateRequest(memberId, planId), authentication("admin", "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(planRepository, never()).findById(any());
        verify(subscriptionRepository, never()).save(any(MemberSubscription.class));
    }

    @Test
    void updateChangesPlanWithoutChangingSubscriptionDatesAndAuditsBothPlans() {
        UUID subscriptionId = UUID.randomUUID();
        UUID previousPlanId = UUID.randomUUID();
        UUID newPlanId = UUID.randomUUID();
        Instant startsAt = Instant.now().minusSeconds(60);
        MemberSubscription subscription = new MemberSubscription(
                subscriptionId, UUID.randomUUID(), previousPlanId, startsAt, startsAt.plusSeconds(60));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(newPlanId)).thenReturn(Optional.of(plan(newPlanId, 45, true)));

        var response = service.updatePlan(
                subscriptionId, new SubscriptionUpdateRequest(newPlanId), authentication("admin", "ADMIN"));

        assertThat(response.planId()).isEqualTo(newPlanId);
        assertThat(response.startsAt()).isEqualTo(startsAt);
        assertThat(response.endsAt()).isEqualTo(startsAt.plusSeconds(60));
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(
                audit -> audit.getAction().equals("SUBSCRIPTION_UPDATED")
                        && audit.getDetails().get("oldPlanId").equals(previousPlanId.toString())
                        && audit.getDetails().get("newPlanId").equals(newPlanId.toString())));
    }

    @Test
    void updateRejectsNonActiveSubscription() {
        UUID subscriptionId = UUID.randomUUID();
        MemberSubscription subscription = new MemberSubscription(
                subscriptionId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minusSeconds(100), Instant.now().minusSeconds(50));
        subscription.expire(Instant.now());
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.updatePlan(
                        subscriptionId,
                        new SubscriptionUpdateRequest(UUID.randomUUID()),
                        authentication("admin", "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(planRepository, never()).findById(any());
        verify(subscriptionRepository, never()).save(any(MemberSubscription.class));
    }

    @Test
    void cancelRequiresActiveSubscriptionAndWritesReasonToAudit() {
        UUID subscriptionId = UUID.randomUUID();
        MemberSubscription subscription = new MemberSubscription(
                subscriptionId, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(MemberSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.cancel(
                subscriptionId,
                new SubscriptionCancelRequest("Requested by member"),
                authentication("admin", "ADMIN"));

        assertThat(response.status()).isEqualTo(MemberSubscription.Status.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("Requested by member");
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void expirationUpdatesEachDueSubscriptionAndAuditsIt() {
        Instant now = Instant.now();
        MemberSubscription due = new MemberSubscription(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now.minusSeconds(3600), now.minusSeconds(1));
        when(subscriptionRepository.findActiveSubscriptionsDueForExpiration(now)).thenReturn(List.of(due));

        assertThat(service.expireDueSubscriptions(now)).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(MemberSubscription.Status.EXPIRED);
        verify(subscriptionRepository).save(due);
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(
                audit -> audit.getAction().equals("SUBSCRIPTION_EXPIRED")
                        && audit.getActorUserId().equals("system:scheduler")));
    }

    private static SubscriptionPlan plan(UUID id, int days, boolean active) {
        SubscriptionPlan plan = new SubscriptionPlan(id, "PLAN-" + id, "Plan", "Conditions", days, 5000, "EUR");
        if (!active) {
            plan.update("Plan", "Conditions", days, 5000, "EUR", false);
        }
        return plan;
    }

    private static JwtAuthenticationToken authentication(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
