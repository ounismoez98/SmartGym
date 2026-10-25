package com.smartgym.membership.profile;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.api.SubscriptionCancelRequest;
import com.smartgym.membership.api.SubscriptionCreateRequest;
import com.smartgym.membership.api.SubscriptionPageResponse;
import com.smartgym.membership.api.SubscriptionResponse;
import com.smartgym.membership.api.SubscriptionUpdateRequest;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.security.AccessChecker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class SubscriptionService {
    private final MemberRepository memberRepository;
    private final SubscriptionPlanRepository planRepository;
    private final MemberSubscriptionRepository subscriptionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccessChecker accessChecker;

    public SubscriptionService(
            MemberRepository memberRepository,
            SubscriptionPlanRepository planRepository,
            MemberSubscriptionRepository subscriptionRepository,
            AuditLogRepository auditLogRepository,
            AccessChecker accessChecker) {
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogRepository = auditLogRepository;
        this.accessChecker = accessChecker;
    }

    @Transactional(readOnly = true)
    public SubscriptionPageResponse list(
            UUID requestedMemberId,
            MemberSubscription.Status status,
            UUID planId,
            int page,
            int size,
            Authentication authentication) {
        SubscriptionPlanService.validatePage(page, size);
        UUID scopeMemberId = requestedMemberId;
        if (!isAdmin(authentication)) {
            if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member identity is unavailable.");
            }
            if (requestedMemberId != null && !accessChecker.isOwner(requestedMemberId, authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Members may read only their own subscriptions.");
            }
            Member self = memberRepository.findByKeycloakUserId(jwt.getSubject()).orElse(null);
            if (self == null) {
                return SubscriptionPageResponse.from(Page.empty(PageRequest.of(page, size)));
            }
            scopeMemberId = self.getId();
        }
        UUID finalMemberId = scopeMemberId;
        Specification<MemberSubscription> filter = (root, criteria, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (finalMemberId != null) {
                predicates.add(builder.equal(root.get("memberId"), finalMemberId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (planId != null) {
                predicates.add(builder.equal(root.get("planId"), planId));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(page, size);
        Page<SubscriptionResponse> results = subscriptionRepository.findAll(filter, pageable)
                .map(SubscriptionResponse::from);
        return SubscriptionPageResponse.from(results);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get(UUID id) {
        return SubscriptionResponse.from(findSubscription(id));
    }

    public SubscriptionResponse create(
            SubscriptionCreateRequest request, Authentication authentication) {
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found."));
        if (member.getStatus() != Member.Status.ACTIVE) {
            throw conflict("An inactive member cannot receive a subscription.");
        }
        SubscriptionPlan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription plan not found."));
        if (!plan.isActive()) {
            throw conflict("An inactive subscription plan cannot be used.");
        }
        if (subscriptionRepository.existsByMemberIdAndStatus(
                member.getId(), MemberSubscription.Status.ACTIVE)) {
            throw conflict("The member already has an active subscription.");
        }
        Instant startsAt = Instant.now();
        Instant endsAt = addDuration(startsAt, plan.getDurationDays());
        MemberSubscription subscription = subscriptionRepository.save(new MemberSubscription(
                UUID.randomUUID(), member.getId(), plan.getId(), startsAt, endsAt));
        audit(authentication, "SUBSCRIPTION_CREATED", subscription, List.of("memberId", "planId", "startsAt", "endsAt", "status"));
        return SubscriptionResponse.from(subscription);
    }

    public SubscriptionResponse updatePlan(
            UUID id, SubscriptionUpdateRequest request, Authentication authentication) {
        MemberSubscription subscription = findSubscription(id);
        if (subscription.getStatus() != MemberSubscription.Status.ACTIVE) {
            throw conflict("Only an active subscription can be modified.");
        }
        SubscriptionPlan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription plan not found."));
        if (!plan.isActive()) {
            throw conflict("An inactive subscription plan cannot be used.");
        }
        UUID previousPlanId = subscription.getPlanId();
        subscription.changePlan(plan.getId());
        subscriptionRepository.save(subscription);
        auditLogRepository.save(new AuditLog(
                SubscriptionPlanService.actor(authentication),
                "SUBSCRIPTION_UPDATED",
                "SUBSCRIPTION",
                subscription.getId(),
                Map.of(
                        "memberId", subscription.getMemberId().toString(),
                        "oldPlanId", previousPlanId.toString(),
                        "newPlanId", plan.getId().toString())));
        return SubscriptionResponse.from(subscription);
    }

    public SubscriptionResponse cancel(
            UUID id, SubscriptionCancelRequest request, Authentication authentication) {
        MemberSubscription subscription = findSubscription(id);
        if (subscription.getStatus() != MemberSubscription.Status.ACTIVE) {
            throw conflict("Only an active subscription can be cancelled.");
        }
        subscription.cancel(request.reason().trim(), Instant.now());
        subscriptionRepository.save(subscription);
        audit(authentication, "SUBSCRIPTION_CANCELLED", subscription, List.of("status", "cancelledAt", "cancelReason"));
        return SubscriptionResponse.from(subscription);
    }

    public int expireDueSubscriptions(Instant now) {
        List<MemberSubscription> dueSubscriptions = subscriptionRepository.findActiveSubscriptionsDueForExpiration(now);
        for (MemberSubscription subscription : dueSubscriptions) {
            subscription.expire(now);
            subscriptionRepository.save(subscription);
            auditSystem("SUBSCRIPTION_EXPIRED", subscription);
        }
        return dueSubscriptions.size();
    }

    private MemberSubscription findSubscription(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found."));
    }

    private void audit(
            Authentication authentication, String action, MemberSubscription subscription, List<String> fields) {
        auditLogRepository.save(new AuditLog(
                SubscriptionPlanService.actor(authentication),
                action,
                "SUBSCRIPTION",
                subscription.getId(),
                Map.of("memberId", subscription.getMemberId().toString(), "fields", fields)));
    }

    private void auditSystem(String action, MemberSubscription subscription) {
        auditLogRepository.save(new AuditLog(
                "system:scheduler",
                action,
                "SUBSCRIPTION",
                subscription.getId(),
                Map.of("memberId", subscription.getMemberId().toString(), "fields", List.of("status"))));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private static Instant addDuration(Instant start, int days) {
        try {
            return start.plus(days, ChronoUnit.DAYS);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The plan duration is out of range.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
