package com.smartgym.nutrition;

import java.time.LocalDate;
import java.util.List;

public final class NutritionPlanApi {
    private NutritionPlanApi() {
    }

    public record GenerateRequest(NutritionPlan.PlanType planType, LocalDate startDate,
                                  List<String> restrictions) {}

    public record DraftResponse(boolean available, String status, String note,
                                String generationContextId, NutritionPlan.PlanType planType,
                                LocalDate startDate, String planName, String goal,
                                NutritionService.GeneratedMealPlan plan,
                                List<DraftDay> days) {}

    public record DraftDay(LocalDate date, List<NutritionService.GeneratedMeal> meals,
                           NutritionService.NutritionEstimate dailyNutrition,
                           List<String> shoppingList, List<String> alternatives) {}

    public record SaveRequest(String generationContextId, NutritionPlan.PlanType planType,
                              LocalDate startDate, String planName, String goal,
                              List<DayInput> days) {}

    public record DayInput(LocalDate date, List<MealInput> meals) {}

    public record MealInput(String name, MealPlan.MealType mealType, Integer servingSizeGrams,
                            String preparationInstructions, List<NutritionService.GeneratedFood> foods,
                            NutritionService.NutritionEstimate estimatedNutrition, String nutritionSource) {}

    public record PlanView(Long id, String name, String goal, NutritionPlan.PlanType planType,
                           LocalDate startDate, LocalDate endDate, long version,
                           List<DayView> days) {}

    public record DayView(Long id, LocalDate date, List<MealView> meals) {}

    public record MealView(Long id, String name, MealPlan.MealType mealType, int servingSizeGrams,
                           String preparationInstructions,
                           NutritionService.NutritionEstimate estimatedNutrition,
                           String nutritionSource, List<FoodView> foods) {}

    public record FoodView(Long foodId, String name, double servings) {}

    public record VersionRequest(long expectedVersion) {}

    public record EditMealRequest(long expectedVersion, MealInput meal) {}
}
