package com.smartgym.membership.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubscriptionCancelRequest(@NotBlank @Size(max = 2000) String reason) {}
