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
    private FoodJournalRepository foodJournalRepository;

    @Autowired
    private BodyMeasurementRepository bodyMeasurementRepository;

    @Autowired
    private NutritionProfileRepository nutritionProfileRepository;

    @Autowired
    @Qualifier("llmExecutor")
    private ThreadPoolTaskExecutor llmExecutor;

    @BeforeEach
    void clearNutritionData() {
        mealPlanRepository.deleteAll();
        foodJournalRepository.deleteAll();
        bodyMeasurementRepository.deleteAll();
        nutritionProfileRepository.deleteAll();
        foodRepository.deleteAll();
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
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("no sex-related input")))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("No value has been assumed")));
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
    void assistantReturnsPlanEvenWithoutRuntime() throws Exception {
        MvcResult pendingResponse = mockMvc.perform(post("/api/nutrition/assistant/plan")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goal": "muscle gain",
                                  "targetCalories": 2600,
                                  "dietStyle": "balanced"
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
    void reviewedAiPlanCanBeSavedOnlyUsingFoodsOwnedByAuthenticatedMember() throws Exception {
        long foodId = createFood("member-123", "Saved oats", 150, 5);
        String payload = """
                {
                  "planName": "Reviewed plan",
                  "goal": "maintenance",
                  "meals": [{
                    "name": "Breakfast",
                    "mealType": "BREAKFAST",
                    "preparationInstructions": "Cook with water.",
                    "foods": [{"foodId": %d, "servings": 2}]
                  }]
                }
                """.formatted(foodId);

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/nutrition/meal-plans").with(member("member-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("\"goal\": \"maintenance\",",
                                "\"goal\": \"maintenance\", \"restrictions\": [\"oats allergy\"],")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("excluded")));

        mockMvc.perform(post("/api/nutrition/assistant/plan/save")
                        .with(member("member-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Reviewed plan - Breakfast"))
                .andExpect(jsonPath("$[0].targetCalories").value(300.0))
                .andExpect(jsonPath("$[0].description").value(org.hamcrest.Matchers.containsString("Cook with water.")));
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
