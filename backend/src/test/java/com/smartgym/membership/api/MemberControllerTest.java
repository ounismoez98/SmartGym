package com.smartgym.membership.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.profile.CoachAssignment;
import com.smartgym.membership.profile.CoachAssignmentRepository;
import com.smartgym.membership.profile.Employee;
import com.smartgym.membership.profile.EmployeeRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import com.smartgym.membership.profile.MemberSubscription;
import com.smartgym.membership.profile.MemberSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class MemberControllerTest {
    private final String memberSubject = UUID.randomUUID().toString();
    private final String otherMemberSubject = UUID.randomUUID().toString();
    private final String coachSubject = UUID.randomUUID().toString();
    private UUID memberId;
    private UUID otherMemberId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CoachAssignmentRepository assignmentRepository;

    @Autowired
    private MemberSubscriptionRepository subscriptionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        auditLogRepository.deleteAll();
        memberRepository.deleteAll();
        employeeRepository.deleteAll();
        Member member = new Member(
                UUID.randomUUID(), memberSubject, memberSubject, "Member One",
                "Member", "One", "member.one@example.test", "111", null);
        Member otherMember = new Member(
                UUID.randomUUID(), otherMemberSubject, otherMemberSubject, "Member Two",
                "Member", "Two", "member.two@example.test", "222", null);
        memberId = member.getId();
        otherMemberId = otherMember.getId();
        memberRepository.saveAll(List.of(member, otherMember));
        employeeRepository.save(new Employee(UUID.randomUUID(), coachSubject, coachSubject, "Coach"));
        assignmentRepository.save(new CoachAssignment(coachSubject, memberSubject));
    }

    @Test
    void createIsAdminOnlyValidatesUuidAndWritesAudit() throws Exception {
        String keycloakUserId = UUID.randomUUID().toString();
        String body = """
                {"keycloakUserId":"%s","firstName":"New","lastName":"Member",
                 "email":"new.member@example.test","phone":"123","birthDate":"1990-01-02"}
                """.formatted(keycloakUserId);

        mockMvc.perform(post("/api/membership/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/membership/members")
                        .with(roleJwt(memberSubject, "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/membership/members")
                        .with(roleJwt(coachSubject, "COACH"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/membership/members")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keycloakUserId":"not-a-uuid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isMap());

        mockMvc.perform(post("/api/membership/members")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keycloakUserId":"%s","firstName":"New","lastName":"Member"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/membership/members")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keycloakUserId").value(keycloakUserId))
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/membership/members")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isMap());
        mockMvc.perform(post("/api/membership/members")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keycloakUserId":"%s","firstName":"New","lastName":"Member",
                                 "email":"new.member@example.test"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        org.assertj.core.api.Assertions.assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> {
                    org.assertj.core.api.Assertions.assertThat(audit.getActorUserId()).isEqualTo("admin-sub");
                    org.assertj.core.api.Assertions.assertThat(audit.getAction()).isEqualTo("MEMBER_CREATED");
                });
    }

    @Test
    void listIsPagedAndCoachOnlySeesAssignedMembers() throws Exception {
        mockMvc.perform(get("/api/membership/members?page=0&size=1&status=ACTIVE")
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/membership/members?query=One&status=ACTIVE")
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(memberId.toString()));

        mockMvc.perform(get("/api/membership/members")
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(memberId.toString()));

        mockMvc.perform(get("/api/membership/members")
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHonorsAdministratorCoachAndMemberOwnership() throws Exception {
        mockMvc.perform(get("/api/membership/members/{id}", memberId)
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/members/{id}", memberId)
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/members/{id}", memberId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/members/{id}", memberId)
                        .with(roleJwt(memberSubject.toUpperCase(java.util.Locale.ROOT), "MEMBER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/members/{id}", otherMemberId)
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/members/{id}", otherMemberId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/membership/members/{id}", UUID.randomUUID())
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/membership/members/{id}", memberId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberMayUpdateOnlyOwnContactAndAdminMayUpdateProfile() throws Exception {
        mockMvc.perform(put("/api/membership/members/{id}", memberId)
                        .with(roleJwt(memberSubject, "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"updated@example.test","phone":"999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.test"))
                .andExpect(jsonPath("$.phone").value("999"));

        mockMvc.perform(put("/api/membership/members/{id}", memberId)
                        .with(roleJwt(memberSubject, "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(put("/api/membership/members/{id}", memberId)
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(put("/api/membership/members/{id}", otherMemberId)
                        .with(roleJwt(memberSubject, "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"000"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/membership/members/{id}", memberId)
                        .with(roleJwt(coachSubject, "COACH"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"333"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/membership/members/{id}", memberId)
                        .with(roleJwt(memberSubject, "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Not allowed"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/membership/members/{id}", memberId)
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Admin Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Admin Updated"));

        org.assertj.core.api.Assertions.assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> org.assertj.core.api.Assertions.assertThat(audit.getAction())
                        .isEqualTo("MEMBER_UPDATED"));
    }

    @Test
    void deactivateIsAdminOnlyAndConflictsWithActiveSubscription() throws Exception {
        subscriptionRepository.save(new MemberSubscription(
                UUID.randomUUID(),
                memberId,
                UUID.randomUUID(),
                java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(3600)));
        mockMvc.perform(post("/api/membership/members/{id}/deactivate", memberId)
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        org.assertj.core.api.Assertions.assertThat(memberRepository.findById(memberId).orElseThrow().getStatus())
                .isEqualTo(Member.Status.ACTIVE);

        subscriptionRepository.deleteAll();
        mockMvc.perform(post("/api/membership/members/{id}/deactivate", memberId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/membership/members/{id}/deactivate", memberId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/membership/members/{id}/deactivate", memberId)
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        org.assertj.core.api.Assertions.assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> org.assertj.core.api.Assertions.assertThat(audit.getAction())
                        .isEqualTo("MEMBER_DEACTIVATED"));
    }

    private static JwtRequestPostProcessor roleJwt(String subject, String role) {
        return jwt()
                .jwt(token -> token.subject(subject).claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
