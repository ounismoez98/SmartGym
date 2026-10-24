package com.smartgym.membership.profile;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProvisioningService {
    private final BusinessProfileRepository profileRepository;
    private final CoachAssignmentRepository assignmentRepository;

    public ProvisioningService(
            BusinessProfileRepository profileRepository,
            CoachAssignmentRepository assignmentRepository) {
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public ProvisioningResult provision(ProvisioningRequest request) {
        java.util.ArrayList<ProvisionedProfile> provisionedProfiles = new java.util.ArrayList<>();
        for (ProvisioningRequest.Profile profile : request.profiles()) {
            BusinessProfile businessProfile = profileRepository.findById(profile.subject())
                    .orElseGet(() -> new BusinessProfile(
                            profile.subject(), profile.username(), profile.displayName()));
            businessProfile.updateIdentity(profile.username(), profile.displayName());
            switch (profile.profileType().toUpperCase(Locale.ROOT)) {
                case "MEMBER" -> businessProfile.ensureMemberProfile();
                case "EMPLOYEE" -> businessProfile.ensureEmployeeProfile();
                default -> throw new IllegalArgumentException(
                        "Profile type must be MEMBER or EMPLOYEE.");
            }
            profileRepository.save(businessProfile);
            provisionedProfiles.add(new ProvisionedProfile(
                    businessProfile.getSubject(),
                    businessProfile.getUsername(),
                    businessProfile.getMemberProfileId(),
                    businessProfile.getEmployeeProfileId()));
        }

        List<ProvisioningRequest.CoachAssignmentRequest> assignments =
                request.coachAssignments() == null ? List.of() : request.coachAssignments();
        for (ProvisioningRequest.CoachAssignmentRequest assignment : assignments) {
            BusinessProfile coach = profileRepository.findById(assignment.coachSubject())
                    .orElseThrow(() -> new IllegalArgumentException("Coach profile is not provisioned."));
            BusinessProfile member = profileRepository.findById(assignment.memberSubject())
                    .orElseThrow(() -> new IllegalArgumentException("Member profile is not provisioned."));
            if (coach.getEmployeeProfileId() == null || member.getMemberProfileId() == null) {
                throw new IllegalArgumentException("Assignments require employee and member profiles.");
            }
            if (!assignmentRepository.existsByCoachSubjectAndMemberSubject(
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
