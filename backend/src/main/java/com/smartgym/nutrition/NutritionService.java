package com.smartgym.nutrition;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

@Service
public class NutritionService {
    private final FoodRepository foodRepository;
    private final MealPlanRepository mealPlanRepository;
    private final FoodJournalRepository foodJournalRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final NutritionProfileRepository nutritionProfileRepository;
    private final LocalLlmMealPlanAssistant localLlmMealPlanAssistant;

    public NutritionService(FoodRepository foodRepository,
                           MealPlanRepository mealPlanRepository,
                           FoodJournalRepository foodJournalRepository,
                           BodyMeasurementRepository bodyMeasurementRepository,
                           NutritionProfileRepository nutritionProfileRepository,
                           LocalLlmMealPlanAssistant localLlmMealPlanAssistant) {
        this.foodRepository = foodRepository;
        this.mealPlanRepository = mealPlanRepository;
        this.bodyMeasurementRepository = bodyMeasurementRepository;
        this.foodJournalRepository = foodJournalRepository;
        this.nutritionProfileRepository = nutritionProfileRepository;
        this.localLlmMealPlanAssistant = localLlmMealPlanAssistant;
    }

    @Transactional
    public Food createFood(String subject, CreateFoodRequest request) {
        validateFoodRequest(request);
        Food food = new Food(subject, request.name(), request.brand(), request.servingSizeGrams(),
                request.calories(), request.proteinGrams(), request.carbohydrateGrams(),
                request.fatGrams(), request.fiberGrams(), request.sodiumMg());
        return foodRepository.save(food);
    }

