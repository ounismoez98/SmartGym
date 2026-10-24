package com.smartgym.membership.security;

import java.util.UUID;
import java.util.Locale;

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
                .filter(subject -> normalize(subject).equals(normalize(jwt.getSubject())))
                .isPresent();
    }

    public boolean isAssignedCoach(UUID memberId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_COACH") || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return memberRepository.findById(memberId)
                .map(Member::getKeycloakUserId)
                .map(memberSubject -> assignmentRepository.existsByCoachSubjectAndMemberSubject(
                        normalize(jwt.getSubject()), normalize(memberSubject)))
                .orElse(false);
    }

    public boolean isAdministrator(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    public boolean isMember(Authentication authentication) {
        return hasRole(authentication, "ROLE_MEMBER");
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private static String normalize(String subject) {
        return subject.toLowerCase(Locale.ROOT);
    }
}
