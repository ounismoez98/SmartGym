package com.smartgym.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LocalLlmMealPlanAssistantTest {
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "openai/gpt-oss-20b";
    private static final String TEST_API_KEY = "test-groq-key";
    private static final String VALID_PLAN = """
            {
              "planName":"Simple plan",
              "goal":"maintenance",
              "meals":[{
                "name":"Breakfast",
                "mealType":"BREAKFAST",
                "servingSizeGrams":350,
                "foods":[{"name":"Oats","servings":2}],
                "preparationInstructions":"Cook with water.",
                "estimatedNutrition":{"calories":999,"proteinGrams":99,"carbohydrateGrams":99,"fatGrams":99,"fiberGrams":10,"sodiumMg":150}
              }],
              "shoppingList":["Oats"],
              "alternatives":["Rice"]
            }
            """;

    @Test
    void missingApiKeyIsUnavailableWithoutCallingGroq() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        LocalLlmMealPlanAssistant assistant =
                new LocalLlmMealPlanAssistant(restTemplate, new ObjectMapper(), "", GROQ_MODEL);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertEquals("UNAVAILABLE", result.status());
        assertNull(result.plan());
        assertTrue(result.note().contains("GROQ_API_KEY"));
        server.verify();
    }

    @Test
    void missingModelIsUnavailableWithoutCallingGroq() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        LocalLlmMealPlanAssistant assistant =
                new LocalLlmMealPlanAssistant(restTemplate, new ObjectMapper(), TEST_API_KEY, "");

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertEquals("UNAVAILABLE", result.status());
        assertNull(result.plan());
        assertTrue(result.note().contains("GROQ_MODEL"));
        server.verify();
    }

    @Test
    void groqChatRequestUsesBearerAuthStructuredJsonAndParsesChoicesContent() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TEST_API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"" + GROQ_MODEL + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"max_completion_tokens\":8192")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"json_schema\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"strict\":true")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"fiberGrams\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"sodiumMg\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"servingSizeGrams\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Optional saved food library")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Oats")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("fiberGrams=4.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sodiumMg=10.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("exactly three meals")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"shoppingList\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "goal=maintenance; saved target calories per day=2000; dietary preference=balanced")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lean protein")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("baking/grilling/stewing")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("limit deep-frying")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Create complete recipes whether or not their ingredients exist in the saved food library")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Never change or omit recipe ingredients just to match the library")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("member-123"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("test-groq-key"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"age\""))))
                .andRespond(withSuccess(groqResponse(VALID_PLAN), MediaType.APPLICATION_JSON));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);
        Food oats = new Food("member-123", "Oats", null, 40, 150, 5, 27, 3, 4, 10);
        ReflectionTestUtils.setField(oats, "id", 7L);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of(oats));

        assertTrue(result.available());
        assertNotNull(result.plan());
        assertEquals(7L, result.plan().meals().get(0).foods().get(0).foodId());
        assertEquals("SAVED_FOOD_DATA", result.plan().meals().get(0).nutritionSource());
        assertEquals(300.0, result.plan().meals().get(0).estimatedNutrition().calories());
        assertEquals(350, result.plan().meals().get(0).servingSizeGrams());
        assertEquals(8.0, result.plan().meals().get(0).estimatedNutrition().fiberGrams());
        assertEquals(10.0, result.plan().dailyNutrition().proteinGrams());
        server.verify();
    }

    @Test
    void generatedRecipeDoesNotRequireSavedFoodMatches() throws Exception {
        String recipePlan = VALID_PLAN.replace("\"name\":\"Oats\"", "\"name\":\"Baked chicken escalope with rice\"")
                .replace("\"name\":\"Oats\"", "\"name\":\"Chicken escalope\"")
                .replace("\"shoppingList\":[\"Oats\"]", "\"shoppingList\":[\"Chicken breast\",\"Rice\"]");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Optional saved food library")))
                .andRespond(withSuccess(groqResponse(recipePlan), MediaType.APPLICATION_JSON));

        NutritionService.MealPlanSuggestion result =
                assistant(restTemplate).generate(request(List.of()), profile(), List.of());

        assertTrue(result.available());
        assertNotNull(result.plan());
        assertNull(result.plan().meals().get(0).foods().get(0).foodId());
        assertEquals("MODEL_ESTIMATE", result.plan().meals().get(0).nutritionSource());
        assertEquals(999.0, result.plan().meals().get(0).estimatedNutrition().calories());
        assertEquals(350, result.plan().meals().get(0).servingSizeGrams());
        server.verify();
    }

    @Test
    void generatedPlanDoesNotRequireUnsavedShoppingListOrAlternativesFields() throws Exception {
        String compactPlan = VALID_PLAN.replaceAll(
                ",\\s*\"shoppingList\"\\s*:\\s*\\[\"Oats\"\\],\\s*\"alternatives\"\\s*:\\s*\\[\"Rice\"\\]", "");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withSuccess(groqResponse(compactPlan), MediaType.APPLICATION_JSON));

        NutritionService.MealPlanSuggestion result =
                assistant(restTemplate).generate(request(List.of()), profile(), List.of());

        assertTrue(result.available());
        assertNotNull(result.plan());
        assertTrue(result.plan().shoppingList().isEmpty());
        assertTrue(result.plan().alternatives().isEmpty());
        server.verify();
    }

    @Test
    void legacyGenerationRejectsPreparationInstructionsOverItsPersistenceLimit() throws Exception {
        String responsePlan = VALID_PLAN.replace("Cook with water.", "a".repeat(351));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withSuccess(groqResponse(responsePlan), MediaType.APPLICATION_JSON));

        NutritionService.MealPlanSuggestion result =
                assistant(restTemplate).generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertNull(result.plan());
        assertTrue(result.note().contains("1 to 350 characters"));
        server.verify();
    }

    @Test
    void persistentPlanGenerationAcceptsLongerInstructionsSupportedByItsEntity() throws Exception {
        String responsePlan = VALID_PLAN.replace("Cook with water.", "a".repeat(400));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 to 2000 characters")))
                .andRespond(withSuccess(groqResponse(responsePlan), MediaType.APPLICATION_JSON));

        NutritionService.MealPlanSuggestion result = assistant(restTemplate)
                .generatePersistentPlanAsync(request(List.of()), profile(), List.of()).join();

        assertTrue(result.available());
        assertNotNull(result.plan());
        assertEquals(400, result.plan().meals().get(0).preparationInstructions().length());
        server.verify();
    }

    @Test
    void malformedGroqMessageContentIsRejectedAsUnavailable() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withSuccess(groqResponse("not JSON"), MediaType.APPLICATION_JSON));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertNull(result.plan());
        assertTrue(result.note().contains("malformed JSON"));
        server.verify();
    }

    @Test
    void missingGroqChoicesContentIsRejected() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertTrue(result.note().contains("no structured meal-plan content"));
        server.verify();
    }

    @Test
    void malformedGroqEnvelopeIsRejectedWithoutExposingResponseDetails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withSuccess("{malformed", MediaType.APPLICATION_JSON));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertNull(result.plan());
        assertTrue(result.note().contains("invalid HTTP response"));
        assertFalse(result.note().contains("malformed"));
        server.verify();
    }

    @Test
    void invalidPlanMissingRequiredFieldsIsRejected() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withSuccess(groqResponse("{\"planName\":\"Incomplete\"}"),
                        MediaType.APPLICATION_JSON));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertNull(result.plan());
        assertTrue(result.note().contains("missing required plan fields"));
        server.verify();
    }

    @Test
    void restrictedIngredientRejectsTheEntireGeneratedPlan() throws Exception {
        String planWithPeanuts = VALID_PLAN.replace("\"Oats\"", "\"Peanut butter\"")
                .replace("\"Rice\"", "\"Peanut flour\"");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withSuccess(groqResponse(planWithPeanuts), MediaType.APPLICATION_JSON));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result =
                assistant.generate(request(List.of("peanut allergy")), profile(), List.of());

        assertFalse(result.available());
        assertNull(result.plan());
        assertTrue(result.note().contains("excluded ingredient"));
        server.verify();
    }

    @Test
    void rateLimitReturnsSafeUnavailableMessage() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"rate limited\"}}"));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertTrue(result.note().contains("rate limit"));
        assertTrue(result.note().contains("Groq account usage and model limits"));
        assertFalse(result.note().contains(TEST_API_KEY));
        server.verify();
    }

    @Test
    void rateLimitIncludesSafeRetryAfterHeaderWhenProvided() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"private provider response\"}}"));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertTrue(result.note().contains("Wait about 30 seconds"));
        assertTrue(result.note().contains("Groq account usage and model limits"));
        assertFalse(result.note().contains("private provider response"));
        assertFalse(result.note().contains(TEST_API_KEY));
        server.verify();
    }

    @Test
    void invalidCredentialsReturnSafeUnavailableMessage() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"invalid key\"}}"));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertTrue(result.note().contains("API key"));
        assertFalse(result.note().contains(TEST_API_KEY));
        server.verify();
    }

    @Test
    void forbiddenAndServerErrorsReturnSafeUnavailableMessages() {
        assertHttpErrorIsUnavailable(HttpStatus.FORBIDDEN, "account permissions");
        assertHttpErrorIsUnavailable(HttpStatus.INTERNAL_SERVER_ERROR, "temporarily unavailable (HTTP 500)");
    }

    @Test
    void badRequestExplainsStructuredOutputConfigurationWithoutExposingProviderBody() {
        assertHttpErrorIsUnavailable(HttpStatus.BAD_REQUEST,
                "Review GROQ_MODEL availability, model request limits, and structured-output support");
    }

    @Test
    void rejectedStrictSchemaRetriesOnceWithValidatedJsonObjectMode() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"json_schema\"")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"response_format json_schema is not supported\"}}"));
        server.expect(requestTo(GROQ_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"json_object\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"max_completion_tokens\":8192")))
                .andRespond(withSuccess(groqResponse(VALID_PLAN), MediaType.APPLICATION_JSON));

        NutritionService.MealPlanSuggestion result =
                assistant(restTemplate).generate(request(List.of()), profile(), List.of());

        assertTrue(result.available());
        assertNotNull(result.plan());
        assertEquals("Breakfast", result.plan().meals().get(0).name());
        server.verify();
    }

    @Test
    void completionLimitRejectionDoesNotRetryAndExplainsTheTruncatedResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"max_completion_tokens\":8192")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"max completion tokens reached before valid document; "
                                + "missing properties shoppingList alternatives\","
                                + "\"type\":\"invalid_request_error\",\"code\":\"json_validate_failed\"}}"));

        NutritionService.MealPlanSuggestion result =
                assistant(restTemplate).generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertNull(result.plan());
        assertTrue(result.note().contains("output token limit was reached"));
        server.verify();
    }

    @Test
    void missingModelAndOversizedRequestsHaveActionableMessages() {
        assertHttpErrorIsUnavailable(HttpStatus.NOT_FOUND, "could not find the configured model (HTTP 404)");
        assertHttpErrorIsUnavailable(HttpStatus.PAYLOAD_TOO_LARGE, "request as too large (HTTP 413)");
    }

    @Test
    void groqTimeoutReturnsSafeUnavailableMessage() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("Read timed out");
                });
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertNull(result.plan());
        assertTrue(result.note().contains("timed out"));
        assertFalse(result.note().contains(TEST_API_KEY));
        server.verify();
    }

    @Test
    void unreachableGroqServiceReturnsUnavailable() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertTrue(result.note().contains("could not reach"));
        server.verify();
    }

    private LocalLlmMealPlanAssistant assistant(RestTemplate restTemplate) {
        return new LocalLlmMealPlanAssistant(
                restTemplate, new ObjectMapper(), TEST_API_KEY, GROQ_MODEL);
    }

    private void assertHttpErrorIsUnavailable(HttpStatus status, String expectedMessage) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(GROQ_URL))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"private provider response\"}}"));
        LocalLlmMealPlanAssistant assistant = assistant(restTemplate);

        NutritionService.MealPlanSuggestion result = assistant.generate(request(List.of()), profile(), List.of());

        assertFalse(result.available());
        assertNull(result.plan());
        assertTrue(result.note().contains(expectedMessage));
        assertFalse(result.note().contains(TEST_API_KEY));
        assertFalse(result.note().contains("private provider response"));
        server.verify();
    }

    private String groqResponse(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))));
    }

    private NutritionService.AiMealRequest request(List<String> restrictions) {
        return new NutritionService.AiMealRequest(null, null, null, restrictions);
    }

    private NutritionProfile profile() {
        return new NutritionProfile("member-123", "Profile", "maintenance",
                "balanced", "moderate", 30, 170.0, 70.0, 2000);
    }
}
