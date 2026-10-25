package com.smartgym.nutrition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.task.TaskRejectedException;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {
    private static final Logger logger = LoggerFactory.getLogger(NutritionController.class);
    private final NutritionService nutritionService;

    public NutritionController(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleNutritionError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(exception.getReason()));
    }

    @PostMapping("/foods")
    public FoodResponse createFood(@AuthenticationPrincipal Jwt jwt,
                                  @RequestBody NutritionService.CreateFoodRequest request) {
        return toResponse(nutritionService.createFood(jwt.getSubject(), request));
    }

    @GetMapping("/foods")
    public List<FoodResponse> listFoods(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.listFoods(jwt.getSubject()).stream().map(this::toResponse).toList();
    }

    @PutMapping("/foods/{foodId}")
    public FoodResponse updateFood(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable Long foodId,
                                  @RequestBody NutritionService.CreateFoodRequest request) {
        return toResponse(nutritionService.updateFood(jwt.getSubject(), foodId, request));
    }

    @DeleteMapping("/foods/{foodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFood(@AuthenticationPrincipal Jwt jwt, @PathVariable Long foodId) {
        nutritionService.deleteFood(jwt.getSubject(), foodId);
    }

    @PostMapping("/meal-plans")
    public MealPlanResponse createMealPlan(@AuthenticationPrincipal Jwt jwt,
                                          @RequestBody NutritionService.CreateMealPlanRequest request) {
        return toResponse(nutritionService.createMealPlan(jwt.getSubject(), request));
    }

    @GetMapping("/meal-plans")
    public List<MealPlanResponse> listMealPlans(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.listMealPlans(jwt.getSubject()).stream().map(this::toResponse).toList();
    }

    @GetMapping("/plans")
    public List<NutritionPlanApi.PlanView> listPersistentPlans(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.listPersistentPlans(jwt.getSubject());
    }

    @GetMapping("/plans/{planId}")
    public NutritionPlanApi.PlanView getPersistentPlan(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        return nutritionService.getPersistentPlan(jwt.getSubject(), planId);
    }

    @PutMapping("/meal-plans/{mealPlanId}")
    public MealPlanResponse updateMealPlan(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable Long mealPlanId,
                                          @RequestBody NutritionService.CreateMealPlanRequest request) {
        return toResponse(nutritionService.updateMealPlan(jwt.getSubject(), mealPlanId, request));
    }

    @DeleteMapping("/meal-plans/{mealPlanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMealPlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long mealPlanId) {
        nutritionService.deleteMealPlan(jwt.getSubject(), mealPlanId);
    }

    @PostMapping("/food-journal")
    public FoodJournalResponse createJournalEntry(@AuthenticationPrincipal Jwt jwt,
                                                @RequestBody NutritionService.CreateFoodJournalRequest request) {
        return toResponse(nutritionService.createFoodJournal(jwt.getSubject(), request));
    }

    @GetMapping("/food-journal")
    public List<FoodJournalResponse> listJournalEntries(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.listFoodJournal(jwt.getSubject()).stream().map(this::toResponse).toList();
    }

    @PutMapping("/food-journal/{entryId}")
    public FoodJournalResponse updateJournalEntry(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable Long entryId,
                                                 @RequestBody NutritionService.CreateFoodJournalRequest request) {
        return toResponse(nutritionService.updateFoodJournal(jwt.getSubject(), entryId, request));
    }

    @DeleteMapping("/food-journal/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJournalEntry(@AuthenticationPrincipal Jwt jwt, @PathVariable Long entryId) {
        nutritionService.deleteFoodJournal(jwt.getSubject(), entryId);
    }

    @PostMapping("/body-measurements")
    public BodyMeasurementResponse createBodyMeasurement(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestBody NutritionService.CreateBodyMeasurementRequest request) {
        return toResponse(nutritionService.createBodyMeasurement(jwt.getSubject(), request));
    }

    @GetMapping("/body-measurements")
    public List<BodyMeasurementResponse> listBodyMeasurements(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.listBodyMeasurements(jwt.getSubject()).stream().map(this::toResponse).toList();
    }

    @PutMapping("/body-measurements/{measurementId}")
    public BodyMeasurementResponse updateBodyMeasurement(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable Long measurementId,
                                                        @RequestBody NutritionService.CreateBodyMeasurementRequest request) {
        return toResponse(nutritionService.updateBodyMeasurement(jwt.getSubject(), measurementId, request));
    }

    @DeleteMapping("/body-measurements/{measurementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBodyMeasurement(@AuthenticationPrincipal Jwt jwt, @PathVariable Long measurementId) {
        nutritionService.deleteBodyMeasurement(jwt.getSubject(), measurementId);
    }

    @PostMapping("/profiles")
    public NutritionProfileResponse saveProfile(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody NutritionService.NutritionProfileRequest request) {
        return toResponse(nutritionService.createOrUpdateProfile(jwt.getSubject(), request));
    }

    @GetMapping("/profiles")
    public NutritionProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return toResponse(nutritionService.getCurrentProfile(jwt.getSubject()));
    }

    @GetMapping("/calculations/goal-timeline")
    public NutritionService.GoalTimelineResult calculateGoalTimeline(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.calculateGoalTimeline(jwt.getSubject());
    }

    @PostMapping("/hydration")
    public NutritionService.HydrationView addHydrationLog(@AuthenticationPrincipal Jwt jwt,
            @RequestBody NutritionService.HydrationRequest request) {
        return nutritionService.addHydrationLog(jwt.getSubject(), request);
    }

    @GetMapping("/hydration")
    public NutritionService.HydrationView getHydrationDay(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate date) {
        LocalDate requestedDate = date == null ? LocalDate.now(java.time.ZoneOffset.UTC) : date;
        return nutritionService.getHydrationDay(jwt.getSubject(), requestedDate);
    }

    @GetMapping("/hydration/history")
    public List<NutritionService.HydrationLogView> getHydrationHistory(@AuthenticationPrincipal Jwt jwt,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return nutritionService.getHydrationHistory(jwt.getSubject(), from, to);
    }

    @PutMapping("/hydration/{logId}")
    public NutritionService.HydrationView correctHydrationLog(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long logId, @RequestBody NutritionService.HydrationRequest request) {
        return nutritionService.correctHydrationLog(jwt.getSubject(), logId, request);
    }

    @DeleteMapping("/hydration/{logId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHydrationLog(@AuthenticationPrincipal Jwt jwt, @PathVariable Long logId) {
        nutritionService.deleteHydrationLog(jwt.getSubject(), logId);
    }

    @GetMapping("/calculations")
    public NutritionCalculationResponse calculateNutrition(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam(required = false) List<Long> foodIds,
                                                         @RequestParam(required = false) List<Double> servings) {
        NutritionService.NutritionCalculationResult result =
                nutritionService.calculateNutrition(jwt.getSubject(), foodIds, servings);
        return new NutritionCalculationResponse(result.calories(), result.proteinGrams(),
                result.carbohydrateGrams(), result.fatGrams(), result.fiberGrams(), result.sodiumMg());
    }

    @GetMapping("/calculations/bmi")
    public NutritionService.BmiResult calculateBmi(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.calculateBmi(jwt.getSubject());
    }

    @GetMapping("/calculations/macros")
    public NutritionService.MacroTargetResult calculateMacroTargets(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.calculateMacroTargets(jwt.getSubject());
    }

    @GetMapping("/calculations/energy")
    public NutritionService.EnergyEstimateResult calculateEnergyEstimate(@AuthenticationPrincipal Jwt jwt) {
        return nutritionService.calculateEnergyEstimate(jwt.getSubject());
    }

    @GetMapping("/food-journal/daily")
    public DailyNutritionResponse calculateDailyNutrition(@AuthenticationPrincipal Jwt jwt,
                                                          @RequestParam LocalDate date) {
        NutritionService.DailyNutritionResult result =
                nutritionService.calculateDailyNutrition(jwt.getSubject(), date);
        return new DailyNutritionResponse(result.date(), toResponse(result.totals()),
                result.meals().stream()
                        .map(meal -> new MealNutritionResponse(meal.mealType(), toResponse(meal.totals())))
                        .toList());
    }

    @PostMapping("/assistant/plan")
    public CompletableFuture<ResponseEntity<AiPlanResponse>> generatePlan(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody NutritionService.AiMealRequest request) {
        try {
            return nutritionService.generateMealPlanSuggestion(jwt.getSubject(), request)
                    .thenApply(suggestion -> ResponseEntity.ok(toAiPlanResponse(suggestion)))
                    .exceptionally(exception -> {
                        logger.warn("Nutrition meal-plan generation failed.", exception);
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(unavailableAiPlan("Meal-plan generation failed. No plan was accepted."));
                    });
        } catch (TaskRejectedException exception) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(unavailableAiPlan("The meal-plan assistant is busy. Please try again shortly.")));
        }
    }

    @PostMapping("/assistant/plans/draft")
        public CompletableFuture<ResponseEntity<Object>> generatePersistentPlanDraft(
                @AuthenticationPrincipal Jwt jwt,
                @RequestBody NutritionPlanApi.GenerateRequest request) {
            try {
                return nutritionService.generatePersistentPlanDraft(jwt.getSubject(), request)
                        .thenApply(value -> ResponseEntity.<Object>ok(value))
                        .exceptionally(this::asyncError);
            } catch (TaskRejectedException exception) {
                return CompletableFuture.completedFuture(ResponseEntity.<Object>status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new NutritionPlanApi.DraftResponse(false, "UNAVAILABLE",
                                "The meal-plan assistant is busy. Please try again shortly.",
                                null, request == null ? null : request.planType(),
                                request == null ? null : request.startDate(), null, null, null, List.of())));
            }
        }

        @PostMapping("/plans")
        public NutritionPlanApi.PlanView savePersistentPlan(
                @AuthenticationPrincipal Jwt jwt,
                @RequestBody NutritionPlanApi.SaveRequest request) {
            return nutritionService.savePersistentPlan(jwt.getSubject(), request);
        }

        @PostMapping("/plans/{planId}/days/{date}/regenerate")
        public CompletableFuture<ResponseEntity<Object>> regeneratePlanDay(
                @AuthenticationPrincipal Jwt jwt, @PathVariable Long planId, @PathVariable LocalDate date,
                @RequestBody NutritionPlanApi.VersionRequest request) {
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The expected plan version is required.");
            }
            try {
                return nutritionService.regeneratePlanDay(jwt.getSubject(), planId, date, request.expectedVersion())
                        .thenApply(value -> ResponseEntity.<Object>ok(value))
                        .exceptionally(this::asyncError);
            } catch (TaskRejectedException exception) {
                return CompletableFuture.completedFuture(ResponseEntity.<Object>status(HttpStatus.SERVICE_UNAVAILABLE).build());
            }
        }

        @PostMapping("/plans/{planId}/meals/{mealId}/replace")
        public CompletableFuture<ResponseEntity<Object>> replacePlanMeal(
                @AuthenticationPrincipal Jwt jwt, @PathVariable Long planId, @PathVariable Long mealId,
                @RequestBody NutritionPlanApi.VersionRequest request) {
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The expected plan version is required.");
            }
            try {
                return nutritionService.replacePlanMeal(jwt.getSubject(), planId, mealId, request.expectedVersion())
                        .thenApply(value -> ResponseEntity.<Object>ok(value))
                        .exceptionally(this::asyncError);
            } catch (TaskRejectedException exception) {
                return CompletableFuture.completedFuture(ResponseEntity.<Object>status(HttpStatus.SERVICE_UNAVAILABLE).build());
            }
        }

        private ResponseEntity<Object> asyncError(Throwable throwable) {
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause() : throwable;
            if (cause instanceof ResponseStatusException responseStatusException) {
                return ResponseEntity.status(responseStatusException.getStatusCode())
                        .<Object>body(new ApiError(responseStatusException.getReason()));
            }
            logger.warn("Nutrition plan operation failed.", cause);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private AiPlanResponse toAiPlanResponse(NutritionService.MealPlanSuggestion suggestion) {
        return new AiPlanResponse(suggestion.available(), suggestion.status(), suggestion.plan(),
                suggestion.note(), suggestion.generationContextId());
    }

    private AiPlanResponse unavailableAiPlan(String note) {
        return new AiPlanResponse(false, "UNAVAILABLE", null, note, null);
    }

    @PostMapping("/assistant/plan/save")
    public List<MealPlanResponse> saveGeneratedPlan(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody NutritionService.SaveGeneratedMealPlanRequest request) {
        return nutritionService.saveGeneratedMealPlan(jwt.getSubject(), request).stream()
                .map(this::toResponse).toList();
    }

    private FoodResponse toResponse(Food food) {
        return new FoodResponse(food.getId(), food.getName(), food.getBrand(), food.getServingSizeGrams(),
                food.getCalories(), food.getProteinGrams(), food.getCarbohydrateGrams(),
                food.getFatGrams(), food.getFiberGrams(), food.getSodiumMg());
    }

    private MealPlanResponse toResponse(MealPlan mealPlan) {
        return new MealPlanResponse(mealPlan.getId(), mealPlan.getName(), mealPlan.getDescription(),
                mealPlan.getMealType(), mealPlan.getTargetCalories(),
                mealPlan.getItems().stream().map(item -> new MealPlanItemResponse(item.getFoodId(), item.getServings(), item.getFoodName())).toList());
    }

    private FoodJournalResponse toResponse(FoodJournal foodJournal) {
        return new FoodJournalResponse(foodJournal.getId(), foodJournal.getFoodId(), foodJournal.getMealType(),
                foodJournal.getEntryDate(), foodJournal.getServings(), foodJournal.getNotes());
    }

    private BodyMeasurementResponse toResponse(BodyMeasurement bodyMeasurement) {
        return new BodyMeasurementResponse(bodyMeasurement.getId(), bodyMeasurement.getMeasurementDate(),
                bodyMeasurement.getWeightKg(), bodyMeasurement.getWaistCm(), bodyMeasurement.getHipCm(),
                bodyMeasurement.getChestCm(), bodyMeasurement.getArmCm(), bodyMeasurement.getThighCm(),
                bodyMeasurement.getNotes());
    }

    private NutritionProfileResponse toResponse(NutritionProfile nutritionProfile) {
        return new NutritionProfileResponse(nutritionProfile.getId(), nutritionProfile.getProfileName(),
                nutritionProfile.getGoal(), nutritionProfile.getDietaryPreference(),
                nutritionProfile.getActivityLevel(), nutritionProfile.getAge(),
                nutritionProfile.getHeightCm(), nutritionProfile.getWeightKg(),
                nutritionProfile.getTargetCalories(), nutritionProfile.getCalorieTargetMode(),
                nutritionProfile.getEnergyEquationSex(), nutritionProfile.getTargetWeightKg(),
                nutritionProfile.getTargetDate(), nutritionProfile.getAllergies().stream().sorted().toList(),
                nutritionProfile.getIntolerances().stream().sorted().toList(),
                nutritionProfile.getDislikedFoods().stream().sorted().toList(),
                nutritionProfile.getPreferredIngredients().stream().sorted().toList(),
                nutritionProfile.getBudgetPreference(), nutritionProfile.getMaxPreparationMinutes(),
                nutritionProfile.getCookingPreference());
    }

    private NutritionCalculationResponse toResponse(NutritionService.NutritionCalculationResult result) {
        return new NutritionCalculationResponse(result.calories(), result.proteinGrams(),
                result.carbohydrateGrams(), result.fatGrams(), result.fiberGrams(), result.sodiumMg());
    }

    public record FoodResponse(Long id, String name, String brand, int servingSizeGrams,
                              double calories, double proteinGrams, double carbohydrateGrams,
                              double fatGrams, double fiberGrams, double sodiumMg) {}

    public record MealPlanResponse(Long id, String name, String description,
                                 MealPlan.MealType mealType, double targetCalories,
                                 List<MealPlanItemResponse> foods) {}

    public record MealPlanItemResponse(Long foodId, double servings, String foodName) {}

    public record FoodJournalResponse(Long id, Long foodId, FoodJournal.MealType mealType,
                                    java.time.LocalDate entryDate, double servings, String notes) {}

    public record BodyMeasurementResponse(Long id, java.time.LocalDate measurementDate,
                                        double weightKg, double waistCm, double hipCm,
                                        double chestCm, double armCm, double thighCm, String notes) {}

    public record NutritionProfileResponse(Long id, String profileName, String goal,
                                          String dietaryPreference, String activityLevel,
                                          Integer age, Double heightCm, Double weightKg,
                                          Integer targetCalories,
                                          NutritionProfile.CalorieTargetMode calorieTargetMode,
                                          NutritionProfile.EnergyEquationSex energyEquationSex,
                                          Double targetWeightKg, LocalDate targetDate,
                                          List<String> allergies, List<String> intolerances,
                                          List<String> dislikedFoods, List<String> preferredIngredients,
                                          String budgetPreference, Integer maxPreparationMinutes,
                                          String cookingPreference) {}

    public record NutritionCalculationResponse(double calories, double proteinGrams,
                                              double carbohydrateGrams, double fatGrams,
                                              double fiberGrams, double sodiumMg) {}

    public record DailyNutritionResponse(LocalDate date, NutritionCalculationResponse totals,
                                         List<MealNutritionResponse> meals) {}

    public record MealNutritionResponse(FoodJournal.MealType mealType,
                                        NutritionCalculationResponse totals) {}

    public record ApiError(String message) {}

    public record AiPlanResponse(boolean available, String status,
                                 NutritionService.GeneratedMealPlan plan, String note,
                                 String generationContextId) {}
}
