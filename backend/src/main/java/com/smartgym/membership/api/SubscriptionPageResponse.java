package com.smartgym.membership.api;

import java.util.List;

import org.springframework.data.domain.Page;

public record SubscriptionPageResponse(
        List<SubscriptionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public static SubscriptionPageResponse from(Page<SubscriptionResponse> page) {
        return new SubscriptionPageResponse(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
