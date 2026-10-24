package com.smartgym.membership.api;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.smartgym.membership.profile.CoachAssignmentRepository;
import com.smartgym.membership.profile.Employee;
import com.smartgym.membership.profile.EmployeeRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/membership")
public class MembershipController {
    private static final Set<String> APPLICATION_ROLES = Set.of("ADMIN", "COACH", "MEMBER");

    private final MemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;
    private final CoachAssignmentRepository assignmentRepository;

    public MembershipController(
            MemberRepository memberRepository,
            EmployeeRepository employeeRepository,
            CoachAssignmentRepository assignmentRepository) {
        this.memberRepository = memberRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'COACH', 'MEMBER')")
    public MembershipIdentity me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = applicationRoles(jwt);
        Member member = roles.contains("MEMBER")
                ? memberRepository.findByKeycloakUserId(jwt.getSubject()).orElse(null)
                : null;
        Employee employee = roles.contains("ADMIN") || roles.contains("COACH")
                ? employeeRepository.findByKeycloakUserId(jwt.getSubject()).orElse(null)
                : null;
        boolean memberProfileRequired = roles.contains("MEMBER");
        boolean employeeProfileRequired = roles.contains("ADMIN") || roles.contains("COACH");
        boolean missingRequiredProfile = (memberProfileRequired
                && member == null)
                || (employeeProfileRequired
                && employee == null);
        String profileStatus = roles.isEmpty()
                ? "NO_APPLICATION_ROLE"
                : missingRequiredProfile ? "ONBOARDING_REQUIRED" : "COMPLETE";
        return new MembershipIdentity(
                jwt.getSubject(),
                displayName(jwt),
                roles,
                member == null ? null : member.getId(),
                employee == null ? null : employee.getId(),
                profileStatus,
                missingRequiredProfile);
    }

    @GetMapping("/members/{memberProfileId}/private")
    @PreAuthorize("hasRole('ADMIN') or @accessChecker.isOwner(#p1, authentication) "
            + "or @accessChecker.isAssignedCoach(#p1, authentication)")
    public PrivateMemberRecord privateMemberRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberProfileId) {
        Member member = memberRepository.findById(memberProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member profile not found."));
        return new PrivateMemberRecord(
                memberProfileId,
                member.getDisplayName(),
                "Development sample: 1 upcoming reservation",
                "Development sample: 1 active training plan");
    }

    @GetMapping("/admin/operations")
    @PreAuthorize("hasRole('ADMIN')")
    public OperationsStatus operationsStatus() {
        return new OperationsStatus(
                "AVAILABLE",
                List.of("membership", "courses", "training", "nutrition"));
    }

    @GetMapping("/coach/assigned-members")
    @PreAuthorize("hasAnyRole('ADMIN', 'COACH')")
    public List<AssignedMember> assignedMembers(@AuthenticationPrincipal Jwt jwt) {
        List<String> assignedSubjects = assignmentRepository.findAllByCoachSubject(jwt.getSubject())
                .stream()
                .map(assignment -> assignment.getMemberSubject())
                .toList();
        return memberRepository.findAllByKeycloakUserIdIn(
                        assignedSubjects.stream().map(subject -> subject.toLowerCase(Locale.ROOT)).toList())
                .stream()
                .map(profile -> new AssignedMember(profile.getId(), profile.getDisplayName()))
                .sorted(Comparator.comparing(AssignedMember::displayName))
                .toList();
    }

    private static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String username = jwt.getClaimAsString("preferred_username");
        return username == null || username.isBlank() ? jwt.getSubject() : username;
    }

    private static List<String> applicationRoles(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaims().get("realm_access");
        if (!(realmAccessClaim instanceof java.util.Map<?, ?> realmAccess)) {
            return List.of();
        }
        Object rolesClaim = realmAccess.get("roles");
        if (!(rolesClaim instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(APPLICATION_ROLES::contains)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public record MembershipIdentity(
            String subject,
            String displayName,
            List<String> roles,
            UUID memberProfileId,
            UUID employeeProfileId,
            String profileStatus,
            boolean onboardingRequired) {}

    public record PrivateMemberRecord(
            UUID memberProfileId,
            String memberDisplayName,
            String reservationSummary,
            String trainingPlanSummary) {}

    public record OperationsStatus(String status, List<String> enabledModuleAreas) {}

    public record AssignedMember(UUID memberProfileId, String displayName) {}
}
