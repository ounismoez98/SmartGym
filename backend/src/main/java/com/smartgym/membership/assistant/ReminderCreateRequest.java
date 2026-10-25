package com.smartgym.membership.assistant;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ReminderCreateRequest(@NotNull UUID subscriptionId) {}
