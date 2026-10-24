package com.smartgym.membership.api;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record MemberCreateRequest(
        @NotNull UUID keycloakUserId,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Email @Size(max = 254) String email,
        @Size(max = 40) String phone,
        @Past LocalDate birthDate) {}
