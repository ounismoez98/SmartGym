package com.smartgym.membership.feedback;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.membership.feedback.dto.FeedbackSubmitRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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

/**
 * Black-Box Test Suite for the Feedback Module.
 *
 * Verifies system functionality strictly through its public HTTP API contract,
 * testing inputs and expected outputs without relying on internal implementation details:
 * 1. Boundary Value Analysis (BVA)
 * 2. Equivalence Class Partitioning (EP)
 * 3. Role-Based Access Control (RBAC) & Authentication
 * 4. State Transitions & Lifecycle Workflows
 * 5. Robustness & Malformed Input Handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class FeedbackBlackBoxTest {

    private static final String MEMBER_SUBJECT = "blackbox-member-1";
    private static final String OTHER_MEMBER_SUBJECT = "blackbox-member-2";
    private static final String ADMIN_SUBJECT = "blackbox-admin";

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

    // =========================================================================
    // 1. BOUNDARY VALUE ANALYSIS (BVA)
    // =========================================================================
    @Nested
    @DisplayName("1. Boundary Value Analysis (BVA)")
    class BoundaryValueAnalysis {

        @ParameterizedTest(name = "Valid Rating Boundary: {0}")
        @ValueSource(ints = {1, 5})
        void validRatingBoundariesAreAccepted(int rating) throws Exception {
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(
                    rating,
                    FeedbackCategory.EQUIPMENT,
                    "Valid boundary rating comment"
            );

            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("name", "Boundary User")
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rating").value(rating));
        }

        @ParameterizedTest(name = "Invalid Rating Boundary: {0}")
        @ValueSource(ints = {-1, 0, 6, 10})
        void invalidRatingBoundariesAreRejected(int invalidRating) throws Exception {
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(
                    invalidRating,
                    FeedbackCategory.EQUIPMENT,
                    "Testing out-of-bound rating"
            );

            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Comment boundary: exactly 1 character is accepted")
        void commentMinimumLengthBoundaryAccepted() throws Exception {
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(
                    3,
                    FeedbackCategory.SERVICE,
                    "A"
            );

            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.comment").value("A"));
        }

        @Test
        @DisplayName("Comment boundary: exactly 4000 characters is accepted")
        void commentMaximumLengthBoundaryAccepted() throws Exception {
            String maxComment = "x".repeat(4000);
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(
                    4,
                    FeedbackCategory.CLEANLINESS,
                    maxComment
            );

            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Comment boundary: 4001 characters exceeds max length and is rejected")
        void commentExceedingMaxLengthRejected() throws Exception {
            String overlimitComment = "x".repeat(4001);
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(
                    4,
                    FeedbackCategory.CLEANLINESS,
                    overlimitComment
            );

            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // 2. EQUIVALENCE PARTITIONING (EP)
    // =========================================================================
    @Nested
    @DisplayName("2. Equivalence Class Partitioning (EP)")
    class EquivalencePartitioning {

        @ParameterizedTest(name = "Valid Category Partition: {0}")
        @EnumSource(FeedbackCategory.class)
        void allValidCategoriesAreAccepted(FeedbackCategory category) throws Exception {
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(
                    4,
                    category,
                    "Testing category equivalence partition for " + category
            );

            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.category").value(category.name()));
        }

        @Test
        @DisplayName("Invalid Category Partition: non-existent category rejected")
        void invalidCategoryPartitionRejected() throws Exception {
            String malformedJson = """
                    {
                        "rating": 5,
                        "category": "INVALID_UNKNOWN_CATEGORY",
                        "comment": "Nice gym"
                    }
                    """;

            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "Invalid Comment Partition: blank or whitespace ''{0}''")
        @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
        void blankCommentPartitionRejected(String blankComment) throws Exception {
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(
                    3,
                    FeedbackCategory.CLEANLINESS,
                    blankComment
            );

            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // 3. ROLE-BASED ACCESS CONTROL (RBAC) & SECURITY MATRIX
    // =========================================================================
    @Nested
    @DisplayName("3. Role-Based Access Control (RBAC) & Authentication")
    class RoleBasedAccessControl {

        @Test
        @DisplayName("Unauthenticated requests to public-protected routes are 401 Unauthorized")
        void unauthenticatedAccessDenied() throws Exception {
            mockMvc.perform(post("/api/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/feedback/my"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/feedback/admin/all"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/feedback/admin/stats"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Member role is forbidden (403) from accessing admin endpoints")
        void memberForbiddenFromAdminEndpoints() throws Exception {
            UUID testId = UUID.randomUUID();

            mockMvc.perform(get("/api/feedback/admin/all")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/feedback/admin/stats")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/feedback/admin/executive-report")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/feedback/admin/" + testId)
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Data isolation: Member only receives own submitted feedback")
        void memberDataIsolationEnforced() throws Exception {
            // Member 1 creates feedback
            FeedbackSubmitRequest req1 = new FeedbackSubmitRequest(5, FeedbackCategory.EQUIPMENT, "Member 1 equipment praise");
            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated());

            // Member 2 creates feedback
            FeedbackSubmitRequest req2 = new FeedbackSubmitRequest(2, FeedbackCategory.STAFF, "Member 2 staff feedback");
            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(OTHER_MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated());

            // Verify Member 1 only gets 1 feedback belonging to MEMBER_SUBJECT
            mockMvc.perform(get("/api/feedback/my")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].subject").value(MEMBER_SUBJECT))
                    .andExpect(jsonPath("$[0].category").value("EQUIPMENT"));
        }
    }

    // =========================================================================
    // 4. STATE TRANSITION & LIFECYCLE WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("4. State Transition & Lifecycle Workflow")
    class LifecycleAndStateTransitions {

        @Test
        @DisplayName("Full Lifecycle: Submit -> Query My -> Admin Search -> Delete -> 404 on Subsequent Delete")
        void fullFeedbackLifecycleWorkflow() throws Exception {
            // Step 1: Member submits feedback
            FeedbackSubmitRequest submitRequest = new FeedbackSubmitRequest(
                    5,
                    FeedbackCategory.CLEANLINESS,
                    "Exceptional hygiene in the locker rooms"
            );

            String responseJson = mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("name", "Lifecycle User")
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(submitRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            String feedbackId = objectMapper.readTree(responseJson).get("id").asText();

            // Step 2: Member checks /my and verifies it appears
            mockMvc.perform(get("/api/feedback/my")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(feedbackId));

            // Step 3: Admin searches with query parameter and finds it
            mockMvc.perform(get("/api/feedback/admin/all")
                            .param("search", "hygiene")
                            .with(jwt()
                                    .jwt(t -> t.subject(ADMIN_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(feedbackId));

            // Step 4: Admin deletes the feedback
            mockMvc.perform(delete("/api/feedback/admin/" + feedbackId)
                            .with(jwt()
                                    .jwt(t -> t.subject(ADMIN_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isNoContent());

            // Step 5: Member checks /my -> now empty
            mockMvc.perform(get("/api/feedback/my")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            // Step 6: Admin attempts to delete again -> 404 Not Found
            mockMvc.perform(delete("/api/feedback/admin/" + feedbackId)
                            .with(jwt()
                                    .jwt(t -> t.subject(ADMIN_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // 5. ROBUSTNESS & MALFORMED INPUT
    // =========================================================================
    @Nested
    @DisplayName("5. Robustness & Malformed Input Handling")
    class RobustnessAndMalformedInput {

        @Test
        @DisplayName("Empty JSON body '{}' yields 400 Bad Request")
        void emptyJsonBodyReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Unsupported Media Type yields 415")
        void unsupportedMediaTypeReturns415() throws Exception {
            mockMvc.perform(post("/api/feedback")
                            .with(jwt()
                                    .jwt(t -> t.subject(MEMBER_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("MEMBER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("Plain text feedback"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("Non-UUID path variable returns 400 Bad Request")
        void invalidUuidPathReturnsBadRequest() throws Exception {
            mockMvc.perform(delete("/api/feedback/admin/not-a-valid-uuid")
                            .with(jwt()
                                    .jwt(t -> t.subject(ADMIN_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Reanalyzing non-existent UUID returns 404 Not Found")
        void reanalyzeNonExistentUuidReturnsNotFound() throws Exception {
            UUID randomUuid = UUID.randomUUID();

            mockMvc.perform(post("/api/feedback/admin/" + randomUuid + "/reanalyze")
                            .with(jwt()
                                    .jwt(t -> t.subject(ADMIN_SUBJECT)
                                            .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isNotFound());
        }
    }
}
