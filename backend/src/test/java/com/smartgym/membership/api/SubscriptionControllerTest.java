package com.smartgym.membership.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.profile.EmployeeRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import com.smartgym.membership.profile.MemberSubscriptionRepository;
import com.smartgym.membership.profile.SubscriptionPlan;
import com.smartgym.membership.profile.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class SubscriptionControllerTest {
    private final String memberSubject = UUID.randomUUID().toString();
    private final String otherMemberSubject = UUID.randomUUID().toString();
    private UUID memberId;
    private UUID otherMemberId;
    private UUID planId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private MemberSubscriptionRepository subscriptionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        auditLogRepository.deleteAll();
        employeeRepository.deleteAll();
        Member member = member(memberSubject, "Plan Member");
        Member other = member(otherMemberSubject, "Other Member");
        memberId = member.getId();
        otherMemberId = other.getId();
        memberRepository.saveAll(List.of(member, other));
        SubscriptionPlan plan = new SubscriptionPlan(
                UUID.randomUUID(), "TEST-" + UUID.randomUUID(), "Test Plan", "Test conditions", 30, 2500, "EUR");
        planId = plan.getId();
        planRepository.save(plan);
    }

    @Test
    void adminCanCreateAndModifyPlanAndSubscriptionThenCancel() throws Exception {
        String planResponse = mockMvc.perform(post("/api/membership/plans")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NEW-%s","name":"New Plan","conditions":"Full access",
                                 "durationDays":28,"priceCents":4200,"currency":"EUR"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceCents").value(4200))
                .andReturn().getResponse().getContentAsString();
        UUID createdPlanId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(planResponse).get("id").asText().isBlank()
                ? null
                : UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(planResponse).get("id").asText());

        mockMvc.perform(get("/api/membership/plans?active=true")
                        .with(roleJwt("member-sub", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        mockMvc.perform(put("/api/membership/plans/{id}", createdPlanId)
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Plan","conditions":"Updated conditions",
                                 "durationDays":35,"priceCents":5000,"currency":"eur","active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"));
        String subscriptionResponse = mockMvc.perform(post("/api/membership/subscriptions")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","planId":"%s"}
                                """.formatted(memberId, planId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        UUID subscriptionId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(subscriptionResponse).get("id").asText());

        mockMvc.perform(put("/api/membership/subscriptions/{id}", subscriptionId)
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":"%s"}
                                """.formatted(createdPlanId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(createdPlanId.toString()));
        mockMvc.perform(post("/api/membership/subscriptions/{id}/cancel", subscriptionId)
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Member requested cancellation"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Member requested cancellation"));
        mockMvc.perform(delete("/api/membership/plans/{id}", createdPlanId)
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void memberReadsOnlyOwnSubscriptionsAndCoachIsDenied() throws Exception {
        String response = mockMvc.perform(post("/api/membership/subscriptions")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","planId":"%s"}
                                """.formatted(memberId, planId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID subscriptionId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText());

        mockMvc.perform(get("/api/membership/subscriptions")
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].memberId").value(memberId.toString()));
        mockMvc.perform(get("/api/membership/subscriptions?memberId={id}", otherMemberId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/membership/subscriptions/{id}", subscriptionId)
                        .with(roleJwt(otherMemberSubject, "MEMBER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/subscriptions/{id}", subscriptionId)
                        .with(roleJwt("coach-sub", "COACH")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/subscriptions/{id}", subscriptionId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/membership/subscriptions/{id}", subscriptionId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isOk());
    }

    @Test
    void secondActiveSubscriptionAndInvalidCancellationAreRejected() throws Exception {
        mockMvc.perform(post("/api/membership/subscriptions")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","planId":"%s"}
                                """.formatted(memberId, planId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/membership/subscriptions")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","planId":"%s"}
                                """.formatted(memberId, planId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(post("/api/membership/subscriptions/{id}/cancel", UUID.randomUUID())
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":" "}
                                """))
                .andExpect(status().isBadRequest());
    }

    private static Member member(String subject, String name) {
        return new Member(
                UUID.randomUUID(), subject, subject, name, name.split(" ")[0],
                name.split(" ")[1], subject + "@example.test", null, null);
    }

    private static JwtRequestPostProcessor roleJwt(String subject, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return jwt().jwt(token -> token.subject(subject).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities.toArray(SimpleGrantedAuthority[]::new));
    }
}
