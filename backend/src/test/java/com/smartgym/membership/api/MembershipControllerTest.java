package com.smartgym.membership.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.profile.BusinessProfile;
import com.smartgym.membership.profile.BusinessProfileRepository;
import com.smartgym.membership.profile.CoachAssignment;
import com.smartgym.membership.profile.CoachAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class MembershipControllerTest {
    private static final String MEMBER_A_SUBJECT = "kc-member-a";
    private static final String MEMBER_B_SUBJECT = "kc-member-b";
    private static final String MEMBER_C_SUBJECT = "kc-member-c";
    private static final String COACH_SUBJECT = "kc-coach";
    private UUID memberAId;
    private UUID memberBId;
    private UUID memberCId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessProfileRepository profileRepository;

    @Autowired
    private CoachAssignmentRepository assignmentRepository;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        profileRepository.deleteAll();
        BusinessProfile memberA = new BusinessProfile(MEMBER_A_SUBJECT, "member-a", "Member A");
        memberA.ensureMemberProfile();
        memberAId = memberA.getMemberProfileId();
        BusinessProfile memberB = new BusinessProfile(MEMBER_B_SUBJECT, "member-b", "Member B");
        memberB.ensureMemberProfile();
        memberBId = memberB.getMemberProfileId();
        BusinessProfile memberC = new BusinessProfile(MEMBER_C_SUBJECT, "member-c", "Member C");
        memberC.ensureMemberProfile();
        memberCId = memberC.getMemberProfileId();
        BusinessProfile coach = new BusinessProfile(COACH_SUBJECT, "gym-coach", "Gym Coach");
        coach.ensureEmployeeProfile();
        profileRepository.saveAll(List.of(memberA, memberB, memberC, coach));
        assignmentRepository.save(new CoachAssignment(COACH_SUBJECT, MEMBER_A_SUBJECT));
        assignmentRepository.save(new CoachAssignment(COACH_SUBJECT, MEMBER_B_SUBJECT));
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/membership/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/membership/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void identityIncludesValidatedSubjectRolesAndBusinessProfile() throws Exception {
        mockMvc.perform(get("/api/membership/me")
                        .with(jwt().jwt(token -> token
                                .subject(MEMBER_A_SUBJECT)
                                .claim("name", "Member A")
                                .claim("realm_access", Map.of("roles", List.of("MEMBER", "UNRELATED"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(MEMBER_A_SUBJECT))
                .andExpect(jsonPath("$.displayName").value("Member A"))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.memberProfileId").value(memberAId.toString()))
                .andExpect(jsonPath("$.employeeProfileId").doesNotExist())
                .andExpect(jsonPath("$.profileStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.onboardingRequired").value(false));
    }

    @Test
    void authenticatedIdentityWithoutBusinessProfileNeedsOnboarding() throws Exception {
        mockMvc.perform(get("/api/membership/me")
                        .with(jwt().jwt(token -> token
                                .subject("not-provisioned")
                                .claim("name", "New member")
                                .claim("realm_access", Map.of("roles", List.of("MEMBER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberProfileId").doesNotExist())
                .andExpect(jsonPath("$.employeeProfileId").doesNotExist())
                .andExpect(jsonPath("$.profileStatus").value("ONBOARDING_REQUIRED"))
                .andExpect(jsonPath("$.onboardingRequired").value(true));
        org.assertj.core.api.Assertions.assertThat(profileRepository.findById("not-provisioned")).isEmpty();
    }

    @Test
    void memberCanReadOwnPrivateRecordButNotAnotherMembers() throws Exception {
        mockMvc.perform(get("/api/membership/members/{id}/private", memberAId)
                        .with(jwt().jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("MEMBER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberDisplayName").value("Member A"));

        mockMvc.perform(get("/api/membership/members/{id}/private", memberBId)
                        .with(jwt().jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("MEMBER"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void coachCanReadOnlyAssignedMemberAndAdminCanReadAnyMember() throws Exception {
        mockMvc.perform(get("/api/membership/members/{id}/private", memberAId)
                        .with(jwt().jwt(token -> token.subject(COACH_SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("COACH"))))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/membership/members/{id}/private", memberCId)
                        .with(jwt().jwt(token -> token.subject(COACH_SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("COACH"))))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/membership/members/{id}/private", memberBId)
                        .with(jwt().jwt(token -> token.subject("gym-admin")
                                .claim("realm_access", Map.of("roles", List.of("ADMIN"))))))
                .andExpect(status().isOk());
    }

    @Test
    void moduleEndpointsEnforceTheModuleRoleBeforeBusinessRouting() throws Exception {
        mockMvc.perform(get("/api/nutrition/private")
                        .with(jwt().jwt(token -> token.subject(COACH_SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("COACH"))))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/nutrition/private")
                        .with(jwt()
                                .jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void administrationAndCoachActionsRequireTheirApplicationRoles() throws Exception {
        mockMvc.perform(get("/api/membership/admin/operations")
                        .with(jwt()
                                .jwt(token -> token.subject("gym-admin")
                                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mockMvc.perform(get("/api/membership/admin/operations")
                        .with(jwt().jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("MEMBER"))))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/membership/coach/assigned-members")
                        .with(jwt()
                                .jwt(token -> token.subject(COACH_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("COACH"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/membership/coach/assigned-members")
                        .with(jwt().jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("MEMBER"))))))
                .andExpect(status().isForbidden());
    }

}
