package com.smartgym.nutrition;

import com.smartgym.membership.profile.BusinessProfile;
import com.smartgym.membership.profile.BusinessProfileRepository;
import com.smartgym.membership.profile.CoachAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/nutrition/coach")
public class CoachNutritionController {
    private final NutritionService nutritionService;
    private final BusinessProfileRepository profileRepository;
    private final CoachAssignmentRepository assignmentRepository;

    public CoachNutritionController(
            NutritionService nutritionService,
            BusinessProfileRepository profileRepository,
            CoachAssignmentRepository assignmentRepository) {
        this.nutritionService = nutritionService;
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<NutritionController.ApiError> handleNutritionError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new NutritionController.ApiError(exception.getReason()));
    }

    @GetMapping("/members/{memberProfileId}/foods")
    public List<FoodResponse> foods(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberProfileId) {
        String memberSubject = assignedMemberSubject(jwt, memberProfileId);
        return nutritionService.listFoods(memberSubject).stream().map(CoachNutritionController::toResponse).toList();
    }

    @PutMapping("/members/{memberProfileId}/foods/{foodId}")
    public FoodResponse updateFood(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberProfileId,
            @PathVariable Long foodId,
            @RequestBody NutritionService.CreateFoodRequest request) {
        String memberSubject = assignedMemberSubject(jwt, memberProfileId);
        return toResponse(nutritionService.updateFood(memberSubject, foodId, request));
    }

    @DeleteMapping("/members/{memberProfileId}/foods/{foodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFood(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberProfileId,
            @PathVariable Long foodId) {
        String memberSubject = assignedMemberSubject(jwt, memberProfileId);
        nutritionService.deleteFood(memberSubject, foodId);
    }

    @GetMapping("/members/{memberProfileId}/meal-plans")
    public List<MealPlanResponse> mealPlans(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberProfileId) {
        String memberSubject = assignedMemberSubject(jwt, memberProfileId);
        return nutritionService.listMealPlans(memberSubject).stream()
                .map(CoachNutritionController::toResponse).toList();
    }

    @PutMapping("/members/{memberProfileId}/meal-plans/{mealPlanId}")
    public MealPlanResponse updateMealPlan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberProfileId,
            @PathVariable Long mealPlanId,
            @RequestBody NutritionService.CreateMealPlanRequest request) {
        String memberSubject = assignedMemberSubject(jwt, memberProfileId);
        return toResponse(nutritionService.updateMealPlan(memberSubject, mealPlanId, request));
    }

    @GetMapping("/members/{memberProfileId}/plans")
    public List<NutritionPlanApi.PlanView> persistentPlans(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID memberProfileId) {
        String memberSubject = assignedMemberSubject(jwt, memberProfileId);
        return nutritionService.listPersistentPlans(memberSubject);
    }

    @PutMapping("/members/{memberProfileId}/plans/{planId}/days/{date}/meals/{mealId}")
    public NutritionPlanApi.PlanView updatePersistentMeal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberProfileId,
            @PathVariable Long planId,
            @PathVariable LocalDate date,
            @PathVariable Long mealId,
            @RequestBody NutritionPlanApi.EditMealRequest request) {
        String memberSubject = assignedMemberSubject(jwt, memberProfileId);
        return nutritionService.editPlanMeal(memberSubject, planId, date, mealId, request);
    }

    private String assignedMemberSubject(Jwt jwt, UUID memberProfileId) {
        BusinessProfile member = profileRepository.findByMemberProfileId(memberProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member profile not found."));
        if (!assignmentRepository.existsByCoachSubjectAndMemberSubject(jwt.getSubject(), member.getSubject())) {
            throw new AccessDeniedException("This member is not assigned to your coach account.");
        }
        return member.getSubject();
    }

    private static FoodResponse toResponse(Food food) {
        return new FoodResponse(food.getId(), food.getName(), food.getBrand(), food.getServingSizeGrams(),
                food.getCalories(), food.getProteinGrams(), food.getCarbohydrateGrams(),
                food.getFatGrams(), food.getFiberGrams(), food.getSodiumMg());
    }

    private static MealPlanResponse toResponse(MealPlan mealPlan) {
        return new MealPlanResponse(mealPlan.getId(), mealPlan.getName(), mealPlan.getDescription(),
                mealPlan.getMealType(), mealPlan.getTargetCalories(),
                mealPlan.getItems().stream().map(item ->
                        new MealPlanItemResponse(item.getFoodId(), item.getServings(), item.getFoodName())).toList());
    }

    public record FoodResponse(Long id, String name, String brand, int servingSizeGrams,
                               double calories, double proteinGrams, double carbohydrateGrams,
                               double fatGrams, double fiberGrams, double sodiumMg) {}

    public record MealPlanResponse(Long id, String name, String description,
                                   MealPlan.MealType mealType, double targetCalories,
                                   List<MealPlanItemResponse> foods) {}

    public record MealPlanItemResponse(Long foodId, double servings, String foodName) {}
}
