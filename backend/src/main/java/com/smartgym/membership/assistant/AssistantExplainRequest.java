package com.smartgym.membership.assistant;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantExplainRequest(UUID planId, @NotBlank @Size(max = 2000) String question) {}
