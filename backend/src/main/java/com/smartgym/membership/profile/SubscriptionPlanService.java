package com.smartgym.membership.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.api.SubscriptionPlanCreateRequest;
import com.smartgym.membership.api.SubscriptionPlanPageResponse;
import com.smartgym.membership.api.SubscriptionPlanResponse;
import com.smartgym.membership.api.SubscriptionPlanUpdateRequest;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
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
public class SubscriptionPlanService {
    private static final int MAX_PAGE_SIZE = 100;

    private final SubscriptionPlanRepository planRepository;
    private final MemberSubscriptionRepository subscriptionRepository;
    private final AuditLogRepository auditLogRepository;

    public SubscriptionPlanService(
            SubscriptionPlanRepository planRepository,
            MemberSubscriptionRepository subscriptionRepository,
            AuditLogRepository auditLogRepository) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanPageResponse list(
            String query, Boolean active, int page, int size) {
        validatePage(page, size);
        Specification<SubscriptionPlan> filter = (root, criteria, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            if (active != null) {
                predicates.add(builder.equal(root.get("active"), active));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(page, size);
        Page<SubscriptionPlanResponse> results = planRepository.findAll(filter, pageable)
                .map(SubscriptionPlanResponse::from);
        return SubscriptionPlanPageResponse.from(results);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanResponse get(UUID id) {
        return SubscriptionPlanResponse.from(findPlan(id));
    }

    public SubscriptionPlanResponse create(
            SubscriptionPlanCreateRequest request, Authentication authentication) {
        if (planRepository.existsByCodeIgnoreCase(request.code().trim())) {
            throw conflict("The subscription plan code is already in use.");
        }
        SubscriptionPlan plan = new SubscriptionPlan(
                UUID.randomUUID(),
                request.code().trim(),
                request.name().trim(),
                request.conditions().trim(),
                request.durationDays(),
                request.priceCents(),
                request.currency().toUpperCase(java.util.Locale.ROOT));
        planRepository.save(plan);
        audit(authentication, "SUBSCRIPTION_PLAN_CREATED", plan.getId(),
                List.of("code", "name", "conditions", "durationDays", "priceCents", "currency", "active"));
        return SubscriptionPlanResponse.from(plan);
    }

    public SubscriptionPlanResponse update(
            UUID id, SubscriptionPlanUpdateRequest request, Authentication authentication) {
        SubscriptionPlan plan = findPlan(id);
        plan.update(
                request.name().trim(),
                request.conditions().trim(),
                request.durationDays(),
                request.priceCents(),
                request.currency().toUpperCase(java.util.Locale.ROOT),
                request.active());
        planRepository.save(plan);
        audit(authentication, "SUBSCRIPTION_PLAN_UPDATED", id,
                List.of("name", "conditions", "durationDays", "priceCents", "currency", "active"));
        return SubscriptionPlanResponse.from(plan);
    }

    public void delete(UUID id, Authentication authentication) {
        SubscriptionPlan plan = findPlan(id);
        if (subscriptionRepository.existsByPlanId(id)) {
            throw conflict("A subscription plan referenced by subscription history cannot be deleted.");
        }
        auditLogRepository.save(new AuditLog(
                actor(authentication),
                "SUBSCRIPTION_PLAN_DELETED",
                "SUBSCRIPTION_PLAN",
                id,
                Map.of("fields", List.of("deleted"))));
        planRepository.delete(plan);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan findPlan(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription plan not found."));
    }

    private void audit(Authentication authentication, String action, UUID entityId, List<String> fields) {
        auditLogRepository.save(new AuditLog(
                actor(authentication), action, "SUBSCRIPTION_PLAN", entityId, Map.of("fields", fields)));
    }

    static String actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getSubject()
                : authentication == null ? "system" : authentication.getName();
    }

    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination values.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
