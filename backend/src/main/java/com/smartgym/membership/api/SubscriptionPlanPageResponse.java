package com.smartgym.membership.api;

import java.util.List;

import org.springframework.data.domain.Page;

public record SubscriptionPlanPageResponse(
        List<SubscriptionPlanResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public static SubscriptionPlanPageResponse from(Page<SubscriptionPlanResponse> page) {
        return new SubscriptionPlanPageResponse(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
