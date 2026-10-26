package com.smartgym.membership.feedback;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.membership.feedback.dto.FeedbackSubmitRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class FeedbackControllerTest {

    private static final String MEMBER_A_SUBJECT = "kc-member-a";
    private static final String MEMBER_B_SUBJECT = "kc-member-b";
    private static final String ADMIN_SUBJECT = "kc-gym-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        feedbackRepository.deleteAll();
    }

    @Test
    void unauthenticatedAccessReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/feedback/my"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCanSubmitFeedbackAndReceiveAiAnalysis() throws Exception {
        FeedbackSubmitRequest request = new FeedbackSubmitRequest(
                5,
                FeedbackCategory.EQUIPMENT,
                "The new power racks and barbells are outstanding!"
        );

        mockMvc.perform(post("/api/feedback")
                        .with(jwt()
                                .jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                        .claim("name", "Member A")
                                        .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value(MEMBER_A_SUBJECT))
                .andExpect(jsonPath("$.memberName").value("Member A"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.category").value("EQUIPMENT"))
                .andExpect(jsonPath("$.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.themes").isArray())
                .andExpect(jsonPath("$.themes", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));

        List<Feedback> saved = feedbackRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSubject()).isEqualTo(MEMBER_A_SUBJECT);
        assertThat(saved.get(0).getRating()).isEqualTo(5);
        assertThat(saved.get(0).getSentiment()).isEqualTo(FeedbackSentiment.POSITIVE);
    }

    @Test
    void validationErrorsOnInvalidInput() throws Exception {
        // Rating out of bounds (6)
        FeedbackSubmitRequest invalidRating = new FeedbackSubmitRequest(
                6,
                FeedbackCategory.CLEANLINESS,
                "Clean floors"
        );

        mockMvc.perform(post("/api/feedback")
                        .with(jwt()
                                .jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRating)))
                .andExpect(status().isBadRequest());

        // Blank comment
        FeedbackSubmitRequest blankComment = new FeedbackSubmitRequest(
                4,
                FeedbackCategory.STAFF,
                "   "
        );

        mockMvc.perform(post("/api/feedback")
                        .with(jwt()
                                .jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankComment)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberCanOnlyViewTheirOwnFeedback() throws Exception {
        // Seed Member A feedback
        feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_A_SUBJECT, "Member A", 4,
                FeedbackCategory.CLEANLINESS, "Very clean locker rooms",
                FeedbackSentiment.POSITIVE, "Clean locker rooms",
                List.of("Cleanliness Standards"), null));

        // Seed Member B feedback
        feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_B_SUBJECT, "Member B", 2,
                FeedbackCategory.EQUIPMENT, "Cable machine broken for days",
                FeedbackSentiment.NEGATIVE, "Cable machine broken",
                List.of("Gym Equipment"), null));

        // Member A calls /my
        mockMvc.perform(get("/api/feedback/my")
                        .with(jwt()
                                .jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subject").value(MEMBER_A_SUBJECT))
                .andExpect(jsonPath("$[0].category").value("CLEANLINESS"));

        // Member B calls /my
        mockMvc.perform(get("/api/feedback/my")
                        .with(jwt()
                                .jwt(token -> token.subject(MEMBER_B_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subject").value(MEMBER_B_SUBJECT))
                .andExpect(jsonPath("$[0].category").value("EQUIPMENT"));
    }

    @Test
    void memberCannotAccessAdminFeedbackEndpoints() throws Exception {
        mockMvc.perform(get("/api/feedback/admin/all")
                        .with(jwt()
                                .jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/feedback/admin/stats")
                        .with(jwt()
                                .jwt(token -> token.subject(MEMBER_A_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanViewAllFeedbackAndStats() throws Exception {
        feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_A_SUBJECT, "Member A", 5,
                FeedbackCategory.CLEANLINESS, "Spotless facility!",
                FeedbackSentiment.POSITIVE, "Spotless facility",
                List.of("Cleanliness Standards", "Hygiene"), null));

        feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_B_SUBJECT, "Member B", 2,
                FeedbackCategory.STAFF, "Front desk was unattended",
                FeedbackSentiment.NEGATIVE, "Front desk unattended",
                List.of("Staff & Coaching"), null));

        // Admin views all
        mockMvc.perform(get("/api/feedback/admin/all")
                        .with(jwt()
                                .jwt(token -> token.subject(ADMIN_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Admin checks stats
        mockMvc.perform(get("/api/feedback/admin/stats")
                        .with(jwt()
                                .jwt(token -> token.subject(ADMIN_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFeedbacks").value(2))
                .andExpect(jsonPath("$.averageRating").value(3.5))
                .andExpect(jsonPath("$.positiveCount").value(1))
                .andExpect(jsonPath("$.negativeCount").value(1))
                .andExpect(jsonPath("$.ratingDistribution.5").value(1))
                .andExpect(jsonPath("$.ratingDistribution.2").value(1))
                .andExpect(jsonPath("$.categoryBreakdown", hasSize(5)))
                .andExpect(jsonPath("$.commonThemes", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    void adminCanFilterFeedback() throws Exception {
        feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_A_SUBJECT, "Member A", 5,
                FeedbackCategory.EQUIPMENT, "Treadmills are top notch",
                FeedbackSentiment.POSITIVE, "Treadmills top notch",
                List.of("Gym Equipment"), null));

        feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_B_SUBJECT, "Member B", 1,
                FeedbackCategory.EQUIPMENT, "Sauna is too cold",
                FeedbackSentiment.NEGATIVE, "Sauna too cold",
                List.of("Facility"), null));

        // Filter by category and sentiment
        mockMvc.perform(get("/api/feedback/admin/all")
                        .param("category", "EQUIPMENT")
                        .param("sentiment", "POSITIVE")
                        .with(jwt()
                                .jwt(token -> token.subject(ADMIN_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].sentiment").value("POSITIVE"));
    }

    @Test
    void adminCanDeleteFeedback() throws Exception {
        Feedback feedback = feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_A_SUBJECT, "Member A", 4,
                FeedbackCategory.SERVICE, "Good overall",
                FeedbackSentiment.POSITIVE, "Good overall",
                List.of("Customer Care"), null));

        mockMvc.perform(delete("/api/feedback/admin/{id}", feedback.getId())
                        .with(jwt()
                                .jwt(token -> token.subject(ADMIN_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        assertThat(feedbackRepository.findById(feedback.getId())).isEmpty();
    }

    @Test
    void adminCanFetchExecutiveReport() throws Exception {
        feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_A_SUBJECT, "Member A", 5,
                FeedbackCategory.CLEANLINESS, "Super clean and pristine facility",
                FeedbackSentiment.POSITIVE, "Clean facility",
                List.of("Cleanliness Standards"), null));

        mockMvc.perform(get("/api/feedback/admin/executive-report")
                        .with(jwt()
                                .jwt(token -> token.subject(ADMIN_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthScore").isNumber())
                .andExpect(jsonPath("$.overallAssessment").isNotEmpty())
                .andExpect(jsonPath("$.keyStrengths").isArray())
                .andExpect(jsonPath("$.strategicRecommendations").isArray());
    }

    @Test
    void adminCanReanalyzeAllFeedbacks() throws Exception {
        feedbackRepository.save(new Feedback(
                UUID.randomUUID(), MEMBER_A_SUBJECT, "Member A", 1,
                FeedbackCategory.EQUIPMENT, "The cable pulley is snapped and broken, dangerous!",
                FeedbackSentiment.NEGATIVE, "Cable broken",
                List.of("Gym Equipment"), null));

        mockMvc.perform(post("/api/feedback/admin/reanalyze-all")
                        .with(jwt()
                                .jwt(token -> token.subject(ADMIN_SUBJECT)
                                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].urgency").value("CRITICAL"))
                .andExpect(jsonPath("$[0].actionRecommendation").isNotEmpty());
    }
}
