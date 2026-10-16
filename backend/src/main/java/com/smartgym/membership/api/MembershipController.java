package com.smartgym.membership.api;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.smartgym.membership.profile.BusinessProfile;
import com.smartgym.membership.profile.BusinessProfileRepository;
import com.smartgym.membership.profile.CoachAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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

    private final BusinessProfileRepository profileRepository;
    private final CoachAssignmentRepository assignmentRepository;

    public MembershipController(
            BusinessProfileRepository profileRepository,
            CoachAssignmentRepository assignmentRepository) {
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @GetMapping("/me")
    public MembershipIdentity me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = applicationRoles(jwt);
        BusinessProfile profile = profileRepository.findById(jwt.getSubject()).orElse(null);
        boolean memberProfileRequired = roles.contains("MEMBER");
        boolean employeeProfileRequired = roles.contains("ADMIN") || roles.contains("COACH");
        boolean missingRequiredProfile = (memberProfileRequired
                && (profile == null || profile.getMemberProfileId() == null))
                || (employeeProfileRequired
                && (profile == null || profile.getEmployeeProfileId() == null));
        String profileStatus = roles.isEmpty()
                ? "NO_APPLICATION_ROLE"
                : missingRequiredProfile ? "ONBOARDING_REQUIRED" : "COMPLETE";
        return new MembershipIdentity(
                jwt.getSubject(),
                displayName(jwt),
                roles,
                profile == null ? null : profile.getMemberProfileId(),
                profile == null ? null : profile.getEmployeeProfileId(),
                profileStatus,
                missingRequiredProfile);
    }

    @GetMapping("/members/{memberProfileId}/private")
    public PrivateMemberRecord privateMemberRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberProfileId) {
        BusinessProfile member = profileRepository.findByMemberProfileId(memberProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member profile not found."));
        List<String> roles = applicationRoles(jwt);
        boolean administrator = roles.contains("ADMIN");
        boolean memberSelf = roles.contains("MEMBER") && jwt.getSubject().equals(member.getSubject());
        boolean assignedCoach = roles.contains("COACH")
                && assignmentRepository.existsByCoachSubjectAndMemberSubject(
                        jwt.getSubject(), member.getSubject());
        if (!administrator && !memberSelf && !assignedCoach) {
            throw new AccessDeniedException("This member record is not assigned to your account.");
        }
        return new PrivateMemberRecord(
                memberProfileId,
                member.getDisplayName(),
                "Development sample: 1 upcoming reservation",
                "Development sample: 1 active training plan");
    }

    @GetMapping("/admin/operations")
    public OperationsStatus operationsStatus() {
        return new OperationsStatus(
                "AVAILABLE",
                List.of("membership", "courses", "training", "nutrition"));
    }

    @GetMapping("/coach/assigned-members")
    public List<AssignedMember> assignedMembers(@AuthenticationPrincipal Jwt jwt) {
        List<String> assignedSubjects = assignmentRepository.findAllByCoachSubject(jwt.getSubject())
                .stream()
                .map(assignment -> assignment.getMemberSubject())
                .toList();
        return profileRepository.findAllById(assignedSubjects)
                .stream()
                .filter(profile -> profile.getMemberProfileId() != null)
                .map(profile -> new AssignedMember(profile.getMemberProfileId(), profile.getDisplayName()))
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
