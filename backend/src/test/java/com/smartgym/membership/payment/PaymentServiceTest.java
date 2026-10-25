package com.smartgym.membership.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartgym.membership.api.PaymentCorrectionRequest;
import com.smartgym.membership.api.PaymentCreateRequest;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import com.smartgym.membership.profile.MemberSubscription;
import com.smartgym.membership.profile.MemberSubscriptionRepository;
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

class PaymentServiceTest {
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final MemberSubscriptionRepository subscriptionRepository = mock(MemberSubscriptionRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AccessChecker accessChecker = mock(AccessChecker.class);
    private final PaymentService service = new PaymentService(
            paymentRepository, memberRepository, subscriptionRepository, auditLogRepository, accessChecker);

    @Test
    void createUsesDefaultCurrencyAndAuditsActor() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.findById(memberId))
                .thenReturn(Optional.of(new Member(memberId, "member-sub", "member", "Member")));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                new PaymentCreateRequest(memberId, null, 2000, null, Payment.Method.CASH, null),
                authentication("admin-sub", "ADMIN"));

        assertThat(response.currency()).isEqualTo("TND");
        assertThat(response.status()).isEqualTo(Payment.Status.RECORDED);
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(
                audit -> audit.getAction().equals("PAYMENT_RECORDED")
                        && audit.getActorUserId().equals("admin-sub")));
    }

    @Test
    void createRejectsSubscriptionBelongingToAnotherMember() {
        UUID memberId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        when(memberRepository.findById(memberId))
                .thenReturn(Optional.of(new Member(memberId, "member-sub", "member", "Member")));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(new MemberSubscription(
                subscriptionId, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(60))));

        assertThatThrownBy(() -> service.create(
                        new PaymentCreateRequest(memberId, subscriptionId, 1000, "TND", Payment.Method.CARD, null),
                        authentication("admin-sub", "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void createRejectsFuturePaidAt() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.findById(memberId))
                .thenReturn(Optional.of(new Member(memberId, "member-sub", "member", "Member")));

        assertThatThrownBy(() -> service.create(
                        new PaymentCreateRequest(
                                memberId,
                                null,
                                1000,
                                "TND",
                                Payment.Method.CASH,
                                Instant.now().plusSeconds(60)),
                        authentication("admin-sub", "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void correctionKeepsOriginalAmountAndAuditsOldAndNewAmounts() {
        UUID paymentId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Payment original = new Payment(
                paymentId, memberId, null, 1000, "TND", Payment.Method.CARD,
                null, null, "original-actor", Instant.now());
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(original));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var correction = service.correct(
                paymentId,
                new PaymentCorrectionRequest(1250, "Amount was entered incorrectly"),
                authentication("admin-sub", "ADMIN"));

        assertThat(original.getStatus()).isEqualTo(Payment.Status.CORRECTED);
        assertThat(original.getAmountCents()).isEqualTo(1000);
        assertThat(correction.amountCents()).isEqualTo(1250);
        assertThat(correction.correctsPaymentId()).isEqualTo(paymentId);
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(
                audit -> audit.getAction().equals("PAYMENT_CORRECTED")
                        && audit.getActorUserId().equals("admin-sub")
                        && audit.getDetails().get("oldAmountCents").equals(1000L)
                        && audit.getDetails().get("newAmountCents").equals(1250L)
                        && audit.getDetails().get("reason").equals("Amount was entered incorrectly")));
    }

    @Test
    void correctionRejectsAlreadyCorrectedPayment() {
        UUID paymentId = UUID.randomUUID();
        Payment original = new Payment(
                paymentId, UUID.randomUUID(), null, 1000, "TND", Payment.Method.CASH,
                null, null, "original-actor", Instant.now());
        original.markCorrected();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.correct(
                        paymentId,
                        new PaymentCorrectionRequest(900, "Correction attempt"),
                        authentication("admin-sub", "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void totalCountsOnlyRecordedPayments() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.existsById(memberId)).thenReturn(true);
        when(paymentRepository.sumAmountCentsByMemberIdAndStatus(memberId, Payment.Status.RECORDED))
                .thenReturn(1250L);

        assertThat(service.totalForMember(memberId).totalPaidCents()).isEqualTo(1250L);
        verify(paymentRepository).sumAmountCentsByMemberIdAndStatus(memberId, Payment.Status.RECORDED);
    }

    private static JwtAuthenticationToken authentication(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
