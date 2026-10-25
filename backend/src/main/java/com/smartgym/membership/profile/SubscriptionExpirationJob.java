package com.smartgym.membership.profile;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionExpirationJob {
    private final SubscriptionService subscriptionService;

    public SubscriptionExpirationJob(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void expireSubscriptions() {
        subscriptionService.expireDueSubscriptions(Instant.now());
    }
}
