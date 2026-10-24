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
        for (ProvisioningRequest.Profile profile : request.profiles()) {
            switch (profile.profileType().toUpperCase(Locale.ROOT)) {
                case "MEMBER" -> memberRepository.findByKeycloakUserId(profile.subject())
                        .ifPresentOrElse(
                                member -> {
                                    member.updateIdentity(profile.username(), profile.displayName());
                                    memberRepository.save(member);
                                },
                                () -> memberRepository.save(new Member(
                                        UUID.randomUUID(),
                                        profile.subject(),
                                        profile.username(),
                                        profile.displayName())));
                case "EMPLOYEE" -> employeeRepository.findByKeycloakUserId(profile.subject())
                        .ifPresentOrElse(
                                employee -> {
                                    employee.updateIdentity(profile.username(), profile.displayName());
                                    employeeRepository.save(employee);
                                },
                                () -> employeeRepository.save(new Employee(
                                        UUID.randomUUID(),
                                        profile.subject(),
                                        profile.username(),
                                        profile.displayName())));
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
            if (!assignmentRepository.existsByCoachSubjectAndMemberSubject(
                    assignment.coachSubject(), assignment.memberSubject())) {
                assignmentRepository.save(new CoachAssignment(
                        assignment.coachSubject(), assignment.memberSubject()));
            }
        }

        return new ProvisioningResult(request.profiles().size(), assignments.size());
    }

    public record ProvisioningResult(int profilesProcessed, int assignmentsProcessed) {}
}
