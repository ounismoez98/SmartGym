package com.smartgym.membership.profile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProvisioningService {
    private final MemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;
    private final CoachAssignmentRepository assignmentRepository;

    public ProvisioningService(
            MemberRepository memberRepository,
            EmployeeRepository employeeRepository,
            CoachAssignmentRepository assignmentRepository) {
        this.memberRepository = memberRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public ProvisioningResult provision(ProvisioningRequest request) {
        java.util.ArrayList<ProvisionedProfile> provisionedProfiles = new java.util.ArrayList<>();
        for (ProvisioningRequest.Profile profile : request.profiles()) {
            switch (profile.profileType().toUpperCase(Locale.ROOT)) {
                case "MEMBER" -> {
                    Member member = memberRepository.findByKeycloakUserId(profile.subject())
                            .orElseGet(() -> new Member(
                                    UUID.randomUUID(),
                                    profile.subject(),
                                    profile.username(),
                                    profile.displayName()));
                    member.updateIdentity(profile.username(), profile.displayName());
                    Member saved = memberRepository.save(member);
                    provisionedProfiles.add(new ProvisionedProfile(
                            profile.subject(), profile.username(), saved.getId(), null));
                }
                case "EMPLOYEE" -> {
                    Employee employee = employeeRepository.findByKeycloakUserId(profile.subject())
                            .orElseGet(() -> new Employee(
                                    UUID.randomUUID(),
                                    profile.subject(),
                                    profile.username(),
                                    profile.displayName()));
                    employee.updateIdentity(profile.username(), profile.displayName());
                    Employee saved = employeeRepository.save(employee);
                    provisionedProfiles.add(new ProvisionedProfile(
                            profile.subject(), profile.username(), null, saved.getId()));
                }
                default -> throw new IllegalArgumentException(
                        "Profile type must be MEMBER or EMPLOYEE.");
            }
        }

        List<ProvisioningRequest.CoachAssignmentRequest> assignments =
                request.coachAssignments() == null ? List.of() : request.coachAssignments();
        for (ProvisioningRequest.CoachAssignmentRequest assignment : assignments) {
            if (employeeRepository.findByKeycloakUserId(assignment.coachSubject()).isEmpty()) {
                throw new IllegalArgumentException("Coach profile is not provisioned.");
            }
            if (memberRepository.findByKeycloakUserId(assignment.memberSubject()).isEmpty()) {
                throw new IllegalArgumentException("Member profile is not provisioned.");
            }
            if (!assignmentRepository.existsByCoachSubjectIgnoreCaseAndMemberSubjectIgnoreCaseAndEndedAtIsNull(
                    assignment.coachSubject(), assignment.memberSubject())) {
                assignmentRepository.save(new CoachAssignment(
                        assignment.coachSubject(), assignment.memberSubject()));
            }
        }

        return new ProvisioningResult(
                request.profiles().size(),
                assignments.size(),
                List.copyOf(provisionedProfiles));
    }

    public record ProvisioningResult(
            int profilesProcessed,
            int assignmentsProcessed,
            List<ProvisionedProfile> profiles) {}

    public record ProvisionedProfile(
            String subject,
            String username,
            java.util.UUID memberProfileId,
            java.util.UUID employeeProfileId) {}
}
