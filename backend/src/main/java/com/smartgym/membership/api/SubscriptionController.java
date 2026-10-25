package com.smartgym.membership.api;

import java.util.UUID;

import com.smartgym.membership.profile.MemberSubscription;
import com.smartgym.membership.profile.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/membership/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(
            @Valid @RequestBody SubscriptionCreateRequest request,
            Authentication authentication) {
        return subscriptionService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
    public SubscriptionPageResponse list(
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) MemberSubscription.Status status,
            @RequestParam(required = false) UUID planId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return subscriptionService.list(memberId, status, planId, page, size, authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @accessChecker.isSubscriptionOwner(#p0, authentication)")
    public SubscriptionResponse get(@PathVariable UUID id) {
        return subscriptionService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionResponse updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionUpdateRequest request,
            Authentication authentication) {
        return subscriptionService.updatePlan(id, request, authentication);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionCancelRequest request,
            Authentication authentication) {
        return subscriptionService.cancel(id, request, authentication);
    }
}
