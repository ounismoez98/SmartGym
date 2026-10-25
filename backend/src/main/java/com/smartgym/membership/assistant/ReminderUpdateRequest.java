package com.smartgym.membership.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReminderUpdateRequest(
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 10000) String body) {}
