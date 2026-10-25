package com.smartgym.membership.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.smartgym.membership.profile.Member;

public record MemberResponse(
        UUID id,
        String keycloakUserId,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate birthDate,
        Member.Status status,
        Instant createdAt,
        Instant updatedAt) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getKeycloakUserId(),
                member.getFirstName(),
                member.getLastName(),
                member.getEmail(),
                member.getPhone(),
                member.getBirthDate(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getUpdatedAt());
    }
}
