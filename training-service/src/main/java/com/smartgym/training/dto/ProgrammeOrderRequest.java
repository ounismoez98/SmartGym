package com.smartgym.training.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ProgrammeOrderRequest(
        @NotNull @Min(0) Long version,
        @NotEmpty List<@NotNull UUID> orderedIds) {}
