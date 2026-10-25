package com.smartgym.membership.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.audit.AuditLogRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class EmployeeControllerTest {
    private final String coachSubject = UUID.randomUUID().toString();
    private final String otherCoachSubject = UUID.randomUUID().toString();
    private final String memberSubject = UUID.randomUUID().toString();
    private UUID coachId;
    private UUID otherCoachId;
    private UUID memberId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CoachAssignmentRepository assignmentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        auditLogRepository.deleteAll();
        memberRepository.deleteAll();
        employeeRepository.deleteAll();
        Employee coach = employee(coachSubject, "Coach One");
        Employee otherCoach = employee(otherCoachSubject, "Coach Two");
        coachId = coach.getId();
        otherCoachId = otherCoach.getId();
        employeeRepository.saveAll(List.of(coach, otherCoach));
        Member member = new Member(
                UUID.randomUUID(), memberSubject, memberSubject, "Member One",
                "Member", "One", "member@example.test", null, null);
        memberId = member.getId();
        memberRepository.save(member);
    }

    @Test
    void adminCanCreateListUpdateAndDeactivateEmployees() throws Exception {
        String newSubject = UUID.randomUUID().toString();
        String body = """
                {"keycloakUserId":"%s","firstName":"New","lastName":"Coach",
                 "email":"new.coach@example.test","jobTitle":"Coach","hireDate":"2024-01-02"}
                """.formatted(newSubject);
        String createResponse = mockMvc.perform(post("/api/membership/employees")
                        .with(roleJwt("admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.coach@example.test"))
                .andReturn().getResponse().getContentAsString();
        UUID employeeId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build().readTree(createResponse).get("id").asText());

        mockMvc.perform(get("/api/membership/employees?jobTitle=coach")
                        .with(roleJwt("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(put("/api/membership/employees/{id}", employeeId)
                        .with(roleJwt("admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Updated","lastName":"Coach",
                                 "email":"updated.coach@example.test","jobTitle":"Head Coach"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
        mockMvc.perform(post("/api/membership/employees/{id}/deactivate", employeeId)
                        .with(roleJwt("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void coachCanOnlyReadOwnProfileAndMembersButCannotManageEmployees() throws Exception {
        mockMvc.perform(get("/api/membership/employees/{id}", coachId)
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/employees/{id}", otherCoachId)
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/employees")
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/membership/employees")
                        .with(roleJwt(coachSubject, "COACH"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keycloakUserId":"%s","firstName":"New","lastName":"Coach",
                                 "email":"new.coach@example.test","jobTitle":"Coach"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/employees/{coachId}/members", coachId)
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membership/employees/{coachId}/members", otherCoachId)
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberAndAnonymousCannotReadEmployeeProfiles() throws Exception {
        mockMvc.perform(get("/api/membership/employees/{id}", coachId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/employees/{id}", coachId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanAssignAndUnassignWithoutDeletingHistory() throws Exception {
        mockMvc.perform(post("/api/membership/employees/{coachId}/members/{memberId}", coachId, memberId)
                        .with(roleJwt("admin", "ADMIN")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/membership/employees/{coachId}/members/{memberId}", coachId, memberId)
                        .with(roleJwt("admin", "ADMIN")))
                .andExpect(status().isConflict());
        mockMvc.perform(post(
                                "/api/membership/employees/{coachId}/members/{memberId}/unassign",
                                coachId,
                                memberId)
                        .with(roleJwt("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").isNotEmpty());
    }

    @Test
    void coachNotAssignedToMemberIsForbidden() throws Exception {
        mockMvc.perform(get("/api/membership/members/{id}", memberId)
                        .with(roleJwt(coachSubject, "COACH")))
                .andExpect(status().isForbidden());
    }

    private static Employee employee(String subject, String displayName) {
        return new Employee(UUID.randomUUID(), subject, subject, displayName);
    }

    private static JwtRequestPostProcessor roleJwt(String subject, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return jwt().jwt(token -> token.subject(subject).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities.toArray(SimpleGrantedAuthority[]::new));
    }
}
