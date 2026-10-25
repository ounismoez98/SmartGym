package com.smartgym.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProgrammeSessionChangeRequest(
        @NotNull @Min(0) Long version,
        @NotNull @Valid ProgrammeSessionRequest session) {}
