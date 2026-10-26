package com.smartgym.membership.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberAccountCreateRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
        @NotBlank @Size(min = 12, max = 72) String temporaryPassword,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 40) String phone,
        java.time.LocalDate birthDate) {}