    @Transactional
    public Food updateFood(String subject, Long foodId, CreateFoodRequest request) {
        validateFoodRequest(request);
        Food food = foodRepository.findByIdAndSubject(foodId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found."));
        food.update(request.name(), request.brand(), request.servingSizeGrams(), request.calories(),
                request.proteinGrams(), request.carbohydrateGrams(), request.fatGrams(),
                request.fiberGrams(), request.sodiumMg());
        return foodRepository.save(food);
    }

    @Transactional
    public void deleteFood(String subject, Long foodId) {
        Food food = foodRepository.findByIdAndSubject(foodId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found."));
        if (foodJournalRepository.existsByFoodId(foodId) || mealPlanRepository.countItemsByFoodId(foodId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Food is used by meal plans or journal entries and cannot be deleted.");
        }
        foodRepository.delete(food);
    }

    @Transactional(readOnly = true)
    public List<Food> listFoods(String subject) {
        return foodRepository.findBySubjectOrderByNameAsc(subject);
    }

    @Transactional
    public MealPlan createMealPlan(String subject, CreateMealPlanRequest request) {
        List<MealPlanItem> items = validateMealPlanRequest(subject, request);
        MealPlan mealPlan = new MealPlan(subject, request.name(), request.description(),
                request.mealType(), request.targetCalories(), items);
        return mealPlanRepository.save(mealPlan);
    }

    @Transactional
    public MealPlan updateMealPlan(String subject, Long mealPlanId, CreateMealPlanRequest request) {
        List<MealPlanItem> items = validateMealPlanRequest(subject, request);
        MealPlan mealPlan = mealPlanRepository.findByIdAndSubject(mealPlanId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal plan not found."));
        mealPlan.update(request.name(), request.description(), request.mealType(), request.targetCalories(), items);
        return mealPlanRepository.save(mealPlan);
    }

    @Transactional
    public void deleteMealPlan(String subject, Long mealPlanId) {
        MealPlan mealPlan = mealPlanRepository.findByIdAndSubject(mealPlanId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal plan not found."));
        mealPlanRepository.delete(mealPlan);
    }

    @Transactional(readOnly = true)
    public List<MealPlan> listMealPlans(String subject) {
        return mealPlanRepository.findBySubjectOrderByCreatedAtDesc(subject);
    }

    @Transactional
    public FoodJournal createFoodJournal(String subject, CreateFoodJournalRequest request) {
        validateJournalRequest(subject, request);
        FoodJournal entry = new FoodJournal(subject, request.foodId(),
                request.mealType(), request.entryDate(), request.servings(), request.notes());
        return foodJournalRepository.save(entry);
    }

    @Transactional
    public FoodJournal updateFoodJournal(String subject, Long entryId, CreateFoodJournalRequest request) {
        validateJournalRequest(subject, request);
        FoodJournal entry = foodJournalRepository.findByIdAndSubject(entryId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal entry not found."));
        entry.update(request.foodId(), request.mealType(), request.entryDate(), request.servings(), request.notes());
        return foodJournalRepository.save(entry);
    }

    @Transactional
    public void deleteFoodJournal(String subject, Long entryId) {
        FoodJournal entry = foodJournalRepository.findByIdAndSubject(entryId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal entry not found."));
        foodJournalRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<FoodJournal> listFoodJournal(String subject) {
        return foodJournalRepository.findBySubjectOrderByEntryDateDesc(subject);
    }

    @Transactional
    public BodyMeasurement createBodyMeasurement(String subject, CreateBodyMeasurementRequest request) {
        validateBodyMeasurementRequest(request);
        BodyMeasurement measurement = new BodyMeasurement(subject, request.measurementDate(),
                request.weightKg(), request.waistCm(), request.hipCm(), request.chestCm(),
                request.armCm(), request.thighCm(), request.notes());
        return bodyMeasurementRepository.save(measurement);
    }

    @Transactional
    public BodyMeasurement updateBodyMeasurement(String subject, Long measurementId, CreateBodyMeasurementRequest request) {
        validateBodyMeasurementRequest(request);
        BodyMeasurement measurement = bodyMeasurementRepository.findByIdAndSubject(measurementId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Body measurement not found."));
        measurement.update(request.measurementDate(), request.weightKg(), request.waistCm(),
                request.hipCm(), request.chestCm(), request.armCm(), request.thighCm(), request.notes());
        return bodyMeasurementRepository.save(measurement);
    }

    @Transactional
    public void deleteBodyMeasurement(String subject, Long measurementId) {
        BodyMeasurement measurement = bodyMeasurementRepository.findByIdAndSubject(measurementId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Body measurement not found."));
        bodyMeasurementRepository.delete(measurement);
    }

    @Transactional(readOnly = true)
    public List<BodyMeasurement> listBodyMeasurements(String subject) {
        return bodyMeasurementRepository.findBySubjectOrderByMeasurementDateDesc(subject);
    }

    @Transactional
    public NutritionProfile createOrUpdateProfile(String subject, NutritionProfileRequest request) {
        validateProfileRequest(request);
        NutritionProfile profile = nutritionProfileRepository.findFirstBySubjectOrderByCreatedAtDesc(subject)
                .orElse(new NutritionProfile(subject, request.profileName(), request.goal(),
                        request.dietaryPreference(), request.activityLevel(), request.age(),
                        request.heightCm(), request.weightKg(), request.targetCalories()));
        profile.update(request.profileName(), request.goal(), request.dietaryPreference(),
                request.activityLevel(), request.age(), request.heightCm(), request.weightKg(),
                request.targetCalories());
        return nutritionProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public NutritionProfile getCurrentProfile(String subject) {
        return nutritionProfileRepository.findFirstBySubjectOrderByCreatedAtDesc(subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutrition profile not found."));
    }

    @Transactional(readOnly = true)
    public NutritionCalculationResult calculateNutrition(String subject, List<Long> foodIds) {
        return calculateNutrition(subject, foodIds, null);
    }

    @Transactional(readOnly = true)
    public NutritionCalculationResult calculateNutrition(String subject, List<Long> foodIds, List<Double> servings) {
        if (foodIds == null || foodIds.isEmpty()) {
            if (servings != null && !servings.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serving quantities require food ids.");
            }
            return NutritionCalculationResult.empty();
        }
        if (servings != null && servings.size() != foodIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide one serving quantity for each food id.");
        }

        NutritionCalculationResult total = NutritionCalculationResult.empty();
        for (int index = 0; index < foodIds.size(); index++) {
            Long id = foodIds.get(index);
            if (id == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food ids must not be null.");
            }
            double quantity = servings == null ? 1 : servings.get(index) == null ? Double.NaN : servings.get(index);
            if (!isPositiveFinite(quantity)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serving quantities must be finite and greater than zero.");
            }
            Food food = foodRepository.findByIdAndSubject(id, subject)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Food not found for your account: " + id));
            total = total.add(food, quantity);
        }
        return total;
    }

    @Transactional(readOnly = true)
    public DailyNutritionResult calculateDailyNutrition(String subject, LocalDate date) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A journal date is required.");
        }
        Map<FoodJournal.MealType, NutritionCalculationResult> meals = new EnumMap<>(FoodJournal.MealType.class);
        NutritionCalculationResult total = NutritionCalculationResult.empty();
        for (FoodJournal entry : foodJournalRepository.findBySubjectAndEntryDateOrderByMealTypeAscIdAsc(subject, date)) {
            Food food = foodRepository.findByIdAndSubject(entry.getFoodId(), subject)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Food not found for your account: " + entry.getFoodId()));
            NutritionCalculationResult entryTotals = NutritionCalculationResult.from(food, entry.getServings());
            total = total.add(entryTotals);
            meals.merge(entry.getMealType(), entryTotals, NutritionCalculationResult::add);
        }
        List<MealNutritionResult> mealTotals = meals.entrySet().stream()
                .map(entry -> new MealNutritionResult(entry.getKey(), entry.getValue()))
                .toList();
        return new DailyNutritionResult(date, total, mealTotals);
    }

    @Transactional(readOnly = true)
    public BmiResult calculateBmi(String subject) {
        NutritionProfile profile = getCurrentProfile(subject);
        if (profile.getHeightCm() == null || !isPositiveFinite(profile.getHeightCm())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A positive profile height in centimetres is required to calculate BMI.");
        }
        if (profile.getWeightKg() == null || !isPositiveFinite(profile.getWeightKg())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A positive profile weight in kilograms is required to calculate BMI.");
        }
        // The profile height is stored in centimetres; BMI uses metres.
        double heightMetres = profile.getHeightCm() / 100.0;
        double bmi = profile.getWeightKg() / (heightMetres * heightMetres);
        if (!isPositiveFinite(bmi)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The profile height and weight exceed supported numeric limits for BMI.");
        }
        return new BmiResult(bmi,
                "BMI is an estimate based on height and weight; it is not a measure of body-fat percentage.");
    }

    @Transactional(readOnly = true)
    public MacroTargetResult calculateMacroTargets(String subject) {
        NutritionProfile profile = getCurrentProfile(subject);
        if (profile.getTargetCalories() == null || profile.getTargetCalories() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A positive targetCalories value in the nutrition profile is required for macronutrient targets.");
        }
        if (profile.getWeightKg() == null || !isPositiveFinite(profile.getWeightKg())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A positive profile weight in kilograms is required for protein targets.");
        }

        // Targets use the saved calorie target: protein 1.6 g/kg, fat 25% of kcal,
        // and carbohydrate receives the remaining energy at 4 kcal/g.
        double proteinGrams = profile.getWeightKg() * 1.6;
        double fatCalories = profile.getTargetCalories() * 0.25;
        double fatGrams = fatCalories / 9.0;
        double carbohydrateCalories = profile.getTargetCalories() - (proteinGrams * 4) - fatCalories;
        if (!Double.isFinite(proteinGrams) || !Double.isFinite(fatGrams)
                || !Double.isFinite(carbohydrateCalories) || carbohydrateCalories < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The saved calorie target is too low for the documented protein and fat targets.");
        }
        double carbohydrateGrams = carbohydrateCalories / 4.0;
        double estimatedCalories = 4 * proteinGrams + 4 * carbohydrateGrams + 9 * fatGrams;
        if (!isNonNegativeFinite(carbohydrateGrams) || !isNonNegativeFinite(estimatedCalories)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The saved profile values exceed supported numeric limits for macronutrient estimates.");
        }
        return new MacroTargetResult(profile.getTargetCalories(), proteinGrams, carbohydrateGrams,
                fatGrams, estimatedCalories,
                "Protein: 1.6 g/kg body weight; fat: 25% of saved calorie target; carbohydrates: remaining calories. "
                        + "These are estimates based on the manually saved calorie target, not a TDEE-derived prescription.");
    }

    @Transactional(readOnly = true)
    public EnergyEstimateResult calculateEnergyEstimate(String subject) {
        NutritionProfile profile = getCurrentProfile(subject);
        Optional<GoalCategory> goalCategory = mapGoalCategory(profile.getGoal());
        OptionalDouble activityFactor = mapActivityFactor(profile.getActivityLevel());
        String note = "BMR and TDEE are unavailable because this profile has no sex-related input needed "
                + "to select the Mifflin–St Jeor equation. No value has been assumed; provide the required "
                + "input through an approved profile change before calculating energy expenditure.";
        if (goalCategory.isEmpty()) {
            note += " The saved goal does not match a supported mapping.";
        }
        if (activityFactor.isEmpty()) {
            note += " The saved activity level does not match a supported mapping.";
        }
        return new EnergyEstimateResult(false, null, null,
                goalCategory.map(Enum::name).orElse(null),
                activityFactor.isPresent() ? activityFactor.getAsDouble() : null,
                note);
    }

    static Optional<GoalCategory> mapGoalCategory(String goal) {
        if (goal == null) {
            return Optional.empty();
        }
        return switch (goal.trim().toLowerCase(Locale.ROOT)) {
            case "weight loss" -> Optional.of(GoalCategory.WEIGHT_LOSS);
            case "maintenance" -> Optional.of(GoalCategory.MAINTENANCE);
            case "muscle gain" -> Optional.of(GoalCategory.MUSCLE_GAIN);
            default -> Optional.empty();
        };
    }

    static OptionalDouble mapActivityFactor(String activityLevel) {
        if (activityLevel == null) {
            return OptionalDouble.empty();
        }
        return switch (activityLevel.trim().toLowerCase(Locale.ROOT)) {
            case "sedentary" -> OptionalDouble.of(1.2);
            case "light" -> OptionalDouble.of(1.375);
            case "moderate" -> OptionalDouble.of(1.55);
            case "active" -> OptionalDouble.of(1.725);
            case "very active" -> OptionalDouble.of(1.9);
            default -> OptionalDouble.empty();
        };
    }

    public CompletableFuture<MealPlanSuggestion> generateMealPlanSuggestion(String subject, AiMealRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meal generation request is required.");
        }
        NutritionProfile profile = nutritionProfileRepository.findFirstBySubjectOrderByCreatedAtDesc(subject)
                .orElse(null);
        return localLlmMealPlanAssistant.generateAsync(
                request, profile, foodRepository.findBySubjectOrderByNameAsc(subject));
    }

    @Transactional
    public List<MealPlan> saveGeneratedMealPlan(String subject, SaveGeneratedMealPlanRequest request) {
        if (request == null || isBlank(request.planName()) || request.planName().length() > 50
                || request.goal() == null || request.goal().isBlank()
                || request.meals() == null || request.meals().isEmpty() || request.meals().size() > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A plan name, goal, and between one and twelve meals are required.");
        }
        List<String> restrictions = request.restrictions() == null ? List.of()
                : request.restrictions().stream().filter(value -> value != null)
                        .map(String::trim).filter(value -> !value.isBlank()).toList();
        if (restrictions.stream().anyMatch(value -> value.length() > 100)
                || violatesRestriction(request.planName(), restrictions)
                || violatesRestriction(request.goal(), restrictions)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The reviewed plan contains an explicitly excluded ingredient or restriction.");
        }
        List<CreateMealPlanRequest> validatedPlans = new ArrayList<>();
        for (SaveGeneratedMealRequest meal : request.meals()) {
            if (meal == null || isBlank(meal.name()) || meal.name().length() > 60
                    || meal.mealType() == null || meal.preparationInstructions() == null
                    || meal.preparationInstructions().isBlank()
                    || meal.preparationInstructions().length() > 350
                    || meal.foods() == null || meal.foods().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each saved meal requires a name, meal type, preparation instructions, and foods.");
            }
            if (violatesRestriction(meal.name(), restrictions)
                    || violatesRestriction(meal.preparationInstructions(), restrictions)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The reviewed plan contains an explicitly excluded ingredient or restriction.");
            }
            double mealCalories = 0;
            for (MealPlanItemRequest item : meal.foods()) {
                if (item == null || item.foodId() == null || item.servings() == null
                        || !isPositiveFinite(item.servings())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Each saved food requires an owned food id and positive serving quantity.");
                }
                Food food = foodRepository.findByIdAndSubject(item.foodId(), subject)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "A selected food was not found for your account."));
                if (violatesRestriction(food.getName(), restrictions)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A selected saved food matches an explicitly excluded ingredient or restriction.");
                }
                mealCalories += food.getCalories() * item.servings();
                if (!isNonNegativeFinite(mealCalories)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Meal nutrition exceeds supported numeric limits.");
                }
            }
            String name = request.planName() + " - " + meal.name();
            String description = "Goal: " + request.goal() + "\nPreparation: " + meal.preparationInstructions();
            validatedPlans.add(new CreateMealPlanRequest(name, description, meal.mealType(),
                    mealCalories, meal.foods()));
        }
        List<MealPlan> savedPlans = new ArrayList<>();
        for (CreateMealPlanRequest validatedPlan : validatedPlans) {
            List<MealPlanItem> items = validateMealPlanRequest(subject, validatedPlan);
            savedPlans.add(mealPlanRepository.save(new MealPlan(subject, validatedPlan.name(),
                    validatedPlan.description(), validatedPlan.mealType(), validatedPlan.targetCalories(), items)));
        }
        return savedPlans;
    }

    private boolean violatesRestriction(String text, List<String> restrictions) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return restrictions.stream()
                .flatMap(value -> java.util.Arrays.stream(value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", " ").trim().split("\\s+")))
                .filter(value -> !List.of("allergy", "allergic", "intolerance", "intolerant",
                        "avoid", "avoidance", "free", "to", "and").contains(value))
                .anyMatch(normalized::contains);
    }

    private void validateFoodRequest(CreateFoodRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.servingSizeGrams() == null || request.calories() == null
                || request.proteinGrams() == null || request.carbohydrateGrams() == null
                || request.fatGrams() == null || request.fiberGrams() == null || request.sodiumMg() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food name, serving size, and nutrition values are required.");
        }
        if (request.servingSizeGrams() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serving size must be greater than zero.");
        }
        if (request.name().length() > 150 || (request.brand() != null && request.brand().length() > 150)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food name and brand must be at most 150 characters.");
        }
        if (!isNonNegativeFinite(request.calories()) || !isNonNegativeFinite(request.proteinGrams())
                || !isNonNegativeFinite(request.carbohydrateGrams()) || !isNonNegativeFinite(request.fatGrams())
                || !isNonNegativeFinite(request.fiberGrams()) || !isNonNegativeFinite(request.sodiumMg())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nutritional values must be finite and nonnegative.");
        }
    }

    private List<MealPlanItem> validateMealPlanRequest(String subject, CreateMealPlanRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meal plan name is required.");
        }
        if (request.name().length() > 120 || (request.description() != null && request.description().length() > 500)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meal plan name or description is too long.");
        }
        if (request.foods() == null || request.foods().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one food item is required.");
        }
        if (request.mealType() == null || request.targetCalories() == null
                || !isNonNegativeFinite(request.targetCalories())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meal type and a nonnegative calorie target are required.");
        }

        List<MealPlanItem> items = new ArrayList<>();
        for (MealPlanItemRequest foodItem : request.foods()) {
            if (foodItem == null || foodItem.foodId() == null || foodItem.servings() == null
                    || !Double.isFinite(foodItem.servings()) || foodItem.servings() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each food item requires a valid food id and positive servings.");
            }
            Food linkedFood = foodRepository.findByIdAndSubject(foodItem.foodId(), subject)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Food not found for your account: " + foodItem.foodId()));
            items.add(new MealPlanItem(foodItem.foodId(), foodItem.servings(), linkedFood.getName()));
        }
        return items;
    }

    private void validateJournalRequest(String subject, CreateFoodJournalRequest request) {
        if (request == null || request.foodId() == null || request.mealType() == null
                || request.entryDate() == null || request.servings() == null
                || !Double.isFinite(request.servings()) || request.servings() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Food, meal type, entry date, and positive servings are required.");
        }
        if (request.notes() != null && request.notes().length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal notes must be at most 300 characters.");
        }
        foodRepository.findByIdAndSubject(request.foodId(), subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Food not found for your account: " + request.foodId()));
    }

    private void validateBodyMeasurementRequest(CreateBodyMeasurementRequest request) {
        if (request == null || request.measurementDate() == null || request.weightKg() == null
                || request.waistCm() == null || request.hipCm() == null || request.chestCm() == null
                || request.armCm() == null || request.thighCm() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Measurement date and values are required.");
        }
        if (!isPositiveFinite(request.weightKg()) || !isPositiveFinite(request.waistCm())
                || !isPositiveFinite(request.hipCm()) || !isPositiveFinite(request.chestCm())
                || !isPositiveFinite(request.armCm()) || !isPositiveFinite(request.thighCm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Measurement values must be finite and greater than zero.");
        }
        if (request.notes() != null && request.notes().length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Measurement notes must be at most 300 characters.");
        }
    }

    private void validateProfileRequest(NutritionProfileRequest request) {
        if (request == null || isBlank(request.profileName()) || isBlank(request.goal())
                || isBlank(request.dietaryPreference()) || isBlank(request.activityLevel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Profile name, goal, dietary preference, and activity level are required.");
        }
        if (request.profileName().length() > 120 || request.goal().length() > 50
                || request.dietaryPreference().length() > 80 || request.activityLevel().length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nutrition profile text fields exceed their maximum length.");
        }
        if ((request.age() != null && request.age() <= 0)
                || (request.heightCm() != null && !isPositiveFinite(request.heightCm()))
                || (request.weightKg() != null && !isPositiveFinite(request.weightKg()))
                || (request.targetCalories() != null && request.targetCalories() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Age, height, weight, and target calories must be positive when provided.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private boolean isNonNegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    public record CreateFoodRequest(String name, String brand, Integer servingSizeGrams,
                                   Double calories, Double proteinGrams,
                                   Double carbohydrateGrams, Double fatGrams,
                                   Double fiberGrams, Double sodiumMg) {}

    public record MealPlanItemRequest(Long foodId, Double servings) {}

    public record CreateMealPlanRequest(String name, String description,
                                       MealPlan.MealType mealType, Double targetCalories,
                                       List<MealPlanItemRequest> foods) {}

    public record CreateFoodJournalRequest(Long foodId, FoodJournal.MealType mealType,
                                          LocalDate entryDate, Double servings, String notes) {}

    public record CreateBodyMeasurementRequest(LocalDate measurementDate, Double weightKg,
                                              Double waistCm, Double hipCm, Double chestCm,
                                              Double armCm, Double thighCm, String notes) {}

    public record NutritionProfileRequest(String profileName, String goal, String dietaryPreference,
                                         String activityLevel, Integer age, Double heightCm,
                                         Double weightKg, Integer targetCalories) {}

    public record NutritionCalculationResult(double calories, double proteinGrams,
                                            double carbohydrateGrams, double fatGrams,
                                            double fiberGrams, double sodiumMg) {
        private static NutritionCalculationResult empty() {
            return new NutritionCalculationResult(0, 0, 0, 0, 0, 0);
        }

        private static NutritionCalculationResult from(Food food, double servings) {
            // Food nutrient values are stored per serving; servings is a serving multiplier.
            return new NutritionCalculationResult(food.getCalories() * servings,
                    food.getProteinGrams() * servings, food.getCarbohydrateGrams() * servings,
                    food.getFatGrams() * servings, food.getFiberGrams() * servings,
                    food.getSodiumMg() * servings);
        }

        private NutritionCalculationResult add(Food food, double servings) {
            return add(from(food, servings));
        }

        private NutritionCalculationResult add(NutritionCalculationResult other) {
            NutritionCalculationResult result = new NutritionCalculationResult(calories + other.calories,
                    proteinGrams + other.proteinGrams, carbohydrateGrams + other.carbohydrateGrams,
                    fatGrams + other.fatGrams, fiberGrams + other.fiberGrams,
                    sodiumMg + other.sodiumMg);
            if (!Double.isFinite(result.calories()) || !Double.isFinite(result.proteinGrams())
                    || !Double.isFinite(result.carbohydrateGrams()) || !Double.isFinite(result.fatGrams())
                    || !Double.isFinite(result.fiberGrams()) || !Double.isFinite(result.sodiumMg())
                    || result.calories() < 0 || result.proteinGrams() < 0
                    || result.carbohydrateGrams() < 0 || result.fatGrams() < 0
                    || result.fiberGrams() < 0 || result.sodiumMg() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Nutrition totals exceed supported numeric limits.");
            }
            return result;
        }
    }

    public record DailyNutritionResult(LocalDate date, NutritionCalculationResult totals,
                                      List<MealNutritionResult> meals) {}

    public record MealNutritionResult(FoodJournal.MealType mealType, NutritionCalculationResult totals) {}

    public record BmiResult(double bmi, String note) {}

    public record MacroTargetResult(int calorieTarget, double proteinGrams, double carbohydrateGrams,
                                    double fatGrams, double estimatedCalories, String method) {}

    public enum GoalCategory {
        WEIGHT_LOSS,
        MAINTENANCE,
        MUSCLE_GAIN
    }

    public record EnergyEstimateResult(boolean available, Double bmrCaloriesPerDay,
                                       Double tdeeCaloriesPerDay, String goalCategory,
                                       Double activityFactor, String note) {}

    public record AiMealRequest(String goal, Integer targetCalories, String dietStyle,
                               List<String> restrictions) {}

    public record GeneratedFood(String name, Double servings, Long foodId) {}

    public record NutritionEstimate(Double calories, Double proteinGrams,
                                    Double carbohydrateGrams, Double fatGrams) {
        public NutritionEstimate add(NutritionEstimate other, double servings) {
            NutritionEstimate total = new NutritionEstimate(calories + other.calories * servings,
                    proteinGrams + other.proteinGrams * servings,
                    carbohydrateGrams + other.carbohydrateGrams * servings,
                    fatGrams + other.fatGrams * servings);
            if (!isEstimateValue(total.calories()) || !isEstimateValue(total.proteinGrams())
                    || !isEstimateValue(total.carbohydrateGrams()) || !isEstimateValue(total.fatGrams())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Generated nutrition totals exceed supported numeric limits.");
            }
            return total;
        }

        private static boolean isEstimateValue(Double value) {
            return value != null && Double.isFinite(value) && value >= 0;
        }
    }

    public record GeneratedMeal(String name, MealPlan.MealType mealType, List<GeneratedFood> foods,
                                String preparationInstructions, NutritionEstimate estimatedNutrition,
                                String nutritionSource) {}

    public record GeneratedMealPlan(String planName, String goal, List<GeneratedMeal> meals,
                                    NutritionEstimate dailyNutrition, List<String> shoppingList,
                                    List<String> alternatives) {}

    public record MealPlanSuggestion(boolean available, String status,
                                     GeneratedMealPlan plan, String note) {}

    public record SaveGeneratedMealPlanRequest(String planName, String goal, List<String> restrictions,
                                               List<SaveGeneratedMealRequest> meals) {}

    public record SaveGeneratedMealRequest(String name, MealPlan.MealType mealType,
                                           String preparationInstructions,
                                           List<MealPlanItemRequest> foods) {}
}
