package com.smartgym.membership.profile;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ProvisioningRequest(
        @NotEmpty List<@Valid Profile> profiles,
        List<@Valid CoachAssignmentRequest> coachAssignments) {
    public record Profile(
            @NotBlank String subject,
            @NotBlank String username,
            @NotBlank String displayName,
            @NotBlank String profileType) {}

    public record CoachAssignmentRequest(
            @NotBlank String coachSubject,
            @NotBlank String memberSubject) {}
}
