package com.smartgym.membership.assistant;

import java.time.Instant;

public record RenewalFact(
        String firstName, String planName, Instant endsAt, Long priceCents, String currency) {}
