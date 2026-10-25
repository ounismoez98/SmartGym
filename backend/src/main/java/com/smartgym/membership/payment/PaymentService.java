package com.smartgym.membership.payment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.api.PaymentCorrectionRequest;
import com.smartgym.membership.api.PaymentCreateRequest;
import com.smartgym.membership.api.PaymentPageResponse;
import com.smartgym.membership.api.PaymentResponse;
import com.smartgym.membership.api.PaymentTotalResponse;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.events.MembershipEventPublisher;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import com.smartgym.membership.profile.MemberSubscription;
import com.smartgym.membership.profile.MemberSubscriptionRepository;
import com.smartgym.membership.security.AccessChecker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PaymentService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final MemberSubscriptionRepository subscriptionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccessChecker accessChecker;
    private final MembershipEventPublisher eventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            MemberRepository memberRepository,
            MemberSubscriptionRepository subscriptionRepository,
            AuditLogRepository auditLogRepository,
            AccessChecker accessChecker,
            MembershipEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogRepository = auditLogRepository;
        this.accessChecker = accessChecker;
        this.eventPublisher = eventPublisher;
    }

    public PaymentResponse create(PaymentCreateRequest request, Authentication authentication) {
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> notFound("Member not found."));
        if (request.subscriptionId() != null) {
            MemberSubscription subscription = subscriptionRepository.findById(request.subscriptionId())
                    .orElseThrow(() -> notFound("Subscription not found."));
            if (!subscription.getMemberId().equals(member.getId())) {
                throw conflict("The subscription does not belong to the specified member.");
            }
        }
        Instant paidAt = request.paidAt() == null ? Instant.now() : request.paidAt();
        if (paidAt.isAfter(Instant.now())) {
            throw badRequest("paidAt cannot be in the future.");
        }

        String actor = actor(authentication);
        Payment payment = paymentRepository.save(new Payment(
                UUID.randomUUID(),
                member.getId(),
                request.subscriptionId(),
                request.amountCents(),
                request.currency().trim().toUpperCase(Locale.ROOT),
                request.method(),
                null,
                null,
                actor,
                paidAt));
        auditLogRepository.save(new AuditLog(
                actor,
                "PAYMENT_RECORDED",
                "PAYMENT",
                payment.getId(),
                Map.of(
                        "memberId", member.getId().toString(),
                        "amountCents", payment.getAmountCents(),
                        "currency", payment.getCurrency().trim(),
                        "paidAt", payment.getPaidAt().toString())));
        eventPublisher.publish("payment.recorded", paymentEventData(payment));
        return toResponse(payment, false);
    }

    @Transactional(readOnly = true)
    public PaymentPageResponse list(
            UUID requestedMemberId,
            Payment.Status status,
            Instant from,
            Instant to,
            int page,
            int size,
            Authentication authentication) {
        validatePage(page, size);
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("from must be earlier than or equal to to.");
        }
        UUID memberId = requestedMemberId;
        if (!accessChecker.isAdministrator(authentication)) {
            if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member identity is unavailable.");
            }
            Member owner = memberRepository.findByKeycloakUserId(jwt.getSubject()).orElse(null);
            if (owner == null) {
                return PaymentPageResponse.from(Page.empty(PageRequest.of(page, size)));
            }
            if (requestedMemberId != null && !requestedMemberId.equals(owner.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Members may read only their own payments.");
            }
            memberId = owner.getId();
        }
        UUID filterMemberId = memberId;
        Specification<Payment> filter = (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (filterMemberId != null) {
                predicates.add(builder.equal(root.get("memberId"), filterMemberId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("paidAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("paidAt"), to));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paidAt"));
        Page<PaymentResponse> results = paymentRepository.findAll(filter, pageable)
                .map(payment -> toResponse(payment, false));
        return PaymentPageResponse.from(results);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id) {
        Payment payment = findPayment(id);
        return toResponse(payment, true);
    }

    public PaymentResponse correct(
            UUID id, PaymentCorrectionRequest request, Authentication authentication) {
        Payment original = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Payment not found."));
        if (original.getStatus() == Payment.Status.CORRECTED) {
            throw conflict("This payment has already been corrected.");
        }
        String actor = actor(authentication);
        Payment correction = paymentRepository.save(new Payment(
                UUID.randomUUID(),
                original.getMemberId(),
                original.getSubscriptionId(),
                request.amountCents(),
                original.getCurrency().trim(),
                original.getMethod(),
                original.getId(),
                request.reason().trim(),
                actor,
                original.getPaidAt()));
        original.markCorrected();
        paymentRepository.save(original);
        auditLogRepository.save(new AuditLog(
                actor,
                "PAYMENT_CORRECTED",
                "PAYMENT",
                original.getId(),
                Map.of(
                        "correctionPaymentId", correction.getId().toString(),
                        "oldAmountCents", original.getAmountCents(),
                        "newAmountCents", correction.getAmountCents(),
                        "reason", correction.getReason())));
        eventPublisher.publish("payment.corrected", Map.of(
                "paymentId", original.getId().toString(),
                "correctionPaymentId", correction.getId().toString(),
                "memberId", correction.getMemberId().toString(),
                "oldAmountCents", original.getAmountCents(),
                "newAmountCents", correction.getAmountCents(),
                "status", original.getStatus().name()));
        return toResponse(correction, false);
    }

    @Transactional(readOnly = true)
    public PaymentTotalResponse totalForMember(UUID memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw notFound("Member not found.");
        }
        return new PaymentTotalResponse(
                paymentRepository.sumAmountCentsByMemberIdAndStatus(memberId, Payment.Status.RECORDED));
    }

    private Payment findPayment(UUID id) {
        return paymentRepository.findById(id).orElseThrow(() -> notFound("Payment not found."));
    }

    private PaymentResponse toResponse(Payment payment, boolean includeCorrections) {
        List<PaymentResponse> corrections = includeCorrections
                ? paymentRepository.findAllByCorrectsPaymentIdOrderByCreatedAtAsc(payment.getId()).stream()
                        .map(child -> toResponse(child, true))
                        .toList()
                : List.of();
        return PaymentResponse.from(payment, corrections);
    }

    private static String actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }

    private static Map<String, Object> paymentEventData(Payment payment) {
        return Map.of(
                "paymentId", payment.getId().toString(),
                "memberId", payment.getMemberId().toString(),
                "amountCents", payment.getAmountCents(),
                "currency", payment.getCurrency().trim(),
                "status", payment.getStatus().name(),
                "paidAt", payment.getPaidAt().toString());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("Invalid pagination values.");
        }
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
