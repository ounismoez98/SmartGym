package com.smartgym.training.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ProgrammeSessionRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 20) List<@Valid ProgrammeItemRequest> items) {}
