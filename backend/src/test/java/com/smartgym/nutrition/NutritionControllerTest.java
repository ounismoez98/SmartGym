package com.smartgym.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.membership.MembershipApiApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MembershipApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NutritionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private MealPlanRepository mealPlanRepository;

    @Autowired
    private NutritionPlanRepository nutritionPlanRepository;

    @Autowired
    private FoodJournalRepository foodJournalRepository;

    @Autowired
    private BodyMeasurementRepository bodyMeasurementRepository;

    @Autowired
    private NutritionProfileRepository nutritionProfileRepository;

    @Autowired
    private MealPlanGenerationContextRepository generationContextRepository;

    @Autowired
    private HydrationLogRepository hydrationLogRepository;

    @Autowired
    @Qualifier("llmExecutor")
    private ThreadPoolTaskExecutor llmExecutor;

    @BeforeEach
    void clearNutritionData() {
        nutritionPlanRepository.deleteAll();
        mealPlanRepository.deleteAll();
        foodJournalRepository.deleteAll();
        bodyMeasurementRepository.deleteAll();
        nutritionProfileRepository.deleteAll();
        hydrationLogRepository.deleteAll();
        foodRepository.deleteAll();
        generationContextRepository.deleteAll();
    }

    @Test
    void memberCanCreateListUpdateAndDeleteFoods() throws Exception {
        long foodId = createFood("member-123", "Chicken Breast", 250, 45);

        mockMvc.perform(get("/api/nutrition/foods")
                        .with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(foodId))
                .andExpect(jsonPath("$[0].name").value("Chicken Breast"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/nutrition/foods/{foodId}", foodId)
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodPayload("Updated chicken", 260, 46)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated chicken"))
                .andExpect(jsonPath("$.calories").value(260.0));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/nutrition/foods/{foodId}", foodId)
                        .with(member("member-123")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/nutrition/foods").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void nutritionRoutesRejectMissingAuthenticationAndUnsupportedRoles() throws Exception {
        mockMvc.perform(get("/api/nutrition/foods"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/nutrition/profiles")
                        .with(jwt().jwt(token -> token.subject("coach-123"))
                                .authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedAndUnknownFoodIdentifiersReturnClientErrorsWithoutDataLeakage() throws Exception {
        long foodId = createFood("member-123", "Private food", 200, 20);

        mockMvc.perform(put("/api/nutrition/foods/{foodId}", "not-a-number")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodPayload("Changed food", 300, 30)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/nutrition/foods/{foodId}", Long.MAX_VALUE)
                        .with(member("member-123")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/nutrition/foods").with(member("member-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Private food", foodRepository.findById(foodId).orElseThrow().getName());
    }

    @Test
    void foodValidationRejectsMissingNamesNonpositiveServingAndNegativeNutrition() throws Exception {
        mockMvc.perform(post("/api/nutrition/foods").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodPayload(" ", 250, 45)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/foods").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid serving",
                                  "servingSizeGrams": 0,
                                  "calories": 250,
                                  "proteinGrams": 45,
                                  "carbohydrateGrams": 0,
                                  "fatGrams": 5,
                                  "fiberGrams": 0,
                                  "sodiumMg": 120
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/foods").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodPayload("Negative calories", -1, 45)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/foods").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Missing nutrients",
                                  "servingSizeGrams": 100
                                }
                                """))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertTrue(foodRepository.findAll().isEmpty());
    }

    @Test
    void mealPlanAndJournalAreStoredAndCalculated() throws Exception {
        long oatsId = createFood("member-123", "Oats", 380, 12);

        mockMvc.perform(post("/api/nutrition/meal-plans")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Breakfast",
                                  "description": "High fibre breakfast",
                                  "mealType": "BREAKFAST",
                                  "targetCalories": 400,
                                  "foods": [
                                    { "foodId": %d, "servings": 1.0 }
                                  ]
                                }
                                """.formatted(oatsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealType").value("BREAKFAST"));
        mockMvc.perform(get("/api/nutrition/meal-plans").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Breakfast"));
        mockMvc.perform(get("/api/nutrition/meal-plans").with(member("member-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/nutrition/food-journal")
                                .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "foodId": %d,
                                  "mealType": "BREAKFAST",
                                  "entryDate": "2026-10-23",
                                  "servings": 1.0,
                                  "notes": "Good start"
                                }
                                """.formatted(oatsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Good start"));
        mockMvc.perform(get("/api/nutrition/food-journal").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notes").value("Good start"));
        mockMvc.perform(get("/api/nutrition/food-journal").with(member("member-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/nutrition/calculations?foodIds=" + oatsId)
                        .with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(380.0))
                .andExpect(jsonPath("$.proteinGrams").value(12.0));
    }

    @Test
    void servingAwareNutritionUsesEachRequestedServingQuantity() throws Exception {
        String created = mockMvc.perform(post("/api/nutrition/foods")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Serving test",
                                  "servingSizeGrams": 100,
                                  "calories": 200,
                                  "proteinGrams": 10,
                                  "carbohydrateGrams": 20,
                                  "fatGrams": 5,
                                  "fiberGrams": 3,
                                  "sodiumMg": 100
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long foodId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/nutrition/calculations")
                        .with(member("member-123"))
                        .param("foodIds", String.valueOf(foodId))
                        .param("servings", "2.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(500.0))
                .andExpect(jsonPath("$.proteinGrams").value(25.0))
                .andExpect(jsonPath("$.carbohydrateGrams").value(50.0))
                .andExpect(jsonPath("$.fatGrams").value(12.5))
                .andExpect(jsonPath("$.fiberGrams").value(7.5))
                .andExpect(jsonPath("$.sodiumMg").value(250.0));
    }

    @Test
    void servingAwareCalculationRejectsMismatchedAndInvalidServings() throws Exception {
        long foodId = createFood("member-123", "Serving test", 200, 10);

        mockMvc.perform(get("/api/nutrition/calculations")
                        .with(member("member-123"))
                        .param("foodIds", String.valueOf(foodId), String.valueOf(foodId))
                        .param("servings", "1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/nutrition/calculations")
                        .with(member("member-123"))
                        .param("foodIds", String.valueOf(foodId))
                        .param("servings", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/nutrition/calculations")
                        .with(member("member-123"))
                        .param("foodIds", String.valueOf(foodId))
                        .param("servings", "1.7976931348623157E308"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("numeric limits")));

        mockMvc.perform(get("/api/nutrition/calculations")
                        .with(member("member-456"))
                        .param("foodIds", String.valueOf(foodId))
                        .param("servings", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void dailyJournalTotalsUseServingQuantitiesAndAreMemberScoped() throws Exception {
        String created = mockMvc.perform(post("/api/nutrition/foods")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Daily total food",
                                  "servingSizeGrams": 100,
                                  "calories": 200,
                                  "proteinGrams": 10,
                                  "carbohydrateGrams": 20,
                                  "fatGrams": 5,
                                  "fiberGrams": 3,
                                  "sodiumMg": 100
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long foodId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(post("/api/nutrition/food-journal").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(foodId, "2026-10-23", 1.5)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/nutrition/food-journal").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(foodId, "2026-10-23", 0.5)
                                .replace("BREAKFAST", "LUNCH")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/nutrition/food-journal").with(member("member-456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(foodId, "2026-10-23", 9)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/nutrition/food-journal/daily")
                        .with(member("member-123"))
                        .param("date", "2026-10-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.calories").value(400.0))
                .andExpect(jsonPath("$.totals.proteinGrams").value(20.0))
                .andExpect(jsonPath("$.totals.carbohydrateGrams").value(40.0))
                .andExpect(jsonPath("$.totals.fatGrams").value(10.0))
                .andExpect(jsonPath("$.totals.fiberGrams").value(6.0))
                .andExpect(jsonPath("$.totals.sodiumMg").value(200.0))
                .andExpect(jsonPath("$.meals[0].mealType").value("BREAKFAST"))
                .andExpect(jsonPath("$.meals[0].totals.calories").value(300.0))
                .andExpect(jsonPath("$.meals[1].mealType").value("LUNCH"))
                .andExpect(jsonPath("$.meals[1].totals.calories").value(100.0));

        mockMvc.perform(get("/api/nutrition/food-journal/daily")
                        .with(member("member-456"))
                        .param("date", "2026-10-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.calories").value(0.0))
                .andExpect(jsonPath("$.meals").isEmpty());
    }

    @Test
    void bmiUsesCentimetreToMetreConversionAndMacroTargetsMatchSavedCalories() throws Exception {
        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Test profile",
                                  "goal": "muscle gain",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "age": 30,
                                  "heightCm": 180,
                                  "weightKg": 81,
                                  "targetCalories": 2200
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/nutrition/calculations/bmi").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bmi").value(25.0))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("not a measure of body-fat percentage")));

        mockMvc.perform(get("/api/nutrition/calculations/macros").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calorieTarget").value(2200))
                .andExpect(jsonPath("$.proteinGrams").value(129.6))
                .andExpect(jsonPath("$.fatGrams").value(org.hamcrest.Matchers.closeTo(2200 * 0.25 / 9, 0.0001)))
                .andExpect(jsonPath("$.estimatedCalories").value(2200.0));
    }

    @Test
    void bmiAndMacroEstimatesExplainMissingProfileInputs() throws Exception {
        mockMvc.perform(get("/api/nutrition/calculations/bmi").with(member("member-123")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Incomplete profile",
                                  "goal": "muscle gain",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "age": 30
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/nutrition/calculations/bmi").with(member("member-123")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("height in centimetres")));

        mockMvc.perform(get("/api/nutrition/calculations/macros").with(member("member-123")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("targetCalories")));
    }

    @Test
    void profilePersistsTargetsAndSeparateFoodPreferencesAndProvidesAnExplainableTimeline() throws Exception {
        String requestedDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusWeeks(52).toString();
        String profileRequest = """
                {
                  "profileName":"Goal profile",
                  "goal":"weight loss",
                  "dietaryPreference":"balanced",
                  "activityLevel":"moderate",
                  "age":30,
                  "heightCm":180,
                  "weightKg":80,
                  "targetCalories":2000,
                  "calorieTargetMode":"MANUAL",
                  "targetWeightKg":70,
                  "targetDate":"%s",
                  "allergies":["peanuts"],
                  "intolerances":["lactose"],
                  "dislikedFoods":["olives"],
                  "preferredIngredients":["lentils"],
                  "budgetPreference":"LOW",
                  "maxPreparationMinutes":30,
                  "cookingPreference":"One-pan meals"
                }
                """.formatted(requestedDate);
        mockMvc.perform(post("/api/nutrition/profiles").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON).content(profileRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetWeightKg").value(70.0))
                .andExpect(jsonPath("$.targetDate").value(requestedDate))
                .andExpect(jsonPath("$.allergies[0]").value("peanuts"))
                .andExpect(jsonPath("$.intolerances[0]").value("lactose"))
                .andExpect(jsonPath("$.dislikedFoods[0]").value("olives"))
                .andExpect(jsonPath("$.preferredIngredients[0]").value("lentils"))
                .andExpect(jsonPath("$.budgetPreference").value("LOW"))
                .andExpect(jsonPath("$.maxPreparationMinutes").value(30))
                .andExpect(jsonPath("$.cookingPreference").value("One-pan meals"));

        mockMvc.perform(get("/api/nutrition/profiles").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetWeightKg").value(70.0))
                .andExpect(jsonPath("$.allergies[0]").value("peanuts"));
        mockMvc.perform(get("/api/nutrition/calculations/goal-timeline").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.minimumWeeks").value(20))
                .andExpect(jsonPath("$.maximumWeeks").value(40))
                .andExpect(jsonPath("$.requestedDateStatus").value("LATER_THAN_ESTIMATE"))
                .andExpect(jsonPath("$.assumptions").value(
                        org.hamcrest.Matchers.containsString("0.25–0.5 kg/week")));

        mockMvc.perform(post("/api/nutrition/profiles").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileRequest.replace(",\n  \"allergies\":[\"peanuts\"]"
                                        + ",\n  \"intolerances\":[\"lactose\"]"
                                        + ",\n  \"dislikedFoods\":[\"olives\"]"
                                        + ",\n  \"preferredIngredients\":[\"lentils\"]"
                                        + ",\n  \"budgetPreference\":\"LOW\""
                                        + ",\n  \"maxPreparationMinutes\":30"
                                        + ",\n  \"cookingPreference\":\"One-pan meals\"", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergies[0]").value("peanuts"))
                .andExpect(jsonPath("$.intolerances[0]").value("lactose"));
    }

    @Test
    void profileRejectsUnsafeIncompatibleMinorAndPastDateTargets() throws Exception {
        String base = """
                {"profileName":"Goal","goal":"weight loss","dietaryPreference":"balanced",
                "activityLevel":"moderate","age":30,"heightCm":180,"weightKg":80,
                "targetCalories":2000,"calorieTargetMode":"MANUAL","targetWeightKg":%s}
                """;
        mockMvc.perform(post("/api/nutrition/profiles").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base.formatted("50")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("BMI below 18.5")));
        mockMvc.perform(post("/api/nutrition/profiles").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base.formatted("85")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("agree with a supported")));
        mockMvc.perform(post("/api/nutrition/profiles").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base.replace("\"age\":30", "\"age\":17").formatted("70")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not supported for minors")));
        String pastDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString();
        mockMvc.perform(post("/api/nutrition/profiles").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileName":"Goal","goal":"weight loss","dietaryPreference":"balanced",
                                "activityLevel":"moderate","age":30,"heightCm":180,"weightKg":80,
                                "targetCalories":2000,"calorieTargetMode":"MANUAL","targetWeightKg":70,
                                "targetDate":"%s"}
                                """.formatted(pastDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("future")));
    }

    @Test
    void hydrationLogsPersistAreCorrectableMemberScopedAndValidateAmountsAndTimestamps() throws Exception {
        java.time.Instant now = java.time.Instant.now();
        java.time.LocalDate date = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        String payload = objectMapper.writeValueAsString(new NutritionService.HydrationRequest(now, 350));
        long id = objectMapper.readTree(mockMvc.perform(post("/api/nutrition/hydration")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(date.toString()))
                .andExpect(jsonPath("$.consumedMl").value(350))
                .andExpect(jsonPath("$.targetMl").value(2000))
                .andExpect(jsonPath("$.targetAssumptions").value(
                        org.hamcrest.Matchers.containsString("excludes moisture in food")))
                .andReturn().getResponse().getContentAsString()).path("logs").get(0).path("id").asLong();

        mockMvc.perform(get("/api/nutrition/hydration").with(member("member-123"))
                        .param("date", date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.logs[0].id").value(id));
        mockMvc.perform(get("/api/nutrition/hydration").with(member("member-456"))
                        .param("date", date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consumedMl").value(0))
                .andExpect(jsonPath("$.logs").isEmpty());
        mockMvc.perform(get("/api/nutrition/hydration/history").with(member("member-123"))
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].waterMl").value(350));

        mockMvc.perform(put("/api/nutrition/hydration/{id}", id).with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NutritionService.HydrationRequest(now, 500))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consumedMl").value(500));
        mockMvc.perform(put("/api/nutrition/hydration/{id}", id).with(member("member-456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NutritionService.HydrationRequest(now, 400))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/nutrition/hydration").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NutritionService.HydrationRequest(now, 2001))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/nutrition/hydration").with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NutritionService.HydrationRequest(
                                java.time.Instant.now().plusSeconds(86400), 250))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/nutrition/hydration/history").with(member("member-123"))
                        .param("from", date.toString()).param("to", date.plusDays(367).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conventionalGoalAndActivityMappingsAreRecognizedWithoutEnablingEnergyEstimates() throws Exception {
        assertEquals(NutritionService.GoalCategory.WEIGHT_LOSS,
                NutritionService.mapGoalCategory(" Weight Loss ").orElseThrow());
        assertEquals(NutritionService.GoalCategory.MAINTENANCE,
                NutritionService.mapGoalCategory("MAINTENANCE").orElseThrow());
        assertEquals(NutritionService.GoalCategory.MUSCLE_GAIN,
                NutritionService.mapGoalCategory("muscle gain").orElseThrow());
        assertTrue(NutritionService.mapGoalCategory("fitness").isEmpty());

        assertEquals(1.2, NutritionService.mapActivityFactor("sedentary").orElseThrow());
        assertEquals(1.375, NutritionService.mapActivityFactor("light").orElseThrow());
        assertEquals(1.55, NutritionService.mapActivityFactor("moderate").orElseThrow());
        assertEquals(1.725, NutritionService.mapActivityFactor("active").orElseThrow());
        assertEquals(1.9, NutritionService.mapActivityFactor(" Very Active ").orElseThrow());
        assertTrue(NutritionService.mapActivityFactor("unknown").isEmpty());

        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Energy estimate unavailable",
                                  "goal": "weight loss",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "very active",
                                  "age": 30,
                                  "heightCm": 180,
                                  "weightKg": 81,
                                  "targetCalories": 2200
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/nutrition/calculations/energy").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.bmrCaloriesPerDay").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.tdeeCaloriesPerDay").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.goalCategory").value("WEIGHT_LOSS"))
                .andExpect(jsonPath("$.activityFactor").value(1.9))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("equation input")))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("no values have been assumed")));
    }

    @Test
    void automaticCalorieTargetUsesMifflinStJeorActivityAndGoalAdjustment() throws Exception {
        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Automatic target",
                                  "goal": "weight loss",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "age": 30,
                                  "heightCm": 180,
                                  "weightKg": 81,
                                  "calorieTargetMode": "AUTO",
                                  "energyEquationSex": "MALE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calorieTargetMode").value("AUTO"))
                .andExpect(jsonPath("$.energyEquationSex").value("MALE"))
                .andExpect(jsonPath("$.targetCalories").value(2358));

        mockMvc.perform(get("/api/nutrition/calculations/energy").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.bmrCaloriesPerDay").value(1790.0))
                .andExpect(jsonPath("$.tdeeCaloriesPerDay").value(2774.5))
                .andExpect(jsonPath("$.goalAdjustedCalories").value(2358))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("not a medical recommendation")));
    }

    @Test
    void automaticTargetRemainsUnsetUntilRequiredAdultEquationInputsAreProvided() throws Exception {
        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Incomplete automatic target",
                                  "goal": "maintenance",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "age": 30,
                                  "heightCm": 180,
                                  "weightKg": 81,
                                  "calorieTargetMode": "AUTO"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCalories").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/nutrition/calculations/energy").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("equation input")));
    }

    @Test
    void bmiAndMacroCalculationsRejectNonFiniteOrNegativeResults() throws Exception {
        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Unrealistic height",
                                  "goal": "maintenance",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "heightCm": 1e-300,
                                  "weightKg": 81,
                                  "targetCalories": 2200
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/nutrition/calculations/bmi").with(member("member-123")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("numeric limits")));

        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Insufficient calorie target",
                                  "goal": "maintenance",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "weightKg": 1e300,
                                  "targetCalories": 2200
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/nutrition/calculations/macros").with(member("member-123")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("too low")));
    }

    @Test
    void bodyMeasurementsAreStoredAndReturned() throws Exception {
        mockMvc.perform(post("/api/nutrition/body-measurements")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "measurementDate": "2026-10-23",
                                  "weightKg": 72.5,
                                  "waistCm": 82.0,
                                  "hipCm": 96.0,
                                  "chestCm": 100.0,
                                  "armCm": 33.0,
                                  "thighCm": 55.0,
                                  "notes": "Stable week"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(72.5));

        mockMvc.perform(get("/api/nutrition/body-measurements")
                        .with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notes").value("Stable week"));
    }

    @Test
    void nutritionProfileCanBeRetrievedAndUpdatedForTheAuthenticatedMember() throws Exception {
        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "First profile",
                                  "goal": "maintenance",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "light",
                                  "weightKg": 70,
                                  "targetCalories": 2000
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/nutrition/profiles").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileName").value("First profile"));

        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Updated profile",
                                  "goal": "muscle gain",
                                  "dietaryPreference": "vegetarian",
                                  "activityLevel": "moderate",
                                  "weightKg": 72,
                                  "targetCalories": 2400
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileName").value("Updated profile"));

        mockMvc.perform(get("/api/nutrition/profiles").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileName").value("Updated profile"))
                .andExpect(jsonPath("$.targetCalories").value(2400));
        mockMvc.perform(get("/api/nutrition/profiles").with(member("member-456")))
                .andExpect(status().isNotFound());
    }


    @Test
    void assistantReturnsPlanEvenWithoutRuntime() throws Exception {
        MvcResult pendingResponse = mockMvc.perform(post("/api/nutrition/assistant/plan")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goal": "muscle gain",
                                  "targetCalories": 2600,
                                  "dietStyle": "balanced",
                                  "restrictions": []
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pendingResponse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    void assistantRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/nutrition/assistant/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restrictions\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void llmInferenceUsesBoundedExecutor() {
        org.junit.jupiter.api.Assertions.assertEquals(2, llmExecutor.getCorePoolSize());
        org.junit.jupiter.api.Assertions.assertEquals(4, llmExecutor.getMaxPoolSize());
        org.junit.jupiter.api.Assertions.assertEquals(8,
                llmExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());
    }

    @Test
    void memberCanSaveGeneratedRecipeWithoutExistingFoodLibraryMatches() throws Exception {
        MealPlanGenerationContext context = generationContextRepository.save(
                new MealPlanGenerationContext("member-123", List.of("peanuts")));
        String payload = """
                {
                  "generationContextId": "%s",
                  "planName": "Healthy week",
                  "goal": "maintenance",
                  "meals": [{
                    "name": "Chicken with rice",
                    "mealType": "LUNCH",
                    "servingSizeGrams": 420,
                    "preparationInstructions": "Bake chicken and cook the rice.",
                    "ingredients": [
                      {"name": "Chicken breast", "servings": 150},
                      {"name": "Brown rice", "servings": 100}
                    ],
                    "estimatedNutrition": {
                      "calories": 520, "proteinGrams": 42, "carbohydrateGrams": 55,
                      "fatGrams": 12, "fiberGrams": 7, "sodiumMg": 390
                    }
                  }]
                }
                """.formatted(context.getId());

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/nutrition/meal-plans").with(member("member-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("\"generationContextId\": \"" + context.getId() + "\",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("generation context")));

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("\"goal\": \"maintenance\",",
                                "\"goal\": \"maintenance\", \"restrictions\": [\"chicken allergy\"],")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("original generation context")));

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("\"goal\": \"maintenance\",",
                                "\"goal\": \"maintenance\", \"restrictions\": [],")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("original generation context")));

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("Chicken breast", "Peanut butter")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("excluded")));

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chicken with rice"))
                .andExpect(jsonPath("$[0].targetCalories").value(520.0))
                .andExpect(jsonPath("$[0].description").value(org.hamcrest.Matchers.containsString("Brown rice")))
                .andExpect(jsonPath("$[0].foods[0].foodName").value("Chicken with rice"));

        MealPlan savedPlan = mealPlanRepository.findAll().getFirst();
        org.junit.jupiter.api.Assertions.assertEquals(context.getId(),
                savedPlan.getGenerationContext().getId());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of("peanuts"),
                generationContextRepository.findById(context.getId()).orElseThrow().getRestrictions());

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been used")));

        mockMvc.perform(get("/api/nutrition/foods").with(member("member-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chicken with rice"))
                .andExpect(jsonPath("$[0].servingSizeGrams").value(420))
                .andExpect(jsonPath("$[0].calories").value(520.0))
                .andExpect(jsonPath("$[0].brand").value(org.hamcrest.Matchers.containsString("AI-generated")));
    }

    @Test
    void memberCannotReadOrModifyAnotherMembersFood() throws Exception {
        long foodId = createFood("member-123", "Private food", 180, 20);

        mockMvc.perform(get("/api/nutrition/foods").with(member("member-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/nutrition/foods/{foodId}", foodId)
                        .with(member("member-456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodPayload("Stolen food", 1, 1)))
                .andExpect(status().isNotFound());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/nutrition/foods/{foodId}", foodId)
                        .with(member("member-456")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/nutrition/calculations?foodIds=" + foodId)
                        .with(member("member-456")))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals("Private food", foodRepository.findById(foodId).orElseThrow().getName());
    }

    @Test
    void mealPlansCanBeUpdatedOnlyByTheirOwnerAndRejectForeignFoods() throws Exception {
        long ownerFoodId = createFood("member-123", "Owner food", 200, 20);
        long otherFoodId = createFood("member-456", "Other food", 100, 10);
        String planPayload = mealPlanPayload("Original plan", ownerFoodId, 1);
        String planResponse = mockMvc.perform(post("/api/nutrition/meal-plans")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long planId = objectMapper.readTree(planResponse).get("id").asLong();

        mockMvc.perform(put("/api/nutrition/meal-plans/{id}", planId)
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealPlanPayload("Updated plan", ownerFoodId, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated plan"))
                .andExpect(jsonPath("$.foods[0].servings").value(2.0));

        mockMvc.perform(put("/api/nutrition/meal-plans/{id}", planId)
                        .with(member("member-456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealPlanPayload("Foreign plan", otherFoodId, 1)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/nutrition/meal-plans/{id}", planId)
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealPlanPayload("Foreign food", otherFoodId, 1)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/nutrition/meal-plans/{id}", planId)
                        .with(member("member-456")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/nutrition/meal-plans/{id}", planId)
                        .with(member("member-123")))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(mealPlanRepository.findById(planId).isEmpty());
    }

    @Test
    void journalEntriesCanBeUpdatedAndDeletedWithOwnerAndFoodValidation() throws Exception {
        long foodId = createFood("member-123", "Journal food", 200, 20);
        long otherFoodId = createFood("member-456", "Other food", 100, 10);
        String entryResponse = mockMvc.perform(post("/api/nutrition/food-journal")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(foodId, "2026-10-23", 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long entryId = objectMapper.readTree(entryResponse).get("id").asLong();

        mockMvc.perform(put("/api/nutrition/food-journal/{id}", entryId)
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(foodId, "2026-10-24", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryDate").value("2026-10-24"))
                .andExpect(jsonPath("$.servings").value(2.0));

        mockMvc.perform(put("/api/nutrition/food-journal/{id}", entryId)
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(otherFoodId, "2026-10-24", 1)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/nutrition/food-journal/{id}", entryId)
                        .with(member("member-456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(otherFoodId, "2026-10-24", 1)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/nutrition/food-journal/{id}", entryId)
                        .with(member("member-456")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/nutrition/food-journal/{id}", entryId)
                        .with(member("member-123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void bodyMeasurementsCanBeUpdatedAndDeletedOnlyByTheirOwner() throws Exception {
        String payload = measurementPayload("2026-10-23", 72.5);
        String response = mockMvc.perform(post("/api/nutrition/body-measurements")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long measurementId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(put("/api/nutrition/body-measurements/{id}", measurementId)
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(measurementPayload("2026-10-24", 71.5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(71.5))
                .andExpect(jsonPath("$.measurementDate").value("2026-10-24"));

        mockMvc.perform(delete("/api/nutrition/body-measurements/{id}", measurementId)
                        .with(member("member-456")))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/nutrition/body-measurements/{id}", measurementId)
                        .with(member("member-456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(measurementPayload("2026-10-25", 70)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/nutrition/body-measurements/{id}", measurementId)
                        .with(member("member-123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void profilesAndMeasurementsRejectInvalidPositiveValues() throws Exception {
        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Profile",
                                  "goal": "fitness",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "age": 0,
                                  "heightCm": 175,
                                  "weightKg": 70,
                                  "targetCalories": 2000
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Profile",
                                  "goal": "fitness",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "age": 30,
                                  "heightCm": 0,
                                  "weightKg": 70,
                                  "targetCalories": 2000
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Profile",
                                  "goal": "fitness",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "age": 30,
                                  "heightCm": 175,
                                  "weightKg": 0,
                                  "targetCalories": 2000
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/profiles")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileName": "Profile",
                                  "goal": "fitness",
                                  "dietaryPreference": "balanced",
                                  "activityLevel": "moderate",
                                  "age": 30,
                                  "heightCm": 175,
                                  "weightKg": 70,
                                  "targetCalories": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/body-measurements")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(measurementPayload("2026-10-23", 0)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/body-measurements")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "measurementDate": "2026-10-23",
                                  "weightKg": 72.5
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void journalRejectsMissingDateAndNonpositiveServings() throws Exception {
        long foodId = createFood("member-123", "Journal food", 200, 20);

        mockMvc.perform(post("/api/nutrition/food-journal")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "foodId": %d,
                                  "mealType": "BREAKFAST",
                                  "servings": 1
                                }
                                """.formatted(foodId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/food-journal")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(foodId, "2026-10-23", 0)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nutrition/meal-plans")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Missing servings",
                                  "mealType": "BREAKFAST",
                                  "targetCalories": 200,
                                  "foods": [{ "foodId": %d }]
                                }
                                """.formatted(foodId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void foodsReferencedByPlansOrJournalCannotBeDeleted() throws Exception {
        long foodId = createFood("member-123", "Referenced food", 200, 20);
        mockMvc.perform(post("/api/nutrition/food-journal")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalPayload(foodId, "2026-10-23", 1)))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/nutrition/foods/{foodId}", foodId)
                        .with(member("member-123")))
                .andExpect(status().isConflict());
    }

    @Test
    void foodsReferencedOnlyByMealPlansCannotBeDeleted() throws Exception {
        long foodId = createFood("member-123", "Planned food", 200, 20);
        mockMvc.perform(post("/api/nutrition/meal-plans")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealPlanPayload("Planned meal", foodId, 1)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/nutrition/foods/{foodId}", foodId)
                        .with(member("member-123")))
                .andExpect(status().isConflict());
    }

    private long createFood(String subject, String name, int calories, int protein) throws Exception {
        String response = mockMvc.perform(post("/api/nutrition/foods")
                        .with(member(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodPayload(name, calories, protein)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String mealPlanPayload(String name, long foodId, double servings) {
        return """
                {
                  "name": "%s",
                  "description": "Plan description",
                  "mealType": "BREAKFAST",
                  "targetCalories": 400,
                  "foods": [{ "foodId": %d, "servings": %s }]
                }
                """.formatted(name, foodId, servings);
    }

    private String journalPayload(long foodId, String entryDate, double servings) {
        return """
                {
                  "foodId": %d,
                  "mealType": "BREAKFAST",
                  "entryDate": "%s",
                  "servings": %s,
                  "notes": "Journal note"
                }
                """.formatted(foodId, entryDate, servings);
    }

    private String measurementPayload(String date, double weight) {
        return """
                {
                  "measurementDate": "%s",
                  "weightKg": %s,
                  "waistCm": 82.0,
                  "hipCm": 96.0,
                  "chestCm": 100.0,
                  "armCm": 33.0,
                  "thighCm": 55.0
                }
                """.formatted(date, weight);
    }

    private String foodPayload(String name, int calories, int protein) {
        return """
                {
                  "name": "%s",
                  "brand": "Smart Gym",
                  "servingSizeGrams": 100,
                  "calories": %d,
                  "proteinGrams": %d,
                  "carbohydrateGrams": 0,
                  "fatGrams": 5,
                  "fiberGrams": 0,
                  "sodiumMg": 120
                }
                """.formatted(name, calories, protein);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor member(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }
}
