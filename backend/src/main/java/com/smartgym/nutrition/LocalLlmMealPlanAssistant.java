package com.smartgym.nutrition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class LocalLlmMealPlanAssistant {
    private static final int LEGACY_PREPARATION_INSTRUCTIONS_LIMIT = 350;
    private static final int PERSISTENT_PREPARATION_INSTRUCTIONS_LIMIT = 2000;
    private static final int MAX_COMPLETION_TOKENS = 8192;
    private static final String CHAT_COMPLETIONS_URL =
            "https://api.groq.com/openai/v1/chat/completions";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public LocalLlmMealPlanAssistant(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${app.groq.api-key:}") String apiKey,
            @Value("${app.groq.model:openai/gpt-oss-20b}") String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
    }

    public NutritionService.MealPlanSuggestion generate(
            NutritionService.AiMealRequest request,
            NutritionProfile profile,
            List<Food> availableFoods) {
        return generate(request, profile, availableFoods, LEGACY_PREPARATION_INSTRUCTIONS_LIMIT);
    }

    private NutritionService.MealPlanSuggestion generate(
            NutritionService.AiMealRequest request,
            NutritionProfile profile,
            List<Food> availableFoods,
            int preparationInstructionsLimit) {
        if (apiKey.isBlank()) {
            return unavailable("Configure GROQ_API_KEY on the backend to enable AI meal-plan generation.");
        }
        if (model.isBlank()) {
            return unavailable("Configure GROQ_MODEL on the backend to select a Groq chat model.");
        }
        if (profile == null || profile.getTargetCalories() == null || profile.getTargetCalories() <= 0
                || profile.getGoal() == null || profile.getGoal().isBlank()) {
            return unavailable("Save a nutrition profile with a goal and positive target calories before generating a plan.");
        }

        List<String> restrictions = request.restrictions() == null
                ? List.of()
                : request.restrictions().stream().filter(Objects::nonNull).map(String::trim)
                        .filter(value -> !value.isBlank()).toList();
        if (restrictions.stream().anyMatch(value -> value.length() > 100)) {
            return unavailable("Each restriction or allergy must be at most 100 characters.");
        }

        String prompt = buildPrompt(profile, availableFoods, request, restrictions,
                preparationInstructionsLimit);
        try {
            JsonNode response = requestPlan(prompt);
            JsonNode content = response == null ? null
                    : response.path("choices").path(0).path("message").path("content");
            if (content == null || !content.isTextual() || content.asText().isBlank()) {
                return unavailable("Groq returned no structured meal-plan content.");
            }
            NutritionService.GeneratedMealPlan plan =
                    objectMapper.readValue(content.asText(), NutritionService.GeneratedMealPlan.class);
            String validationError = validate(plan, restrictions, preparationInstructionsLimit);
            if (validationError != null) {
                return unavailable(validationError);
            }
            plan = withOptionalSuggestions(plan);
            NutritionService.GeneratedMealPlan enriched = enrichNutrition(plan, availableFoods);
            return new NutritionService.MealPlanSuggestion(true, "AI_GENERATED", enriched,
                    "Review all ingredients before use. Nutrition is calculated from your saved food data when every meal ingredient matches; otherwise it remains a model estimate. "
                            + "AI output cannot establish allergy or medical safety.");
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            if (status == 429) {
                return unavailable(rateLimitMessage(exception.getResponseHeaders()));
            }
            if (status == 401 || status == 403) {
                return unavailable("Groq rejected the configured API key or account permissions.");
            }
            if (status == 400 || status == 422) {
                if (isCompletionLimitRejection(exception)) {
                    return unavailable("Groq stopped before completing the meal-plan response because its output "
                            + "token limit was reached. No plan was accepted; please try again.");
                }
                return unavailable("Groq rejected the generation request (HTTP " + status
                        + "). Review GROQ_MODEL availability, model request limits, and structured-output support; "
                        + "no meal plan was accepted.");
            }
            if (status == 404) {
                return unavailable("Groq could not find the configured model (HTTP 404). Check GROQ_MODEL "
                        + "against the models available to your account; no meal plan was accepted.");
            }
            if (status == 413) {
                return unavailable("Groq rejected the request as too large (HTTP 413). Reduce the saved "
                        + "food-library context and try again; no meal plan was accepted.");
            }
            if (status >= 500) {
                return unavailable("Groq is temporarily unavailable (HTTP " + status
                        + "). Please try again later; no meal plan was accepted.");
            }
            return unavailable("Groq returned an unexpected HTTP status (" + status
                    + "); no meal plan was accepted.");
        } catch (ResourceAccessException exception) {
            return unavailable("The Groq request timed out or could not reach the service.");
        } catch (RestClientException exception) {
            return unavailable("The Groq service could not be reached or returned an invalid HTTP response.");
        } catch (JsonProcessingException exception) {
            return unavailable("Groq returned malformed JSON; no plan was accepted.");
        } catch (ResponseStatusException exception) {
            return unavailable("Groq returned nutrition totals outside supported numeric limits.");
        }
    }

    @Async("llmExecutor")
    public CompletableFuture<NutritionService.MealPlanSuggestion> generateAsync(
            NutritionService.AiMealRequest request,
            NutritionProfile profile,
            List<Food> availableFoods) {
        return CompletableFuture.completedFuture(generate(request, profile, availableFoods));
    }

    @Async("llmExecutor")
    public CompletableFuture<NutritionService.MealPlanSuggestion> generatePersistentPlanAsync(
            NutritionService.AiMealRequest request,
            NutritionProfile profile,
            List<Food> availableFoods) {
        return CompletableFuture.completedFuture(generate(request, profile, availableFoods,
                PERSISTENT_PREPARATION_INSTRUCTIONS_LIMIT));
    }

    private NutritionService.MealPlanSuggestion unavailable(String note) {
        return new NutritionService.MealPlanSuggestion(false, "UNAVAILABLE", null, note);
    }

    private JsonNode requestPlan(String prompt) {
        try {
            return postPlanRequest(prompt, true);
        } catch (HttpStatusCodeException exception) {
            if ((exception.getStatusCode().value() != 400 && exception.getStatusCode().value() != 422)
                    || isCompletionLimitRejection(exception)
                    || !isStructuredOutputRejection(exception)) {
                throw exception;
            }
            return postPlanRequest(prompt, false);
        }
    }

    private JsonNode postPlanRequest(String prompt, boolean strictSchema) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> responseFormat = strictSchema
                ? Map.of("type", "json_schema", "json_schema", Map.of(
                        "name", "nutrition_meal_plan",
                        "strict", true,
                        "schema", mealPlanSchema()))
                : Map.of("type", "json_object");
        Map<String, Object> payload = Map.of(
                "model", model,
                "max_completion_tokens", MAX_COMPLETION_TOKENS,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Create a meal plan using only the supplied context. Return one JSON object "
                                        + "matching the requested shape. Create complete, portioned meals rather "
                                        + "than lists of unrelated ingredients. Create recipes even when their "
                                        + "ingredients are not in the saved food library. Use a saved food only "
                                        + "when it is a suitable match; never distort a recipe to force a match. "
                                        + "Include ingredient quantities and preparation steps. Prefer vegetables, "
                                        + "legumes, lean protein, whole grains, baking/grilling/stewing, and modest "
                                        + "oil; limit deep-frying, processed meats, and excess salt. Never promise "
                                        + "medical or allergy safety."),
                        Map.of("role", "user", "content", prompt)),
                "response_format", responseFormat);
        return restTemplate.postForObject(
                CHAT_COMPLETIONS_URL, new HttpEntity<>(payload, headers), JsonNode.class);
    }

    private boolean isStructuredOutputRejection(HttpStatusCodeException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            String diagnostic = String.join(" ",
                    error.path("message").asText(""),
                    error.path("type").asText(""),
                    error.path("code").asText(""))
                    .toLowerCase(Locale.ROOT);
            return diagnostic.contains("json_schema")
                    || diagnostic.contains("response_format")
                    || diagnostic.contains("structured output")
                    || diagnostic.contains("schema");
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    private boolean isCompletionLimitRejection(HttpStatusCodeException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String diagnostic = responseBody.toLowerCase(Locale.ROOT);
        return diagnostic.contains("max completion tokens")
                || diagnostic.contains("max_completion_tokens")
                || diagnostic.contains("completion token limit");
    }

    private String rateLimitMessage(HttpHeaders headers) {
        Long retrySeconds = retryAfterSeconds(headers);
        if (retrySeconds != null) {
            return "Groq rate limit reached. Wait about " + retrySeconds
                    + " seconds before trying again. If this continues, check your Groq account usage and model limits.";
        }
        return "Groq rate limit reached. Wait before trying again. If this continues, check your Groq account usage and model limits.";
    }

    private Long retryAfterSeconds(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return seconds > 0 && seconds <= 86_400 ? seconds : null;
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant();
                long seconds = Duration.between(Instant.now(), retryAt).getSeconds();
                return seconds > 0 && seconds <= 86_400 ? seconds : null;
            } catch (DateTimeParseException exception) {
                return null;
            }
        }
    }

    private String buildPrompt(NutritionProfile profile, List<Food> foods,
                               NutritionService.AiMealRequest request, List<String> restrictions,
                               int preparationInstructionsLimit) {
        List<Map<String, Object>> inventory = foods.stream()
                .map(food -> Map.<String, Object>of(
                        "name", food.getName(),
                        "servingSizeGrams", food.getServingSizeGrams(),
                        "calories", food.getCalories(),
                        "proteinGrams", food.getProteinGrams(),
                        "carbohydrateGrams", food.getCarbohydrateGrams(),
                        "fatGrams", food.getFatGrams(),
                        "fiberGrams", food.getFiberGrams(),
                        "sodiumMg", food.getSodiumMg()))
                .toList();
        return """
                Return only one JSON object matching this exact shape:
                {"planName":"string","goal":"string","meals":[{"name":"string","mealType":"BREAKFAST|LUNCH|DINNER|SNACK|PRE_WORKOUT|POST_WORKOUT","servingSizeGrams":350,"foods":[{"name":"string","servings":1.0}],"preparationInstructions":"string","estimatedNutrition":{"calories":0,"proteinGrams":0,"carbohydrateGrams":0,"fatGrams":0,"fiberGrams":0,"sodiumMg":0}}]}
                Provide at least one meal and food per meal. servingSizeGrams must be an integer from 1 to 2500.
                servings and all nutrition values must be finite nonnegative numbers (servings must be greater than zero).
                Return exactly three meals: breakfast, lunch, and dinner. Each meal must have 3 to 6 foods and
                concise preparation instructions no longer than 350 characters, even when the allowed limit is larger.
                Keep the complete response concise enough to fit within the output token budget.
                Include every required meal and food field with a nonblank value;
                meal names must be 1 to 120 characters, food names 1 to 150 characters, and preparationInstructions
                must be 1 to %d characters. Do not use null or omit required fields.
                Only use supported meal types. Keep all explicitly restricted/allergen ingredients out of meal names, foods,
                and instructions. Never state that a plan is medically or allergy safe.
                Treat nutrition values as estimates, not a guarantee that a meal is healthy or suitable for a medical condition.
                Create complete recipes whether or not their ingredients exist in the saved food library.
                List the recipe's actual ingredients as foods with practical serving quantities; use exact saved food names only
                when a saved item genuinely matches an ingredient or prepared meal. Never change or omit recipe ingredients just to match the library.
                Estimate nutrition per complete meal serving and include its approximate finished serving weight in grams.
                Include calories, protein, carbohydrates, fat, fiber, and sodium per serving. Put brief ingredient
                quantities and cooking steps in preparationInstructions.
                Prefer vegetables, legumes, lean protein, whole grains, baking/grilling/stewing, and modest oil; limit deep-frying,
                processed meats, pastries, and excess salt. Keep the daily plan reasonably close to the saved calorie target without
                extreme restriction. Do not claim unavailable profile information.
                Profile context: goal=%s; saved target calories per day=%d; dietary preference=%s.
                Explicit restrictions/allergies to exclude: %s
                Optional saved food library (nutrition values per serving; use only when genuinely suitable): %s
                Saved disliked foods: %s
                Preferred or available ingredients: %s
                Budget preference: %s
                Maximum preparation time in minutes: %s
                Other saved cooking preference: %s
                Do not invent preferences beyond this saved context.
                """.formatted(
                preparationInstructionsLimit,
                profile.getGoal(),
                profile.getTargetCalories(),
                profile.getDietaryPreference(),
                restrictions,
                inventory,
                request.dislikedFoods() == null ? List.of() : request.dislikedFoods(),
                request.preferredIngredients() == null ? List.of() : request.preferredIngredients(),
                request.budgetPreference() == null ? "not set" : request.budgetPreference(),
                request.maxPreparationMinutes() == null ? "not set" : request.maxPreparationMinutes(),
                request.cookingPreference() == null ? "not set" : request.cookingPreference());
    }

    private Map<String, Object> mealPlanSchema() {
        Map<String, Object> nutritionProperties = new LinkedHashMap<>();
        nutritionProperties.put("calories", nonnegativeNumberSchema());
        nutritionProperties.put("proteinGrams", nonnegativeNumberSchema());
        nutritionProperties.put("carbohydrateGrams", nonnegativeNumberSchema());
        nutritionProperties.put("fatGrams", nonnegativeNumberSchema());
        nutritionProperties.put("fiberGrams", nonnegativeNumberSchema());
        nutritionProperties.put("sodiumMg", nonnegativeNumberSchema());

        Map<String, Object> foodProperties = new LinkedHashMap<>();
        foodProperties.put("name", stringSchema());
        foodProperties.put("servings", Map.of("type", "number"));

        Map<String, Object> mealProperties = new LinkedHashMap<>();
        mealProperties.put("name", stringSchema());
        mealProperties.put("mealType", Map.of("type", "string", "enum",
                List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK", "PRE_WORKOUT", "POST_WORKOUT")));
        mealProperties.put("servingSizeGrams", Map.of("type", "integer"));
        mealProperties.put("foods", Map.of("type", "array", "items",
                objectSchema(foodProperties, "name", "servings")));
        mealProperties.put("preparationInstructions", stringSchema());
        mealProperties.put("estimatedNutrition", objectSchema(nutritionProperties,
                "calories", "proteinGrams", "carbohydrateGrams", "fatGrams", "fiberGrams", "sodiumMg"));

        Map<String, Object> planProperties = new LinkedHashMap<>();
        planProperties.put("planName", stringSchema());
        planProperties.put("goal", stringSchema());
        planProperties.put("meals", Map.of("type", "array", "items",
                objectSchema(mealProperties, "name", "mealType", "servingSizeGrams", "foods",
                        "preparationInstructions", "estimatedNutrition")));
        return objectSchema(planProperties, "planName", "goal", "meals");
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, String... required) {
        return Map.of("type", "object", "properties", properties,
                "required", List.of(required), "additionalProperties", false);
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> nonnegativeNumberSchema() {
        return Map.of("type", "number");
    }

    private String validate(NutritionService.GeneratedMealPlan plan, List<String> restrictions,
                            int preparationInstructionsLimit) {
        if (plan == null || blank(plan.planName()) || blank(plan.goal()) || plan.meals() == null
                || plan.meals().isEmpty() || plan.meals().size() > 12) {
            return "The generated response is missing required plan fields or contains too many meals.";
        }
        for (NutritionService.GeneratedMeal meal : plan.meals()) {
            if (meal == null || blank(meal.name()) || meal.name().length() > 120
                    || meal.mealType() == null) {
                return "The generated response contains a meal with a missing or invalid name or meal type.";
            }
            if (meal.servingSizeGrams() == null || meal.servingSizeGrams() <= 0
                    || meal.servingSizeGrams() > 2500) {
                return "The generated response contains a meal with an invalid serving size; use 1 to 2,500 grams.";
            }
            if (meal.foods() == null || meal.foods().isEmpty() || meal.foods().size() > 30) {
                return "Each generated meal must contain between 1 and 30 food items.";
            }
            if (blank(meal.preparationInstructions())
                    || meal.preparationInstructions().length() > preparationInstructionsLimit) {
                return "Each generated meal requires preparation instructions of 1 to "
                        + preparationInstructionsLimit + " characters.";
            }
            if (!validNutrition(meal.estimatedNutrition())) {
                return "Each generated meal requires finite, nonnegative values for all six nutrition estimates.";
            }
            for (NutritionService.GeneratedFood food : meal.foods()) {
                if (food == null || blank(food.name()) || food.name().length() > 150
                        || food.servings() == null || !Double.isFinite(food.servings()) || food.servings() <= 0) {
                    return "The generated response contains a food with an invalid name or serving quantity.";
                }
            }
        }
        List<String> shoppingList = plan.shoppingList() == null ? List.of() : plan.shoppingList();
        List<String> alternatives = plan.alternatives() == null ? List.of() : plan.alternatives();
        if (plan.planName().length() > 100 || plan.goal().length() > 100
                || shoppingList.size() > 50 || alternatives.size() > 50
                || shoppingList.stream().anyMatch(value -> blank(value) || value.length() > 200)
                || alternatives.stream().anyMatch(value -> blank(value) || value.length() > 200)) {
            return "The generated response contains invalid or overly long text fields.";
        }
        Set<String> excluded = restrictions.stream()
                .flatMap(value -> Arrays.stream(value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", " ").trim().split("\\s+")))
                .filter(value -> !Set.of("allergy", "allergic", "intolerance", "intolerant",
                        "avoid", "avoidance", "free", "to", "and").contains(value))
                .collect(Collectors.toSet());
        if (containsExcluded(plan.planName(), excluded) || containsExcluded(plan.goal(), excluded)
                || shoppingList.stream().anyMatch(value -> containsExcluded(value, excluded))
                || alternatives.stream().anyMatch(value -> containsExcluded(value, excluded))) {
            return "The generated response included an explicitly excluded ingredient or restriction; the plan was rejected.";
        }
        for (NutritionService.GeneratedMeal meal : plan.meals()) {
            if (containsExcluded(meal.name(), excluded) || containsExcluded(meal.preparationInstructions(), excluded)
                    || meal.foods().stream().anyMatch(food -> containsExcluded(food.name(), excluded))) {
                return "The generated response included an explicitly excluded ingredient or restriction; the plan was rejected.";
            }
        }
        return null;
    }

    private NutritionService.GeneratedMealPlan withOptionalSuggestions(
            NutritionService.GeneratedMealPlan plan) {
        return new NutritionService.GeneratedMealPlan(plan.planName(), plan.goal(), plan.meals(),
                plan.dailyNutrition(), plan.shoppingList() == null ? List.of() : plan.shoppingList(),
                plan.alternatives() == null ? List.of() : plan.alternatives());
    }

    private NutritionService.GeneratedMealPlan enrichNutrition(
            NutritionService.GeneratedMealPlan plan, List<Food> availableFoods) {
        List<NutritionService.GeneratedMeal> meals = plan.meals().stream().map(meal -> {
            List<NutritionService.GeneratedFood> foods = meal.foods().stream().map(item -> {
                Food match = availableFoods.stream()
                        .filter(food -> food.getName().equalsIgnoreCase(item.name().trim()))
                        .findFirst().orElse(null);
                return new NutritionService.GeneratedFood(item.name().trim(), item.servings(),
                        match == null ? null : match.getId());
            }).toList();
            boolean allMatched = foods.stream().allMatch(food -> food.foodId() != null);
            NutritionService.NutritionEstimate nutrition = meal.estimatedNutrition();
            if (allMatched) {
                nutrition = new NutritionService.NutritionEstimate(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                for (NutritionService.GeneratedFood item : foods) {
                    Food savedFood = availableFoods.stream()
                            .filter(food -> food.getId().equals(item.foodId()))
                            .findFirst().orElseThrow();
                    NutritionService.NutritionEstimate perServing = new NutritionService.NutritionEstimate(
                            savedFood.getCalories(), savedFood.getProteinGrams(),
                            savedFood.getCarbohydrateGrams(), savedFood.getFatGrams(),
                            savedFood.getFiberGrams(), savedFood.getSodiumMg());
                    nutrition = nutrition.add(perServing, item.servings());
                }
            }
            return new NutritionService.GeneratedMeal(meal.name().trim(), meal.mealType(),
                    meal.servingSizeGrams(), foods,
                    meal.preparationInstructions().trim(), nutrition,
                    allMatched ? "SAVED_FOOD_DATA" : "MODEL_ESTIMATE");
        }).toList();
        NutritionService.NutritionEstimate daily = meals.stream()
                .map(NutritionService.GeneratedMeal::estimatedNutrition)
                .reduce(new NutritionService.NutritionEstimate(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                        (total, item) -> total.add(item, 1));
        return new NutritionService.GeneratedMealPlan(plan.planName().trim(), plan.goal().trim(),
                meals, daily, plan.shoppingList(), plan.alternatives());
    }

    private boolean validNutrition(NutritionService.NutritionEstimate nutrition) {
        return nutrition != null && nutrition.calories() != null && nutrition.proteinGrams() != null
                && nutrition.carbohydrateGrams() != null && nutrition.fatGrams() != null
                && nutrition.fiberGrams() != null && nutrition.sodiumMg() != null
                && Double.isFinite(nutrition.calories()) && nutrition.calories() >= 0
                && Double.isFinite(nutrition.proteinGrams()) && nutrition.proteinGrams() >= 0
                && Double.isFinite(nutrition.carbohydrateGrams()) && nutrition.carbohydrateGrams() >= 0
                && Double.isFinite(nutrition.fatGrams()) && nutrition.fatGrams() >= 0
                && Double.isFinite(nutrition.fiberGrams()) && nutrition.fiberGrams() >= 0
                && Double.isFinite(nutrition.sodiumMg()) && nutrition.sodiumMg() >= 0;
    }

    private boolean containsExcluded(String text, Set<String> exclusions) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return exclusions.stream().anyMatch(exclusion -> normalized.contains(exclusion));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
