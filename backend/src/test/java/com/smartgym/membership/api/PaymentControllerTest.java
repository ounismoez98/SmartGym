package com.smartgym.membership.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.payment.PaymentRepository;
import com.smartgym.membership.profile.EmployeeRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import com.smartgym.membership.profile.MemberSubscription;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class PaymentControllerTest {
    private final String memberSubject = UUID.randomUUID().toString();
    private final String otherMemberSubject = UUID.randomUUID().toString();
    private UUID memberId;
    private UUID otherMemberId;
    private UUID otherSubscriptionId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private MemberSubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        auditLogRepository.deleteAll();
        employeeRepository.deleteAll();
        memberId = UUID.randomUUID();
        otherMemberId = UUID.randomUUID();
        Member member = new Member(memberId, memberSubject, "member", "Payment Member");
        Member other = new Member(otherMemberId, otherMemberSubject, "other", "Other Member");
        memberRepository.saveAll(List.of(member, other));
        SubscriptionPlan plan = new SubscriptionPlan(
                UUID.randomUUID(), "PAY-" + UUID.randomUUID(), "Payment test", "Test", 30, 1000, "TND");
        planRepository.save(plan);
        otherSubscriptionId = UUID.randomUUID();
        subscriptionRepository.save(new MemberSubscription(
                otherSubscriptionId, otherMemberId, plan.getId(), java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(30L * 24 * 60 * 60)));
    }

    @Test
    void adminCreatesPaymentAndMemberCanReadOnlyTheirOwn() throws Exception {
        String response = createPayment(memberId, 2400);
        UUID paymentId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText());

        mockMvc.perform(get("/api/membership/payments").with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].currency").value("TND"));
        mockMvc.perform(get("/api/membership/payments/{id}", paymentId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(2400));
        mockMvc.perform(get("/api/membership/payments/{id}", paymentId)
                        .with(roleJwt(otherMemberSubject, "MEMBER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/payments").with(roleJwt("coach-sub", "COACH")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membership/payments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/membership/members/{id}/payments/total", memberId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaidCents").value(2400));
        mockMvc.perform(get("/api/membership/members/{id}/payments/total", otherMemberId)
                        .with(roleJwt(memberSubject, "MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctionIsTraceableAndTotalExcludesCorrectedOriginal() throws Exception {
        String response = createPayment(memberId, 2400);
        UUID paymentId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText());

        String correctionResponse = mockMvc.perform(post("/api/membership/payments/{id}/corrections", paymentId)
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":2600,"reason":"Corrected the recorded amount"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountCents").value(2600))
                .andExpect(jsonPath("$.correctsPaymentId").value(paymentId.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID correctionId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(correctionResponse).get("id").asText());

        mockMvc.perform(get("/api/membership/payments/{id}", paymentId)
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CORRECTED"))
                .andExpect(jsonPath("$.amountCents").value(2400))
                .andExpect(jsonPath("$.corrections[0].id").value(correctionId.toString()));
        mockMvc.perform(get("/api/membership/members/{id}/payments/total", memberId)
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaidCents").value(2600));
        mockMvc.perform(post("/api/membership/payments/{id}/corrections", paymentId)
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":2500,"reason":"Second correction"}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/membership/payments")
                        .with(roleJwt(memberSubject, "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","amountCents":1000,"method":"CASH"}
                                """.formatted(memberId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/membership/payments/{id}", correctionId)
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/membership/payments/{id}", correctionId)
                        .with(roleJwt("admin-sub", "ADMIN")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void adminCannotAttachAnotherMembersSubscription() throws Exception {
        mockMvc.perform(post("/api/membership/payments")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","subscriptionId":"%s","amountCents":1500,"method":"TRANSFER"}
                                """.formatted(memberId, otherSubscriptionId)))
                .andExpect(status().isConflict());
    }

    private String createPayment(UUID ownerId, long amountCents) throws Exception {
        return mockMvc.perform(post("/api/membership/payments")
                        .with(roleJwt("admin-sub", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","amountCents":%d,"method":"CARD"}
                                """.formatted(ownerId, amountCents)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("TND"))
                .andReturn().getResponse().getContentAsString();
    }

    private static JwtRequestPostProcessor roleJwt(String subject, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return jwt().jwt(token -> token.subject(subject).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities.toArray(SimpleGrantedAuthority[]::new));
    }
}
