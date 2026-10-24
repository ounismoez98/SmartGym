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

        String prompt = buildPrompt(profile, availableFoods, restrictions);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "Create a meal plan using only the supplied context. Return data matching the JSON schema. "
                                            + "Never claim medical or allergy safety."),
                            Map.of("role", "user", "content", prompt)),
                    "response_format", Map.of(
                            "type", "json_schema",
                            "json_schema", Map.of(
                                    "name", "nutrition_meal_plan",
                                    "strict", true,
                                    "schema", mealPlanSchema())));
            JsonNode response = restTemplate.postForObject(
                    CHAT_COMPLETIONS_URL, new HttpEntity<>(payload, headers), JsonNode.class);
            JsonNode content = response == null ? null
                    : response.path("choices").path(0).path("message").path("content");
            if (content == null || !content.isTextual() || content.asText().isBlank()) {
                return unavailable("Groq returned no structured meal-plan content.");
            }
            NutritionService.GeneratedMealPlan plan =
                    objectMapper.readValue(content.asText(), NutritionService.GeneratedMealPlan.class);
            String validationError = validate(plan, restrictions);
            if (validationError != null) {
                return unavailable(validationError);
            }
            NutritionService.GeneratedMealPlan enriched = enrichNutrition(plan, availableFoods);
            return new NutritionService.MealPlanSuggestion(true, "AI_GENERATED", enriched,
                    "Review all ingredients before use. Nutrition is calculated from your saved food data when every meal ingredient matches; otherwise it remains a model estimate. "
                            + "AI output cannot establish allergy or medical safety.");
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 429) {
                return unavailable("Groq rate limit reached. Please try again later.");
            }
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                return unavailable("Groq rejected the configured API key or account permissions.");
            }
            return unavailable("Groq returned an HTTP error; no meal plan was accepted.");
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

    private NutritionService.MealPlanSuggestion unavailable(String note) {
        return new NutritionService.MealPlanSuggestion(false, "UNAVAILABLE", null, note);
    }

    private String buildPrompt(NutritionProfile profile, List<Food> foods, List<String> restrictions) {
        List<Map<String, Object>> inventory = foods.stream()
                .map(food -> Map.<String, Object>of(
                        "name", food.getName(),
                        "servingSizeGrams", food.getServingSizeGrams(),
                        "calories", food.getCalories(),
                        "proteinGrams", food.getProteinGrams(),
                        "carbohydrateGrams", food.getCarbohydrateGrams(),
                        "fatGrams", food.getFatGrams()))
                .toList();
        return """
                Return only one JSON object matching this exact shape:
                {"planName":"string","goal":"string","meals":[{"name":"string","mealType":"BREAKFAST|LUNCH|DINNER|SNACK|PRE_WORKOUT|POST_WORKOUT","foods":[{"name":"string","servings":1.0}],"preparationInstructions":"string","estimatedNutrition":{"calories":0,"proteinGrams":0,"carbohydrateGrams":0,"fatGrams":0}}],"shoppingList":["string"],"alternatives":["string"]}
                Provide at least one meal and food per meal. servings and all nutrition values must be finite nonnegative numbers (servings must be greater than zero). Only use supported meal types. Keep all explicitly restricted/allergen ingredients out of meal names, foods, instructions, shopping list, alternatives, and substitutions. Never state that a plan is medically or allergy safe.
                Treat nutrition values as estimates. Prefer the provided food library; use exact food names where applicable. Do not claim unavailable profile information.
                Profile context: goal=%s; saved target calories per day=%d; dietary preference=%s.
                Explicit restrictions/allergies to exclude: %s
                Available saved foods (nutrition values per serving): %s
                Other preferences such as budget, cuisine, number of meals, cooking facilities, and preparation preferences were not collected; do not invent them.
                """.formatted(
                profile.getGoal(),
                profile.getTargetCalories(),
                profile.getDietaryPreference(),
                restrictions,
                inventory);
    }

    private Map<String, Object> mealPlanSchema() {
        Map<String, Object> nutritionProperties = new LinkedHashMap<>();
        nutritionProperties.put("calories", nonnegativeNumberSchema());
        nutritionProperties.put("proteinGrams", nonnegativeNumberSchema());
        nutritionProperties.put("carbohydrateGrams", nonnegativeNumberSchema());
        nutritionProperties.put("fatGrams", nonnegativeNumberSchema());

        Map<String, Object> foodProperties = new LinkedHashMap<>();
        foodProperties.put("name", stringSchema());
        foodProperties.put("servings", Map.of("type", "number"));

        Map<String, Object> mealProperties = new LinkedHashMap<>();
        mealProperties.put("name", stringSchema());
        mealProperties.put("mealType", Map.of("type", "string", "enum",
                List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK", "PRE_WORKOUT", "POST_WORKOUT")));
        mealProperties.put("foods", Map.of("type", "array", "items",
                objectSchema(foodProperties, "name", "servings")));
        mealProperties.put("preparationInstructions", stringSchema());
        mealProperties.put("estimatedNutrition", objectSchema(nutritionProperties,
                "calories", "proteinGrams", "carbohydrateGrams", "fatGrams"));

        Map<String, Object> planProperties = new LinkedHashMap<>();
        planProperties.put("planName", stringSchema());
        planProperties.put("goal", stringSchema());
        planProperties.put("meals", Map.of("type", "array", "items",
                objectSchema(mealProperties, "name", "mealType", "foods",
                        "preparationInstructions", "estimatedNutrition")));
        planProperties.put("shoppingList", stringArraySchema());
        planProperties.put("alternatives", stringArraySchema());
        return objectSchema(planProperties, "planName", "goal", "meals", "shoppingList", "alternatives");
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

    private Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", stringSchema());
    }

    private String validate(NutritionService.GeneratedMealPlan plan, List<String> restrictions) {
        if (plan == null || blank(plan.planName()) || blank(plan.goal()) || plan.meals() == null
                || plan.meals().isEmpty() || plan.meals().size() > 12
                || plan.shoppingList() == null || plan.alternatives() == null) {
            return "The generated response is missing required plan fields or contains too many meals.";
        }
        for (NutritionService.GeneratedMeal meal : plan.meals()) {
            if (meal == null || blank(meal.name()) || meal.mealType() == null || meal.foods() == null
                    || meal.foods().isEmpty() || blank(meal.preparationInstructions())
                    || meal.name().length() > 60 || meal.preparationInstructions().length() > 350
                    || meal.foods().size() > 30
                    || !validNutrition(meal.estimatedNutrition())) {
                return "The generated response contains a meal with missing or invalid required fields.";
            }
            for (NutritionService.GeneratedFood food : meal.foods()) {
                if (food == null || blank(food.name()) || food.name().length() > 150
                        || food.servings() == null || !Double.isFinite(food.servings()) || food.servings() <= 0) {
                    return "The generated response contains a food with an invalid name or serving quantity.";
                }
            }
        }
        if (plan.planName().length() > 100 || plan.goal().length() > 100
                || plan.shoppingList().size() > 50 || plan.alternatives().size() > 50
                || plan.shoppingList().stream().anyMatch(value -> blank(value) || value.length() > 200)
                || plan.alternatives().stream().anyMatch(value -> blank(value) || value.length() > 200)) {
            return "The generated response contains invalid or overly long text fields.";
        }
        Set<String> excluded = restrictions.stream()
                .flatMap(value -> Arrays.stream(value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", " ").trim().split("\\s+")))
                .filter(value -> !Set.of("allergy", "allergic", "intolerance", "intolerant",
                        "avoid", "avoidance", "free", "to", "and").contains(value))
                .collect(Collectors.toSet());
        if (containsExcluded(plan.planName(), excluded) || containsExcluded(plan.goal(), excluded)
                || plan.shoppingList().stream().anyMatch(value -> containsExcluded(value, excluded))
                || plan.alternatives().stream().anyMatch(value -> containsExcluded(value, excluded))) {
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
                nutrition = new NutritionService.NutritionEstimate(0.0, 0.0, 0.0, 0.0);
                for (NutritionService.GeneratedFood item : foods) {
                    Food savedFood = availableFoods.stream()
                            .filter(food -> food.getId().equals(item.foodId()))
                            .findFirst().orElseThrow();
                    NutritionService.NutritionEstimate perServing = new NutritionService.NutritionEstimate(
                            savedFood.getCalories(), savedFood.getProteinGrams(),
                            savedFood.getCarbohydrateGrams(), savedFood.getFatGrams());
                    nutrition = nutrition.add(perServing, item.servings());
                }
            }
            return new NutritionService.GeneratedMeal(meal.name().trim(), meal.mealType(), foods,
                    meal.preparationInstructions().trim(), nutrition,
                    allMatched ? "SAVED_FOOD_DATA" : "MODEL_ESTIMATE");
        }).toList();
        NutritionService.NutritionEstimate daily = meals.stream()
                .map(NutritionService.GeneratedMeal::estimatedNutrition)
                .reduce(new NutritionService.NutritionEstimate(0.0, 0.0, 0.0, 0.0),
                        (total, item) -> total.add(item, 1));
        return new NutritionService.GeneratedMealPlan(plan.planName().trim(), plan.goal().trim(),
                meals, daily, plan.shoppingList(), plan.alternatives());
    }

    private boolean validNutrition(NutritionService.NutritionEstimate nutrition) {
        return nutrition != null && nutrition.calories() != null && nutrition.proteinGrams() != null
                && nutrition.carbohydrateGrams() != null && nutrition.fatGrams() != null
                && Double.isFinite(nutrition.calories()) && nutrition.calories() >= 0
                && Double.isFinite(nutrition.proteinGrams()) && nutrition.proteinGrams() >= 0
                && Double.isFinite(nutrition.carbohydrateGrams()) && nutrition.carbohydrateGrams() >= 0
                && Double.isFinite(nutrition.fatGrams()) && nutrition.fatGrams() >= 0;
    }

    private boolean containsExcluded(String text, Set<String> exclusions) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return exclusions.stream().anyMatch(exclusion -> normalized.contains(exclusion));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
