package com.smartgym.membership.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record SubscriptionCreateRequest(@NotNull UUID memberId, @NotNull UUID planId) {}
