package com.smartgym.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.membership.MembershipApiApplication;
import com.smartgym.membership.profile.BusinessProfile;
import com.smartgym.membership.profile.BusinessProfileRepository;
import com.smartgym.membership.profile.CoachAssignment;
import com.smartgym.membership.profile.CoachAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MembershipApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NutritionGenerationContextTest {
    private static final String MEMBER_SUBJECT = "generation-context-member";
    private static final String OTHER_MEMBER_SUBJECT = "generation-context-other-member";
    private static final String COACH_SUBJECT = "generation-context-coach";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NutritionProfileRepository nutritionProfileRepository;

    @Autowired
    private MealPlanRepository mealPlanRepository;

    @Autowired
    private NutritionPlanRepository nutritionPlanRepository;

    @Autowired
    private MealPlanGenerationContextRepository generationContextRepository;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private FoodJournalRepository foodJournalRepository;

    @Autowired
    private BusinessProfileRepository profileRepository;

    @Autowired
    private CoachAssignmentRepository assignmentRepository;

    @MockBean
    private LocalLlmMealPlanAssistant mealPlanAssistant;

    @BeforeEach
    void setUp() {
        reset(mealPlanAssistant);
        nutritionPlanRepository.deleteAll();
        mealPlanRepository.deleteAll();
        generationContextRepository.deleteAll();
        foodJournalRepository.deleteAll();
        nutritionProfileRepository.deleteAll();
        foodRepository.deleteAll();
        assignmentRepository.deleteAll();
        profileRepository.deleteAll();

        BusinessProfile member = new BusinessProfile(MEMBER_SUBJECT, "generation-member", "Generation Member");
        member.ensureMemberProfile();
        BusinessProfile otherMember = new BusinessProfile(
                OTHER_MEMBER_SUBJECT, "generation-other-member", "Other Member");
        otherMember.ensureMemberProfile();
        BusinessProfile coach = new BusinessProfile(COACH_SUBJECT, "generation-coach", "Generation Coach");
        coach.ensureEmployeeProfile();
        profileRepository.saveAll(List.of(member, otherMember, coach));
        assignmentRepository.save(new CoachAssignment(COACH_SUBJECT, MEMBER_SUBJECT));
        nutritionProfileRepository.save(new NutritionProfile(MEMBER_SUBJECT, "Member profile",
                "maintenance", "balanced", "moderate", 30, 170.0, 70.0, 2100));

        NutritionService.GeneratedMeal meal = new NutritionService.GeneratedMeal(
                "Oat breakfast",
                MealPlan.MealType.BREAKFAST,
                350,
                List.of(new NutritionService.GeneratedFood("Oats", 50.0, null)),
                "Cook the oats with milk and serve with fruit.",
                new NutritionService.NutritionEstimate(340.0, 12.0, 52.0, 8.0, 6.0, 120.0),
                "MODEL_ESTIMATE");
        NutritionService.GeneratedMealPlan generatedPlan = new NutritionService.GeneratedMealPlan(
                "Member plan",
                "maintenance",
                List.of(meal),
                new NutritionService.NutritionEstimate(340.0, 12.0, 52.0, 8.0, 6.0, 120.0),
                List.of("Oats", "Milk", "Fruit"),
                List.of());
        when(mealPlanAssistant.generateAsync(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new NutritionService.MealPlanSuggestion(
                        true, "AI_GENERATED", generatedPlan, "Review estimates; no safety guarantee.")));
    }

    @Test
    void generationContextIsBoundToMemberAndRestrictionsThroughSaveAndCoachEdit() throws Exception {
        MvcResult generation = mockMvc.perform(post("/api/nutrition/assistant/plan")
                        .with(member(MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"restrictions":[" peanuts "]}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        String generationResponse = mockMvc.perform(asyncDispatch(generation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.generationContextId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String contextId = objectMapper.readTree(generationResponse)
                .get("generationContextId").asText();
        MealPlanGenerationContext context = generationContextRepository.findById(
                java.util.UUID.fromString(contextId)).orElseThrow();
        assertEquals(Set.of("peanuts", "pork", "bacon", "ham", "lard", "pancetta",
                "prosciutto", "pork sausage", "pork gelatin"), context.getRestrictions());
        assertEquals(MEMBER_SUBJECT, context.getSubject());

        String saveWithoutRestrictions = savePayload(contextId, null, "Rolled oats");
        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member(OTHER_MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveWithoutRestrictions))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member(MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(savePayload(null, null, "Rolled oats")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member(MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(savePayload("not-a-uuid", null, "Rolled oats")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member(MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(savePayload(contextId, "[\"shellfish\"]", "Rolled oats")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("original generation context")));
        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member(MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(savePayload(contextId, "[]", "Rolled oats")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("original generation context")));
        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member(MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(savePayload(contextId, null, "Peanut butter")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("excluded")));

        String savedResponse = mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member(MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveWithoutRestrictions))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Oat breakfast"))
                .andExpect(jsonPath("$[0].description").value(
                        org.hamcrest.Matchers.containsString("Cook the oats")))
                .andReturn().getResponse().getContentAsString();
        JsonNode savedMeal = objectMapper.readTree(savedResponse).get(0);
        long mealPlanId = savedMeal.get("id").asLong();
        long foodId = savedMeal.get("foods").get(0).get("foodId").asLong();
        MealPlan savedPlan = mealPlanRepository.findByIdAndSubject(mealPlanId, MEMBER_SUBJECT).orElseThrow();
        assertNotNull(savedPlan.getGenerationContext());
        assertEquals(Set.of("peanuts", "pork", "bacon", "ham", "lard", "pancetta",
                "prosciutto", "pork sausage", "pork gelatin"),
                generationContextRepository.findById(UUID.fromString(contextId)).orElseThrow().getRestrictions());

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member(MEMBER_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveWithoutRestrictions))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been used")));

        String restrictedCoachEdit = mealPlanUpdatePayload(
                "Oat breakfast", "Peanut butter oats.", foodId);
        mockMvc.perform(put("/api/nutrition/coach/members/{memberId}/meal-plans/{mealPlanId}",
                        profileRepository.findById(MEMBER_SUBJECT).orElseThrow().getMemberProfileId(), mealPlanId)
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restrictedCoachEdit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("restriction")));

        mockMvc.perform(put("/api/nutrition/coach/members/{memberId}/meal-plans/{mealPlanId}",
                        profileRepository.findById(MEMBER_SUBJECT).orElseThrow().getMemberProfileId(), mealPlanId)
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealPlanUpdatePayload(
                                "Reviewed oat breakfast", "Cook gently and serve warm.", foodId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reviewed oat breakfast"));
        assertEquals(Set.of("peanuts", "pork", "bacon", "ham", "lard", "pancetta",
                "prosciutto", "pork sausage", "pork gelatin"),
                generationContextRepository.findById(UUID.fromString(contextId)).orElseThrow().getRestrictions());
    }

    private String savePayload(String contextId, String restrictionsJson, String ingredient) {
        String contextField = contextId == null ? "" : "\"generationContextId\":\"" + contextId + "\",";
        String restrictionField = restrictionsJson == null ? "" : "\"restrictions\":" + restrictionsJson + ",";
        return """
                {
                  %s
                  %s
                  "planName":"Member plan",
                  "goal":"maintenance",
                  "meals":[{
                    "name":"Oat breakfast",
                    "mealType":"BREAKFAST",
                    "servingSizeGrams":350,
                    "preparationInstructions":"Cook the oats with milk and serve with fruit.",
                    "ingredients":[{"name":"%s","servings":50}],
                    "estimatedNutrition":{
                      "calories":340,
                      "proteinGrams":12,
                      "carbohydrateGrams":52,
                      "fatGrams":8,
                      "fiberGrams":6,
                      "sodiumMg":120
                    }
                  }]
                }
                """.formatted(contextField, restrictionField, ingredient);
    }

    private String mealPlanUpdatePayload(String name, String description, long foodId) {
        return """
                {
                  "name":"%s",
                  "description":"%s",
                  "mealType":"BREAKFAST",
                  "targetCalories":340,
                  "foods":[{"foodId":%d,"servings":1}]
                }
                """.formatted(name, description, foodId);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor member(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor coach() {
        return jwt().jwt(token -> token.subject(COACH_SUBJECT)
                        .claim("realm_access", Map.of("roles", List.of("COACH"))))
                .authorities(new SimpleGrantedAuthority("ROLE_COACH"));
    }
}
