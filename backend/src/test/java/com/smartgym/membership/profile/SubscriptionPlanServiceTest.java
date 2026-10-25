package com.smartgym.membership.profile;

import java.util.Optional;
import java.util.UUID;

import com.smartgym.membership.api.SubscriptionPlanCreateRequest;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
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

class SubscriptionPlanServiceTest {
    private final SubscriptionPlanRepository planRepository = mock(SubscriptionPlanRepository.class);
    private final MemberSubscriptionRepository subscriptionRepository = mock(MemberSubscriptionRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final SubscriptionPlanService service =
            new SubscriptionPlanService(planRepository, subscriptionRepository, auditLogRepository);

    @Test
    void createNormalizesCurrencyAndWritesAudit() {
        when(planRepository.existsByCodeIgnoreCase("MONTHLY")).thenReturn(false);
        when(planRepository.save(any(SubscriptionPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                new SubscriptionPlanCreateRequest("MONTHLY", "Monthly", "Gym access", 30, 5000, "eur"),
                authentication());

        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.conditions()).isEqualTo("Gym access");
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void createRejectsDuplicatePlanCode() {
        when(planRepository.existsByCodeIgnoreCase("MONTHLY")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        new SubscriptionPlanCreateRequest("MONTHLY", "Monthly", "Gym access", 30, 5000, "EUR"),
                        authentication()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(planRepository, never()).save(any(SubscriptionPlan.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void deleteRejectsPlanReferencedBySubscriptionHistory() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId))
                .thenReturn(Optional.of(new SubscriptionPlan(planId, "CODE", "Plan", "Terms", 30, 1000, "EUR")));
        when(subscriptionRepository.existsByPlanId(planId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(planId, authentication()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(planRepository, never()).delete(any(SubscriptionPlan.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    private static JwtAuthenticationToken authentication() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("admin-sub").build();
        return new JwtAuthenticationToken(jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
