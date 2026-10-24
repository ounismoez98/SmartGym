package com.smartgym.membership.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.profile.CoachAssignment;
import com.smartgym.membership.profile.CoachAssignmentRepository;
import com.smartgym.membership.profile.Employee;
import com.smartgym.membership.profile.EmployeeRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private MemberRepository memberRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CoachAssignmentRepository assignmentRepository;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        memberRepository.deleteAll();
        employeeRepository.deleteAll();
        Member memberA = new Member(UUID.randomUUID(), MEMBER_A_SUBJECT, "member-a", "Member A");
        Member memberB = new Member(UUID.randomUUID(), MEMBER_B_SUBJECT, "member-b", "Member B");
        Member memberC = new Member(UUID.randomUUID(), MEMBER_C_SUBJECT, "member-c", "Member C");
        memberAId = memberA.getId();
        memberBId = memberB.getId();
        memberCId = memberC.getId();
        memberRepository.saveAll(List.of(memberA, memberB, memberC));
        employeeRepository.save(new Employee(UUID.randomUUID(), COACH_SUBJECT, "gym-coach", "Gym Coach"));
        employeeRepository.save(new Employee(UUID.randomUUID(), "gym-admin", "gym-admin", "Gym Administrator"));
        assignmentRepository.save(new CoachAssignment(COACH_SUBJECT, MEMBER_A_SUBJECT));
        assignmentRepository.save(new CoachAssignment(COACH_SUBJECT, MEMBER_B_SUBJECT));
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/membership/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedResponsesUseUniformJson() throws Exception {
        mockMvc.perform(get("/api/membership/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/membership/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void identityIncludesValidatedSubjectRolesAndBusinessProfile() throws Exception {
        mockMvc.perform(get("/api/membership/me")
                        .with(roleJwtWithDisplayName(MEMBER_A_SUBJECT, "Member A", "MEMBER", "UNRELATED")))
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
                        .with(roleJwtWithDisplayName("not-provisioned", "New member", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberProfileId").doesNotExist())
                .andExpect(jsonPath("$.employeeProfileId").doesNotExist())
                .andExpect(jsonPath("$.profileStatus").value("ONBOARDING_REQUIRED"))
                .andExpect(jsonPath("$.onboardingRequired").value(true));
        org.assertj.core.api.Assertions.assertThat(memberRepository.findByKeycloakUserId("not-provisioned")).isEmpty();
    }

    @Test
    void memberCanReadOwnPrivateRecordButNotAnotherMembers() throws Exception {
        mockMvc.perform(get("/api/membership/members/{id}/private", memberAId)
                        .with(roleJwt(MEMBER_A_SUBJECT, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberDisplayName").value("Member A"));

        mockMvc.perform(get("/api/membership/members/{id}/private", memberBId)
                        .with(roleJwt(MEMBER_A_SUBJECT, "MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void coachCanReadOnlyAssignedMemberAndAdminCanReadAnyMember() throws Exception {
        mockMvc.perform(get("/api/membership/members/{id}/private", memberAId)
                        .with(roleJwt(COACH_SUBJECT, "COACH")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/membership/members/{id}/private", memberCId)
                        .with(roleJwt(COACH_SUBJECT, "COACH")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/membership/members/{id}/private", memberBId)
                        .with(roleJwt("gym-admin", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void roleMatrixProtectsAllExistingMembershipRoutes() throws Exception {
        var admin = roleJwt("gym-admin", "ADMIN");
        var coach = roleJwt(COACH_SUBJECT, "COACH");
        var member = roleJwt(MEMBER_A_SUBJECT, "MEMBER");

        mockMvc.perform(get("/api/membership/me").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeProfileId").isNotEmpty());
        mockMvc.perform(get("/api/membership/admin/operations").with(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/coach/assigned-members").with(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/members/{id}/private", memberCId).with(admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/membership/me").with(coach)).andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeProfileId").isNotEmpty());
        mockMvc.perform(get("/api/membership/coach/assigned-members").with(coach))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/members/{id}/private", memberAId).with(coach))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/members/{id}/private", memberCId).with(coach))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
        mockMvc.perform(get("/api/membership/admin/operations").with(coach))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/membership/me").with(member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberProfileId").value(memberAId.toString()));
        mockMvc.perform(get("/api/membership/members/{id}/private", memberAId).with(member))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/members/{id}/private", memberBId).with(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/membership/coach/assigned-members").with(member))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/admin/operations").with(member))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyConfiguredFrontendOriginCanMakeCorsRequests() throws Exception {
        mockMvc.perform(options("/api/membership/me")
                        .header("Origin", "http://localhost:8088")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:8088"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Headers",
                                org.hamcrest.Matchers.containsString("Authorization")));

        mockMvc.perform(options("/api/membership/me")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void unknownMembershipResourcesUseUniformNotFoundJsonForAdministrators() throws Exception {
        mockMvc.perform(get("/api/membership/employees")
                        .with(roleJwt("gym-admin", "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void invalidMemberIdUsesUniformBadRequestJson() throws Exception {
        mockMvc.perform(get("/api/membership/members/not-a-uuid/private")
                        .with(roleJwt("gym-admin", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void moduleEndpointsEnforceTheModuleRoleBeforeBusinessRouting() throws Exception {
        mockMvc.perform(get("/api/nutrition/private")
                        .with(roleJwt(COACH_SUBJECT, "COACH")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/nutrition/private")
                        .with(roleJwt(MEMBER_A_SUBJECT, "MEMBER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void administrationAndCoachActionsRequireTheirApplicationRoles() throws Exception {
        mockMvc.perform(get("/api/membership/admin/operations")
                        .with(roleJwt("gym-admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mockMvc.perform(get("/api/membership/admin/operations")
                        .with(roleJwt(MEMBER_A_SUBJECT, "MEMBER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/membership/coach/assigned-members")
                        .with(roleJwt(COACH_SUBJECT, "COACH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/membership/coach/assigned-members")
                        .with(roleJwt(MEMBER_A_SUBJECT, "MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void devProvisioningCreatesMemberEmployeeAndCoachAssignment() throws Exception {
        mockMvc.perform(post("/api/dev/provision")
                        .header("X-Provisioning-Key", "test-provisioning-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "profiles": [
                                    {"subject":"provisioned-member","username":"member","displayName":"Provisioned Member","profileType":"MEMBER"},
                                    {"subject":"provisioned-coach","username":"coach","displayName":"Provisioned Coach","profileType":"EMPLOYEE"}
                                  ],
                                  "coachAssignments": [
                                    {"coachSubject":"provisioned-coach","memberSubject":"provisioned-member"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilesProcessed").value(2))
                .andExpect(jsonPath("$.assignmentsProcessed").value(1));

        org.assertj.core.api.Assertions.assertThat(memberRepository.findByKeycloakUserId("provisioned-member"))
                .isPresent();
        org.assertj.core.api.Assertions.assertThat(employeeRepository.findByKeycloakUserId("provisioned-coach"))
                .isPresent();
        org.assertj.core.api.Assertions.assertThat(assignmentRepository
                .existsByCoachSubjectAndMemberSubject("provisioned-coach", "provisioned-member"))
                .isTrue();
    }

    private static JwtRequestPostProcessor roleJwt(String subject, String... roles) {
        return roleJwtWithDisplayName(subject, null, roles);
    }

    private static JwtRequestPostProcessor roleJwtWithDisplayName(
            String subject,
            String displayName,
            String... roles) {
        List<String> realmRoles = List.of(roles);
        List<SimpleGrantedAuthority> authorities = realmRoles.stream()
                .filter(role -> List.of("ADMIN", "COACH", "MEMBER").contains(role))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return jwt().jwt(token -> {
            token.subject(subject)
                    .claim("realm_access", Map.of("roles", realmRoles));
            if (displayName != null) {
                token.claim("name", displayName);
            }
        }).authorities(authorities.toArray(SimpleGrantedAuthority[]::new));
    }
}
