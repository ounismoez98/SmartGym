package com.smartgym.membership.security;

import java.util.UUID;

import com.smartgym.membership.profile.CoachAssignmentRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("accessChecker")
public class AccessChecker {
    private final MemberRepository memberRepository;
    private final CoachAssignmentRepository assignmentRepository;

    public AccessChecker(
            MemberRepository memberRepository,
            CoachAssignmentRepository assignmentRepository) {
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public boolean isOwner(UUID memberId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_MEMBER") || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return memberRepository.findById(memberId)
                .map(Member::getKeycloakUserId)
                .filter(jwt.getSubject()::equals)
                .isPresent();
    }

    public boolean isAssignedCoach(UUID memberId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_COACH") || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return memberRepository.findById(memberId)
                .map(Member::getKeycloakUserId)
                .map(memberSubject -> assignmentRepository.existsByCoachSubjectAndMemberSubject(
                        jwt.getSubject(), memberSubject))
                .orElse(false);
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
