package com.smartgym.nutrition;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class NutritionService {
    private static final int DAILY_WATER_ESTIMATE_ML = 2000;
    private static final int MAX_SINGLE_WATER_LOG_ML = 2000;
    private static final int MAX_DAILY_WATER_LOG_ML = 10000;
    private final FoodRepository foodRepository;
    private final MealPlanRepository mealPlanRepository;
    private final FoodJournalRepository foodJournalRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final NutritionProfileRepository nutritionProfileRepository;
    private final MealPlanGenerationContextRepository generationContextRepository;
    private final NutritionPlanRepository nutritionPlanRepository;
    private final HydrationLogRepository hydrationLogRepository;
    private final LocalLlmMealPlanAssistant localLlmMealPlanAssistant;
    private final TransactionTemplate transactionTemplate;

    public NutritionService(FoodRepository foodRepository,
                           MealPlanRepository mealPlanRepository,
                           FoodJournalRepository foodJournalRepository,
                           BodyMeasurementRepository bodyMeasurementRepository,
                           NutritionProfileRepository nutritionProfileRepository,
                           MealPlanGenerationContextRepository generationContextRepository,
                           NutritionPlanRepository nutritionPlanRepository,
                           HydrationLogRepository hydrationLogRepository,
                           LocalLlmMealPlanAssistant localLlmMealPlanAssistant,
                           PlatformTransactionManager transactionManager) {
        this.foodRepository = foodRepository;
        this.mealPlanRepository = mealPlanRepository;
        this.bodyMeasurementRepository = bodyMeasurementRepository;
        this.foodJournalRepository = foodJournalRepository;
        this.nutritionProfileRepository = nutritionProfileRepository;
        this.generationContextRepository = generationContextRepository;
        this.nutritionPlanRepository = nutritionPlanRepository;
        this.hydrationLogRepository = hydrationLogRepository;
        this.localLlmMealPlanAssistant = localLlmMealPlanAssistant;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        String updatedFoodText = request.name() + " " + (request.brand() == null ? "" : request.brand());
        for (MealPlan mealPlan : mealPlanRepository.findDistinctByItemsFoodId(foodId)) {
            MealPlanGenerationContext context = mealPlan.getGenerationContext();
            if (context != null && violatesRestriction(updatedFoodText, context.getRestrictions())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The updated food conflicts with a restriction saved for a generated meal.");
            }
        }

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
        MealPlan mealPlan = mealPlanRepository.findByIdAndSubject(mealPlanId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal plan not found."));
        List<MealPlanItem> items = validateMealPlanRequest(subject, request);
        validateGeneratedMealRestrictions(subject, mealPlan.getGenerationContext(), request.name(),
                request.description(), items);
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
        NutritionProfile.CalorieTargetMode targetMode = request.calorieTargetMode() != null
                ? request.calorieTargetMode()
                : request.targetCalories() == null
                        ? NutritionProfile.CalorieTargetMode.AUTO
                        : NutritionProfile.CalorieTargetMode.MANUAL;
        Integer targetCalories = targetMode == NutritionProfile.CalorieTargetMode.AUTO
                ? calculateEnergyEstimate(request.age(), request.heightCm(), request.weightKg(),
                        request.energyEquationSex(), request.activityLevel(), request.goal())
                        .goalAdjustedCalories()
                : request.targetCalories();
        NutritionProfile profile = nutritionProfileRepository.findFirstBySubjectOrderByCreatedAtDesc(subject)
                .orElse(new NutritionProfile(subject, request.profileName(), request.goal(),
                        request.dietaryPreference(), request.activityLevel(), request.age(),
                        request.heightCm(), request.weightKg(), targetCalories, targetMode,
                        request.energyEquationSex()));
        validateGoalTarget(request.targetWeightKg(), request.targetDate(),
                request.weightKg(), request.heightCm(), request.age(), request.goal());
        Set<String> allergies = request.allergies() == null
                ? profile.getAllergies() : normalizeProfilePreferences(request.allergies(), "allergies");
        Set<String> intolerances = request.intolerances() == null
                ? profile.getIntolerances() : normalizeProfilePreferences(request.intolerances(), "intolerances");
        Set<String> dislikedFoods = request.dislikedFoods() == null
                ? profile.getDislikedFoods() : normalizeProfilePreferences(request.dislikedFoods(), "disliked foods");
        Set<String> preferredIngredients = request.preferredIngredients() == null
                ? profile.getPreferredIngredients()
                : normalizeProfilePreferences(request.preferredIngredients(), "preferred ingredients");
        profile.update(request.profileName(), request.goal(), request.dietaryPreference(),
                request.activityLevel(), request.age(), request.heightCm(), request.weightKg(),
                targetCalories, targetMode, request.energyEquationSex(), request.targetWeightKg(),
                request.targetDate(), request.budgetPreference(), request.maxPreparationMinutes(),
                request.cookingPreference(), allergies, intolerances, dislikedFoods, preferredIngredients);
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

    @Transactional
    public HydrationView addHydrationLog(String subject, HydrationRequest request) {
        validateHydrationRequest(request);
        Instant consumedAt = request.consumedAt() == null ? Instant.now() : request.consumedAt();
        validateDailyHydrationLimit(subject, consumedAt, request.waterMl(), null);
        hydrationLogRepository.save(new HydrationLog(subject, consumedAt, request.waterMl()));
        return getHydrationDay(subject, consumedAt.atZone(ZoneOffset.UTC).toLocalDate());
    }

    @Transactional
    public HydrationView correctHydrationLog(String subject, Long logId, HydrationRequest request) {
        validateHydrationRequest(request);
        HydrationLog log = hydrationLogRepository.findByIdAndSubject(logId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hydration log not found."));
        Instant consumedAt = request.consumedAt() == null ? log.getConsumedAt() : request.consumedAt();
        validateDailyHydrationLimit(subject, consumedAt, request.waterMl(), log);
        log.correct(consumedAt, request.waterMl());
        return getHydrationDay(subject, consumedAt.atZone(ZoneOffset.UTC).toLocalDate());
    }

    @Transactional
    public void deleteHydrationLog(String subject, Long logId) {
        HydrationLog log = hydrationLogRepository.findByIdAndSubject(logId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hydration log not found."));
        hydrationLogRepository.delete(log);
    }

    @Transactional(readOnly = true)
    public HydrationView getHydrationDay(String subject, LocalDate date) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A hydration date is required.");
        }
        Instant from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<HydrationLogView> logs = hydrationLogRepository
                .findBySubjectAndConsumedAtGreaterThanEqualAndConsumedAtLessThanOrderByConsumedAtAsc(
                        subject, from, to)
                .stream().map(log -> new HydrationLogView(log.getId(), log.getConsumedAt(), log.getWaterMl()))
                .toList();
        int consumedMl = logs.stream().mapToInt(HydrationLogView::waterMl).sum();
        return new HydrationView(date, consumedMl, DAILY_WATER_ESTIMATE_ML, logs,
                "General reference estimate: 2,000 mL/day of drinking water. Actual needs vary. "
                        + "This tracks drinks only, excludes moisture in food, and is not a medical prescription.");
    }

    @Transactional(readOnly = true)
    public List<HydrationLogView> getHydrationHistory(String subject, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)
                || ChronoUnit.DAYS.between(fromDate, toDate) > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hydration history requires a valid date range of at most 366 days.");
        }
        Instant from = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return hydrationLogRepository
                .findBySubjectAndConsumedAtGreaterThanEqualAndConsumedAtLessThanOrderByConsumedAtDesc(
                        subject, from, to)
                .stream().map(log -> new HydrationLogView(log.getId(), log.getConsumedAt(), log.getWaterMl()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BmiResult calculateBmi(String subject) {
        NutritionProfile profile = getCurrentProfile(subject);
        if (profile.getAge() != null && profile.getAge() < 18) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Adult BMI calculations and interpretations are not provided for minors.");
        }
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
                "BMI is a screening measurement based on height and weight, not a measure of body-fat percentage, and not a diagnosis.");
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
        return calculateEnergyEstimate(profile.getAge(), profile.getHeightCm(), profile.getWeightKg(),
                profile.getEnergyEquationSex(), profile.getActivityLevel(), profile.getGoal());
    }

    @Transactional(readOnly = true)
    public GoalTimelineResult calculateGoalTimeline(String subject) {
        NutritionProfile profile = getCurrentProfile(subject);
        return estimateGoalTimeline(profile.getAge(), profile.getHeightCm(), profile.getWeightKg(),
                profile.getTargetWeightKg(), profile.getTargetDate(), profile.getGoal(),
                LocalDate.now(ZoneOffset.UTC));
    }

    static GoalTimelineResult estimateGoalTimeline(Integer age, Double heightCm, Double currentWeightKg,
                                                   Double targetWeightKg, LocalDate targetDate,
                                                   String goal, LocalDate today) {
        if (age == null || age < 18 || age > 120) {
            return GoalTimelineResult.unavailable(targetDate,
                    "Adult weight-goal estimates are not available for minors.");
        }
        if (targetWeightKg == null || currentWeightKg == null || heightCm == null
                || !Double.isFinite(targetWeightKg) || !Double.isFinite(currentWeightKg)
                || !Double.isFinite(heightCm) || currentWeightKg <= 0 || heightCm <= 0
                || currentWeightKg > 500 || targetWeightKg > 500 || heightCm > 300) {
            return GoalTimelineResult.unavailable(targetDate,
                    "Save an adult current weight, target weight, and height to estimate a weight-loss timeline.");
        }
        if (!"weight loss".equalsIgnoreCase(goal == null ? "" : goal.trim())) {
            return GoalTimelineResult.unavailable(targetDate,
                    "A documented timeline method is available for weight-loss goals only; no rate is applied to maintenance or weight-gain goals.");
        }
        double lossKg = currentWeightKg - targetWeightKg;
        if (lossKg <= 0) {
            return GoalTimelineResult.unavailable(targetDate,
                    "The saved target is not below the current weight, so a weight-loss timeline cannot be estimated.");
        }
        long minWeeks = Math.max(1, (long) Math.ceil(lossKg / 0.5));
        long maxWeeks = Math.max(minWeeks, (long) Math.ceil(lossKg / 0.25));
        LocalDate earliest = today.plusWeeks(minWeeks);
        LocalDate latest = today.plusWeeks(maxWeeks);
        String requestedDateStatus = targetDate == null ? "NOT_SET"
                : targetDate.isBefore(earliest) ? "EARLIER_THAN_ESTIMATE"
                : targetDate.isAfter(latest) ? "LATER_THAN_ESTIMATE" : "WITHIN_ESTIMATE";
        String note = "Approximate range uses a conservative reference rate of 0.25–0.5 kg per week; actual progress varies and is not guaranteed. "
                + (requestedDateStatus.equals("EARLIER_THAN_ESTIMATE")
                    ? "The requested date is earlier than this estimate and may be unrealistic."
                    : requestedDateStatus.equals("LATER_THAN_ESTIMATE")
                        ? "The requested date is later than this estimate; it is a personal target, not a prediction."
                        : requestedDateStatus.equals("WITHIN_ESTIMATE")
                            ? "The requested date falls within this approximate range, not a promise."
                            : "No desired date was set.");
        return new GoalTimelineResult(true, minWeeks, maxWeeks, earliest, latest,
                targetDate, requestedDateStatus, note,
                "Weight loss only; 0.25–0.5 kg/week reference; adult profile; calculated from saved current and target weights. "
                        + "BMI is a screening measurement, not a diagnosis.");
    }

    private EnergyEstimateResult calculateEnergyEstimate(Integer age, Double heightCm, Double weightKg,
                                                         NutritionProfile.EnergyEquationSex equationSex,
                                                         String activityLevel, String goal) {
        Optional<GoalCategory> goalCategory = mapGoalCategory(goal);
        OptionalDouble activityFactor = mapActivityFactor(activityLevel);
        String missing = age == null || age < 18 ? "an age of at least 18"
                : heightCm == null || !isPositiveFinite(heightCm) ? "height"
                : weightKg == null || !isPositiveFinite(weightKg) ? "current weight"
                : equationSex == null ? "the optional Mifflin–St Jeor equation input"
                : activityFactor.isEmpty() ? "a supported activity level"
                : goalCategory.isEmpty() ? "a supported fitness goal" : null;
        if (missing != null) {
            return new EnergyEstimateResult(false, null, null,
                    goalCategory.map(Enum::name).orElse(null),
                    activityFactor.isPresent() ? activityFactor.getAsDouble() : null,
                    null, "An adult calorie estimate needs " + missing
                            + ". Enter the missing profile details; no values have been assumed.");
        }

        double sexAdjustment = equationSex == NutritionProfile.EnergyEquationSex.MALE ? 5 : -161;
        double bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + sexAdjustment;
        double tdee = bmr * activityFactor.getAsDouble();
        double goalAdjustedCalories = switch (goalCategory.orElseThrow()) {
            case WEIGHT_LOSS -> tdee * 0.85;
            case MAINTENANCE -> tdee;
            case MUSCLE_GAIN -> tdee * 1.05;
        };
        if (!isPositiveFinite(bmr) || !isPositiveFinite(tdee)
                || !isPositiveFinite(goalAdjustedCalories)
                || goalAdjustedCalories > Integer.MAX_VALUE) {
            return new EnergyEstimateResult(false, null, null, goalCategory.orElseThrow().name(),
                    activityFactor.getAsDouble(), null,
                    "The saved measurements exceed the supported range for an energy estimate.");
        }
        int roundedTarget = (int) Math.round(goalAdjustedCalories);
        return new EnergyEstimateResult(true, bmr, tdee, goalCategory.orElseThrow().name(),
                activityFactor.getAsDouble(), roundedTarget,
                "Estimated with Mifflin–St Jeor for adults. The daily target uses an approximate 15% deficit "
                        + "for weight loss, maintenance calories, or a 5% surplus for muscle gain. "
                        + "This is an estimate, not a medical recommendation.");
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
        List<String> requestedRestrictions = normalizeRestrictions(request.restrictions());
        NutritionProfile profile = nutritionProfileRepository.findFirstBySubjectOrderByCreatedAtDesc(subject)
                .orElse(null);
        List<String> restrictions = effectiveRestrictions(profile, requestedRestrictions);
        AiMealRequest effectiveRequest = aiMealRequest(profile, request.goal(), request.targetCalories(),
                request.dietStyle(), restrictions);
        return localLlmMealPlanAssistant.generateAsync(
                effectiveRequest,
                profile, foodRepository.findBySubjectOrderByNameAsc(subject))
                .thenApply(suggestion -> {
                    if (!suggestion.available() || suggestion.plan() == null) {
                        return suggestion;
                    }
                    MealPlanGenerationContext context = generationContextRepository.save(
                            new MealPlanGenerationContext(subject, restrictions));
                    return new MealPlanSuggestion(suggestion.available(), suggestion.status(),
                            suggestion.plan(), suggestion.note(), context.getId().toString());
                });
    }

    public CompletableFuture<NutritionPlanApi.DraftResponse> generatePersistentPlanDraft(
            String subject, NutritionPlanApi.GenerateRequest request) {
        if (request == null || request.planType() == null || request.startDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A plan type and start date are required.");
        }
        NutritionProfile profile = nutritionProfileRepository.findFirstBySubjectOrderByCreatedAtDesc(subject)
                .orElse(null);
        List<Food> foods = foodRepository.findBySubjectOrderByNameAsc(subject);
        List<String> restrictions = effectiveRestrictions(profile, request.restrictions());
        if (profile == null || profile.getTargetCalories() == null
                || profile.getTargetCalories() <= 0 || isBlank(profile.getGoal())) {
            return CompletableFuture.completedFuture(new NutritionPlanApi.DraftResponse(false, "UNAVAILABLE",
                    "Save a nutrition profile with a fitness goal and calorie target first.",
                    null, request.planType(), request.startDate(), null, null, null, List.of()));
        }

        int dayCount = request.planType() == NutritionPlan.PlanType.WEEKLY ? 7 : 1;
        AiMealRequest aiRequest = aiMealRequest(profile, profile.getGoal(), profile.getTargetCalories(),
                profile.getDietaryPreference(), restrictions);
        return generatePlanDaysSequentially(aiRequest, profile, foods, dayCount, new ArrayList<>())
                .thenApply(suggestions -> {
                    Optional<MealPlanSuggestion> unavailable = suggestions.stream()
                            .filter(suggestion -> !suggestion.available() || suggestion.plan() == null).findFirst();
                    if (unavailable.isPresent()) {
                        MealPlanSuggestion result = unavailable.orElseThrow();
                        return new NutritionPlanApi.DraftResponse(false, result.status(), result.note(),
                                null, request.planType(), request.startDate(), null, null, null, List.of());
                    }

                    MealPlanGenerationContext validationContext =
                            new MealPlanGenerationContext(subject, restrictions);
                    for (MealPlanSuggestion suggestion : suggestions) {
                        validateGeneratedPlanForRestrictions(subject, validationContext, suggestion.plan());
                    }
                    MealPlanSuggestion first = suggestions.get(0);
                    List<NutritionPlanApi.DraftDay> days = new ArrayList<>();
                    for (int i = 0; i < suggestions.size(); i++) {
                        GeneratedMealPlan plan = suggestions.get(i).plan();
                        NutritionEstimate totals = emptyEstimate();
                        for (GeneratedMeal meal : plan.meals()) {
                            totals = totals.add(meal.estimatedNutrition(), 1);
                        }
                        days.add(new NutritionPlanApi.DraftDay(request.startDate().plusDays(i),
                                plan.meals(), totals, plan.shoppingList(), plan.alternatives()));
                    }
                    MealPlanGenerationContext context = generationContextRepository.save(
                            new MealPlanGenerationContext(subject, restrictions));
                    return new NutritionPlanApi.DraftResponse(true, "AI_GENERATED",
                            "Review every meal, ingredient, portion, and estimate before saving. Nutrition is an estimate; "
                                    + "AI output cannot establish allergy or medical safety.",
                            context.getId().toString(), request.planType(), request.startDate(),
                            first.plan().planName(), profile.getGoal(), first.plan(), days);
                });
    }

    private CompletableFuture<List<MealPlanSuggestion>> generatePlanDaysSequentially(
            AiMealRequest request, NutritionProfile profile, List<Food> foods,
            int dayCount, List<MealPlanSuggestion> suggestions) {
        if (suggestions.size() == dayCount
                || (!suggestions.isEmpty()
                && (!suggestions.getLast().available() || suggestions.getLast().plan() == null))) {
            return CompletableFuture.completedFuture(suggestions);
        }
        return localLlmMealPlanAssistant.generatePersistentPlanAsync(request, profile, foods)
                .thenCompose(suggestion -> {
                    suggestions.add(suggestion);
                    return generatePlanDaysSequentially(request, profile, foods, dayCount, suggestions);
                });
    }

    @Transactional
    public NutritionPlanApi.PlanView savePersistentPlan(String subject, NutritionPlanApi.SaveRequest request) {
        if (request == null || isBlank(request.generationContextId()) || request.planType() == null
                || request.startDate() == null || isBlank(request.planName()) || request.planName().length() > 120
                || isBlank(request.goal()) || request.goal().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A generation context, plan type, start date, name, and goal are required.");
        }
        int expectedDayCount = request.planType() == NutritionPlan.PlanType.WEEKLY ? 7 : 1;
        if (request.days() == null || request.days().size() != expectedDayCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The submitted plan must contain exactly the selected number of days.");
        }
        MealPlanGenerationContext context = generationContextRepository.findAvailableForUpdate(
                        parseGenerationContextId(request.generationContextId()), subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The generation context is invalid, belongs to another member, or has already been used."));
        Set<String> restrictions = context.getRestrictions();
        if (violatesRestriction(request.planName(), restrictions) || violatesRestriction(request.goal(), restrictions)) {
            throw restrictionConflict();
        }

        NutritionPlan plan = new NutritionPlan(subject, request.planName(), request.goal(),
                request.planType(), request.startDate(), context);
        for (int dayIndex = 0; dayIndex < expectedDayCount; dayIndex++) {
            NutritionPlanApi.DayInput dayInput = request.days().get(dayIndex);
            LocalDate expectedDate = request.startDate().plusDays(dayIndex);
            if (dayInput == null || !expectedDate.equals(dayInput.date())
                    || dayInput.meals() == null || dayInput.meals().isEmpty() || dayInput.meals().size() > 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Plan days must be consecutive from the start date and contain between one and twelve meals.");
            }
            NutritionPlanDay day = new NutritionPlanDay(expectedDate);
            for (NutritionPlanApi.MealInput meal : dayInput.meals()) {
                day.addMeal(toPersistentMeal(subject, context, meal));
            }
            plan.addDay(day);
        }
        context.consume();
        return toPlanView(nutritionPlanRepository.saveAndFlush(plan));
    }

    @Transactional(readOnly = true)
    public List<NutritionPlanApi.PlanView> listPersistentPlans(String subject) {
        return nutritionPlanRepository.findBySubjectOrderByStartDateDescCreatedAtDesc(subject).stream()
                .map(this::toPlanView).toList();
    }

    @Transactional(readOnly = true)
    public NutritionPlanApi.PlanView getPersistentPlan(String subject, Long planId) {
        return nutritionPlanRepository.findByIdAndSubject(planId, subject)
                .map(this::toPlanView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutrition plan not found."));
    }

    public CompletableFuture<NutritionPlanApi.PlanView> regeneratePlanDay(
            String subject, Long planId, LocalDate date, long expectedVersion) {
        PlanGenerationSnapshot snapshot = transactionTemplate.execute(status -> {
            NutritionPlan plan = nutritionPlanRepository.findByIdAndSubject(planId, subject)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutrition plan not found."));
            if (plan.getPlanType() != NutritionPlan.PlanType.WEEKLY
                    || date == null || plan.getDays().stream().noneMatch(day -> day.getPlanDate().equals(date))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The requested day is not in this weekly plan.");
            }
            if (plan.getVersion() != expectedVersion) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The plan changed; reload it before regenerating a day.");
            }
            return generationSnapshot(subject, plan, date, null, expectedVersion);
        });
        return generatePlanDay(snapshot).thenApply(suggestion -> {
            if (!suggestion.available() || suggestion.plan() == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, suggestion.note());
            }
            return transactionTemplate.execute(status -> {
                NutritionPlan plan = lockPlan(subject, planId, expectedVersion);
                NutritionPlanDay day = findDay(plan, date);
                MealPlanGenerationContext context = plan.getGenerationContext();
                validatePlanMeals(subject, context, suggestion.plan().planName(), suggestion.plan().goal(),
                        suggestion.plan().meals().stream().map(this::asMealInput).toList());
                day.replaceMeals(persistedMeals(subject, context,
                        suggestion.plan().meals().stream().map(this::asMealInput).toList()));
                plan.markChanged();
                return toPlanView(nutritionPlanRepository.saveAndFlush(plan));
            });
        });
    }

    public CompletableFuture<NutritionPlanApi.PlanView> replacePlanMeal(
            String subject, Long planId, Long mealId, long expectedVersion) {
        PlanGenerationSnapshot snapshot = transactionTemplate.execute(status -> {
            NutritionPlan plan = nutritionPlanRepository.findByIdAndSubject(planId, subject)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutrition plan not found."));
            if (plan.getVersion() != expectedVersion) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The plan changed; reload it before replacing a meal.");
            }
            NutritionPlanDay containingDay = plan.getDays().stream()
                    .filter(day -> day.getMeals().stream().anyMatch(meal -> meal.getId().equals(mealId)))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found in this plan."));
            NutritionPlanMeal oldMeal = containingDay.getMeals().stream()
                    .filter(meal -> meal.getId().equals(mealId)).findFirst().orElseThrow();
            return generationSnapshot(subject, plan, containingDay.getPlanDate(), oldMeal.getMealType(), expectedVersion);
        });
        return generatePlanDay(snapshot).thenApply(suggestion -> {
            if (!suggestion.available() || suggestion.plan() == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, suggestion.note());
            }
            GeneratedMeal replacement = suggestion.plan().meals().stream()
                    .filter(meal -> meal.mealType() == snapshot.mealType()).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The generated response did not contain a replacement of the requested meal type."));
            return transactionTemplate.execute(status -> {
                NutritionPlan plan = lockPlan(subject, planId, expectedVersion);
                NutritionPlanDay day = findDay(plan, snapshot.date());
                MealPlanGenerationContext context = plan.getGenerationContext();
                NutritionPlanApi.MealInput input = asMealInput(replacement);
                validatePlanMeals(subject, context, replacement.name(), plan.getGoal(), List.of(input));
                day.replaceMeal(mealId, toPersistentMeal(subject, context, input));
                plan.markChanged();
                return toPlanView(nutritionPlanRepository.saveAndFlush(plan));
            });
        });
    }

    @Transactional
    public NutritionPlanApi.PlanView editPlanMeal(
            String memberSubject, Long planId, LocalDate date, Long mealId,
            NutritionPlanApi.EditMealRequest request) {
        if (request == null || request.meal() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Edited meal details are required.");
        }
        NutritionPlan plan = lockPlan(memberSubject, planId, request.expectedVersion());
        NutritionPlanDay day = findDay(plan, date);
        if (day.getMeals().stream().noneMatch(meal -> meal.getId().equals(mealId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found in this plan day.");
        }
        MealPlanGenerationContext context = plan.getGenerationContext();
        validatePlanMeals(memberSubject, context, plan.getName(), plan.getGoal(), List.of(request.meal()));
        day.replaceMeal(mealId, toPersistentMeal(memberSubject, context, request.meal()));
        plan.markChanged();
        return toPlanView(nutritionPlanRepository.saveAndFlush(plan));
    }

    private CompletableFuture<MealPlanSuggestion> generatePlanDay(PlanGenerationSnapshot snapshot) {
        NutritionProfile profile = nutritionProfileRepository.findFirstBySubjectOrderByCreatedAtDesc(snapshot.subject())
                .orElse(null);
        if (profile == null || profile.getTargetCalories() == null || profile.getTargetCalories() <= 0) {
            return CompletableFuture.completedFuture(new MealPlanSuggestion(false, "UNAVAILABLE", null,
                    "A saved nutrition profile with a calorie target is required."));
        }
        List<String> restrictions = effectiveRestrictions(profile, List.copyOf(snapshot.restrictions()));
        return localLlmMealPlanAssistant.generatePersistentPlanAsync(
                aiMealRequest(profile, profile.getGoal(), profile.getTargetCalories(),
                        profile.getDietaryPreference(), restrictions),
                profile, foodRepository.findBySubjectOrderByNameAsc(snapshot.subject()));
    }

    private List<String> effectiveRestrictions(NutritionProfile profile, List<String> requested) {
        LinkedHashSet<String> combined = new LinkedHashSet<>(normalizeRestrictions(requested));
        if (profile != null) {
            combined.addAll(profile.getAllergies());
            combined.addAll(profile.getIntolerances());
            combined.addAll(profile.getDislikedFoods());
        }
        return List.copyOf(combined);
    }

    private AiMealRequest aiMealRequest(NutritionProfile profile, String goal, Integer targetCalories,
                                        String dietStyle, List<String> restrictions) {
        return new AiMealRequest(goal, targetCalories, dietStyle, restrictions,
                profile == null ? List.of() : List.copyOf(profile.getDislikedFoods()),
                profile == null ? List.of() : List.copyOf(profile.getPreferredIngredients()),
                profile == null ? null : profile.getBudgetPreference(),
                profile == null ? null : profile.getMaxPreparationMinutes(),
                profile == null ? null : profile.getCookingPreference());
    }

    private PlanGenerationSnapshot generationSnapshot(
            String subject, NutritionPlan plan, LocalDate date,
            MealPlan.MealType mealType, long expectedVersion) {
        MealPlanGenerationContext context = plan.getGenerationContext();
        if (context == null || !context.getSubject().equals(subject)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The saved plan has no valid member generation context.");
        }
        return new PlanGenerationSnapshot(subject, plan.getPlanType(), date, mealType,
                expectedVersion, context.getRestrictions());
    }

    private NutritionPlan lockPlan(String subject, Long planId, long expectedVersion) {
        NutritionPlan plan = nutritionPlanRepository.findOwnedForUpdate(planId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutrition plan not found."));
        if (plan.getVersion() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The plan changed during generation; reload it before applying this result.");
        }
        return plan;
    }

    private NutritionPlanDay findDay(NutritionPlan plan, LocalDate date) {
        return plan.getDays().stream().filter(day -> day.getPlanDate().equals(date)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan day not found."));
    }

    private List<NutritionPlanMeal> persistedMeals(
            String subject, MealPlanGenerationContext context, List<NutritionPlanApi.MealInput> meals) {
        return meals.stream().map(meal -> toPersistentMeal(subject, context, meal)).toList();
    }

    private NutritionPlanMeal toPersistentMeal(
            String subject, MealPlanGenerationContext context, NutritionPlanApi.MealInput meal) {
        validatePlanMeals(subject, context, meal == null ? null : meal.name(),
                meal == null ? null : meal.name(), meal == null ? null : List.of(meal));
        List<NutritionPlanMeal.NutritionPlanMealItem> foods = meal.foods().stream()
                .map(food -> new NutritionPlanMeal.NutritionPlanMealItem(
                        food.foodId(), food.name().trim(), food.servings()))
                .toList();
        String source = meal.nutritionSource() == null || meal.nutritionSource().isBlank()
                ? "REVIEW_REQUIRED" : meal.nutritionSource();
        return new NutritionPlanMeal(meal.name().trim(), meal.mealType(), meal.servingSizeGrams(),
                meal.preparationInstructions().trim(), meal.estimatedNutrition(), source, foods);
    }

    private void validatePlanMeals(String subject, MealPlanGenerationContext context, String planName,
                                   String goal, List<NutritionPlanApi.MealInput> meals) {
        if (context == null || !context.getSubject().equals(subject)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid member-owned generation context is required for this plan.");
        }
        Set<String> restrictions = context.getRestrictions();
        if (violatesRestriction(planName, restrictions) || violatesRestriction(goal, restrictions)) {
            throw restrictionConflict();
        }
        if (meals == null || meals.isEmpty() || meals.size() > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each plan day must contain between one and twelve meals.");
        }
        for (NutritionPlanApi.MealInput meal : meals) {
            if (meal == null || isBlank(meal.name()) || meal.name().length() > 120
                    || meal.mealType() == null || meal.servingSizeGrams() == null
                    || meal.servingSizeGrams() <= 0 || meal.servingSizeGrams() > 2500
                    || isBlank(meal.preparationInstructions()) || meal.preparationInstructions().length() > 2000
                    || meal.foods() == null || meal.foods().isEmpty() || meal.foods().size() > 30
                    || !validGeneratedNutrition(meal.estimatedNutrition())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each meal requires a name, type, serving size, instructions, ingredients, and valid estimated nutrition.");
            }
            if (meal.nutritionSource() != null && !Set.of("SAVED_FOOD_DATA", "MODEL_ESTIMATE",
                    "REVIEW_REQUIRED").contains(meal.nutritionSource())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The meal nutrition source is not supported.");
            }
            if (violatesRestriction(meal.name(), restrictions)
                    || violatesRestriction(meal.preparationInstructions(), restrictions)) {
                throw restrictionConflict();
            }
            for (GeneratedFood item : meal.foods()) {
                if (item == null || isBlank(item.name()) || item.name().length() > 150
                        || item.servings() == null
                        || !isPositiveFinite(item.servings()) || violatesRestriction(item.name(), restrictions)) {
                    throw restrictionConflict();
                }
                if (item.foodId() != null) {
                    Food savedFood = foodRepository.findByIdAndSubject(item.foodId(), subject)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "A selected saved food does not belong to this member."));
                    if (violatesRestriction(savedFood.getName() + " " + savedFood.getBrand(), restrictions)) {
                        throw restrictionConflict();
                    }
                }
            }
        }
    }

    private void validateGeneratedPlanForRestrictions(
            String subject, MealPlanGenerationContext context, GeneratedMealPlan plan) {
        if (plan == null || plan.shoppingList() == null || plan.alternatives() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The generated response is missing valid shopping-list or alternative data.");
        }
        validatePlanMeals(subject, context, plan.planName(), plan.goal(),
                plan.meals() == null ? null : plan.meals().stream().map(this::asMealInput).toList());
        if (plan.shoppingList().stream().anyMatch(item -> violatesRestriction(item, context.getRestrictions()))
                || plan.alternatives().stream().anyMatch(item -> violatesRestriction(item, context.getRestrictions()))) {
            throw restrictionConflict();
        }
    }

    private NutritionPlanApi.MealInput asMealInput(GeneratedMeal meal) {
        return new NutritionPlanApi.MealInput(meal.name(), meal.mealType(), meal.servingSizeGrams(),
                meal.preparationInstructions(), meal.foods(), meal.estimatedNutrition(), meal.nutritionSource());
    }

    private NutritionPlanApi.PlanView toPlanView(NutritionPlan plan) {
        List<NutritionPlanApi.DayView> days = plan.getDays().stream()
                .sorted(java.util.Comparator.comparing(NutritionPlanDay::getPlanDate))
                .map(day -> new NutritionPlanApi.DayView(day.getId(), day.getPlanDate(),
                        day.getMeals().stream().sorted(java.util.Comparator.comparingInt(
                                        NutritionPlanMeal::getPosition))
                                .map(meal -> new NutritionPlanApi.MealView(meal.getId(), meal.getName(),
                                        meal.getMealType(), meal.getServingSizeGrams(),
                                        meal.getPreparationInstructions(), new NutritionEstimate(meal.getCalories(),
                                        meal.getProteinGrams(), meal.getCarbohydrateGrams(), meal.getFatGrams(),
                                        meal.getFiberGrams(), meal.getSodiumMg()), meal.getNutritionSource(),
                                        meal.getFoods().stream().map(item -> new NutritionPlanApi.FoodView(
                                                item.getFoodId(), item.getFoodName(), item.getServings())).toList()))
                                .toList()))
                .toList();
        return new NutritionPlanApi.PlanView(plan.getId(), plan.getName(), plan.getGoal(), plan.getPlanType(),
                plan.getStartDate(), plan.getEndDate(), plan.getVersion(), days);
    }

    private NutritionEstimate emptyEstimate() {
        return new NutritionEstimate(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private ResponseStatusException restrictionConflict() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The meal conflicts with an ingredient restriction saved for this plan.");
    }

    private record PlanGenerationSnapshot(String subject, NutritionPlan.PlanType planType,
                                          LocalDate date, MealPlan.MealType mealType,
                                          long expectedVersion, Set<String> restrictions) {}

    @Transactional
    public List<MealPlan> saveGeneratedMealPlan(String subject, SaveGeneratedMealPlanRequest request) {
        if (request == null || isBlank(request.generationContextId())
                || isBlank(request.planName()) || request.planName().length() > 50
                || isBlank(request.goal()) || request.goal().length() > 100
                || request.meals() == null || request.meals().isEmpty() || request.meals().size() > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid generation context, plan name, goal, and between one and twelve meals are required.");
        }
        UUID contextId = parseGenerationContextId(request.generationContextId());
        MealPlanGenerationContext context = generationContextRepository
                .findAvailableForUpdate(contextId, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The generation context is invalid, belongs to another member, or has already been used."));
        Set<String> restrictions = context.getRestrictions();
        if (request.restrictions() != null
                && !new LinkedHashSet<>(normalizeRestrictions(request.restrictions())).equals(restrictions)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Restrictions must match the original generation context.");
        }
        if (violatesRestriction(request.planName(), restrictions)
                || violatesRestriction(request.goal(), restrictions)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The reviewed plan contains an explicitly excluded ingredient or restriction.");
        }

        List<ValidatedGeneratedMeal> validatedMeals = new ArrayList<>();
        for (SaveGeneratedMealRequest meal : request.meals()) {
            if (meal == null || isBlank(meal.name()) || meal.name().length() > 120
                    || meal.mealType() == null || meal.preparationInstructions() == null
                    || meal.preparationInstructions().isBlank() || meal.preparationInstructions().length() > 350
                    || meal.servingSizeGrams() == null || meal.servingSizeGrams() <= 0
                    || meal.servingSizeGrams() > 2500
                    || meal.ingredients() == null || meal.ingredients().isEmpty()
                    || meal.ingredients().size() > 30 || !validGeneratedNutrition(meal.estimatedNutrition())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each meal requires a name, type, recipe, serving size, ingredients, and valid estimated nutrition.");
            }
            if (violatesRestriction(meal.name(), restrictions)
                    || violatesRestriction(meal.preparationInstructions(), restrictions)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The reviewed plan contains an explicitly excluded ingredient or restriction.");
            }
            for (GeneratedFood ingredient : meal.ingredients()) {
                if (ingredient == null || isBlank(ingredient.name()) || ingredient.name().length() > 150
                        || ingredient.servings() == null || !isPositiveFinite(ingredient.servings())
                        || violatesRestriction(ingredient.name(), restrictions)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A meal ingredient is invalid or matches an explicitly excluded restriction.");
                }
            }
            CreateFoodRequest generatedFood = new CreateFoodRequest(meal.name(),
                    "AI-generated recipe; nutrition is an estimate", meal.servingSizeGrams(),
                    meal.estimatedNutrition().calories(), meal.estimatedNutrition().proteinGrams(),
                    meal.estimatedNutrition().carbohydrateGrams(), meal.estimatedNutrition().fatGrams(),
                    meal.estimatedNutrition().fiberGrams(), meal.estimatedNutrition().sodiumMg());
            validateFoodRequest(generatedFood);
            validatedMeals.add(new ValidatedGeneratedMeal(meal, generatedFood));
        }

        context.consume();
        List<MealPlan> savedPlans = new ArrayList<>();
        for (ValidatedGeneratedMeal validated : validatedMeals) {
            SaveGeneratedMealRequest meal = validated.meal();
            CreateFoodRequest generatedFood = validated.food();
            String ingredients = meal.ingredients().stream()
                    .map(ingredient -> ingredient.name() + " (" + ingredient.servings() + " servings)")
                    .collect(java.util.stream.Collectors.joining(", "));
            String description = "Plan: " + request.planName() + "\nGoal: " + request.goal()
                    + "\nIngredients: " + ingredients + "\nPreparation: " + meal.preparationInstructions();
            if (description.length() > 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The recipe details are too long to save. Shorten the ingredient list or instructions.");
            }
            Food savedFood = foodRepository.save(new Food(subject, generatedFood.name(), generatedFood.brand(),
                    generatedFood.servingSizeGrams(), generatedFood.calories(), generatedFood.proteinGrams(),
                    generatedFood.carbohydrateGrams(), generatedFood.fatGrams(),
                    generatedFood.fiberGrams(), generatedFood.sodiumMg()));
            List<MealPlanItem> items = List.of(new MealPlanItem(savedFood.getId(), 1.0, savedFood.getName()));
            savedPlans.add(mealPlanRepository.save(new MealPlan(subject, meal.name(), description,
                    meal.mealType(), meal.estimatedNutrition().calories(), items, context)));
        }
        return savedPlans;
    }

    private UUID parseGenerationContextId(String value) {
        try {
            UUID contextId = UUID.fromString(value);
            if (!contextId.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return contextId;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid generation context is required.");
        }
    }

    private List<String> normalizeRestrictions(List<String> requestedRestrictions) {
        if (requestedRestrictions != null && requestedRestrictions.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At most twenty generation restrictions may be supplied.");
        }
        LinkedHashSet<String> restrictions = new LinkedHashSet<>(
                List.of("pork", "bacon", "ham", "lard", "pancetta", "prosciutto", "pork sausage", "pork gelatin"));
        for (String restriction : requestedRestrictions == null ? List.<String>of() : requestedRestrictions) {
            if (isBlank(restriction) || restriction.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each generation restriction must contain 1 to 100 characters.");
            }
            restrictions.add(restriction.trim());
        }
        return List.copyOf(restrictions);
    }

    private void validateGeneratedMealRestrictions(
            String subject,
            MealPlanGenerationContext context,
            String name,
            String description,
            List<MealPlanItem> items) {
        if (context == null) {
            return;
        }
        if (!context.getSubject().equals(subject)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The generation context does not match this member's meal.");
        }
        Set<String> restrictions = context.getRestrictions();
        boolean restrictedMealText = violatesRestriction(name, restrictions)
                || violatesRestriction(description, restrictions)
                || items.stream().anyMatch(item -> violatesRestriction(item.getFoodName(), restrictions));
        if (restrictedMealText) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The meal edit conflicts with a restriction saved for its generation.");
        }
    }

    private boolean validGeneratedNutrition(NutritionEstimate nutrition) {
        return nutrition != null && nutrition.calories() != null && nutrition.proteinGrams() != null
                && nutrition.carbohydrateGrams() != null && nutrition.fatGrams() != null
                && nutrition.fiberGrams() != null && nutrition.sodiumMg() != null
                && isNonNegativeFinite(nutrition.calories())
                && isNonNegativeFinite(nutrition.proteinGrams())
                && isNonNegativeFinite(nutrition.carbohydrateGrams())
                && isNonNegativeFinite(nutrition.fatGrams())
                && isNonNegativeFinite(nutrition.fiberGrams())
                && isNonNegativeFinite(nutrition.sodiumMg());
    }

    private record ValidatedGeneratedMeal(SaveGeneratedMealRequest meal, CreateFoodRequest food) {}

    private boolean violatesRestriction(String text, Collection<String> restrictions) {
        if (text == null || restrictions == null || restrictions.isEmpty()) {
            return false;
        }
        Set<String> words = restrictionWords(text);
        return restrictions.stream()
                .flatMap(value -> restrictionWords(value).stream())
                .filter(value -> !List.of("allergy", "allergic", "intolerance", "intolerant",
                        "avoid", "avoidance", "free", "to", "and").contains(value))
                .anyMatch(restrictedWord -> words.stream()
                        .anyMatch(word -> singularize(word).equals(singularize(restrictedWord))));
    }

    private Set<String> restrictionWords(String text) {
        return new LinkedHashSet<>(List.of(text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim().split("\\s+")));
    }

    private String singularize(String word) {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
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

    private void validateHydrationRequest(HydrationRequest request) {
        if (request == null || request.waterMl() == null
                || request.waterMl() < 1
                || request.waterMl() > MAX_SINGLE_WATER_LOG_ML) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A water amount from 1 to 2,000 mL is required.");
        }
        if (request.consumedAt() == null) return;
        Instant now = Instant.now();
        if (request.consumedAt().isBefore(now.minus(Duration.ofDays(365L * 5)))
                || request.consumedAt().isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hydration timestamps must be within the past five years and no more than five minutes in the future.");
        }
    }

    private void validateDailyHydrationLimit(
            String subject, Instant consumedAt, int waterMl, HydrationLog existing) {
        LocalDate date = consumedAt.atZone(ZoneOffset.UTC).toLocalDate();
        Instant from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        int existingTotal = hydrationLogRepository
                .findBySubjectAndConsumedAtGreaterThanEqualAndConsumedAtLessThanOrderByConsumedAtAsc(
                        subject, from, to)
                .stream().mapToInt(HydrationLog::getWaterMl).sum();
        if (existing != null && existing.getConsumedAt().atZone(ZoneOffset.UTC).toLocalDate().equals(date)) {
            existingTotal -= existing.getWaterMl();
        }
        if ((long) existingTotal + waterMl > MAX_DAILY_WATER_LOG_ML) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Daily drinking-water logs cannot exceed 10,000 mL.");
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
        if ((request.age() != null && (request.age() <= 0 || request.age() > 120))
                || (request.heightCm() != null && !isPositiveFinite(request.heightCm()))
                || (request.weightKg() != null && !isPositiveFinite(request.weightKg()))
                || (request.targetCalories() != null && request.targetCalories() <= 0)
                || (request.targetWeightKg() != null
                    && (!isPositiveFinite(request.targetWeightKg()) || request.targetWeightKg() > 500))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Age, height, current/target weight, and target calories must be within supported positive ranges.");
        }
        if (request.calorieTargetMode() == NutritionProfile.CalorieTargetMode.MANUAL
                && request.targetCalories() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A positive targetCalories value is required for a manual calorie target.");
        }
        if (request.targetDate() != null && request.targetWeightKg() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A desired target date requires a saved target weight.");
        }
        if (request.budgetPreference() != null
                && !Set.of("LOW", "MODERATE", "FLEXIBLE").contains(request.budgetPreference())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Budget preference must be LOW, MODERATE, or FLEXIBLE.");
        }
        if (request.maxPreparationMinutes() != null
                && (request.maxPreparationMinutes() < 5 || request.maxPreparationMinutes() > 240)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Preparation time must be between 5 and 240 minutes.");
        }
        if (request.cookingPreference() != null && request.cookingPreference().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cooking preferences must be at most 500 characters.");
        }
        normalizeProfilePreferences(request.allergies(), "allergies");
        normalizeProfilePreferences(request.intolerances(), "intolerances");
        normalizeProfilePreferences(request.dislikedFoods(), "disliked foods");
        normalizeProfilePreferences(request.preferredIngredients(), "preferred ingredients");
    }

    private void validateGoalTarget(Double targetWeightKg, LocalDate targetDate,
                                    Double currentWeightKg, Double heightCm, Integer age, String goal) {
        if (targetWeightKg == null) {
            if (targetDate != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A desired target date requires a saved target weight.");
            }
            return;
        }
        if (age == null || age < 18) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Target-weight validation and adult BMI screening are not supported for minors.");
        }
        if (heightCm == null || !isPositiveFinite(heightCm)
                || heightCm > 300 || currentWeightKg == null
                || !isPositiveFinite(currentWeightKg) || currentWeightKg > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid adult age, current weight, and height are required before saving a target weight.");
        }
        if (targetDate != null && !targetDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The desired target date must be in the future.");
        }
        double targetBmi = targetWeightKg / Math.pow(heightCm / 100.0, 2);
        if (!Double.isFinite(targetBmi) || targetBmi < 18.5) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The target implies an adult BMI below 18.5. This BMI screening threshold is not a diagnosis; choose a different target or consult a qualified professional.");
        }
        String normalizedGoal = goal == null ? "" : goal.trim().toLowerCase(Locale.ROOT);
        boolean supported = switch (normalizedGoal) {
            case "weight loss" -> targetWeightKg < currentWeightKg;
            case "muscle gain" -> targetWeightKg > currentWeightKg;
            case "maintenance" -> Math.abs(targetWeightKg - currentWeightKg)
                    <= Math.max(2.0, currentWeightKg * 0.05);
            default -> false;
        };
        if (!supported) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Target weight must agree with a supported weight-loss, maintenance, or muscle-gain goal.");
        }
    }

    private Set<String> normalizeProfilePreferences(List<String> values, String field) {
        if (values == null) {
            return Set.of();
        }
        if (values.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At most twenty " + field + " may be saved.");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (isBlank(value) || value.trim().length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each entry in " + field + " must contain 1 to 100 characters.");
            }
            normalized.add(value.trim());
        }
        return normalized;
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
                                         Double weightKg, Integer targetCalories,
                                         NutritionProfile.CalorieTargetMode calorieTargetMode,
                                         NutritionProfile.EnergyEquationSex energyEquationSex,
                                         Double targetWeightKg, LocalDate targetDate,
                                         List<String> allergies, List<String> intolerances,
                                         List<String> dislikedFoods, List<String> preferredIngredients,
                                         String budgetPreference, Integer maxPreparationMinutes,
                                         String cookingPreference) {}

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

    public record GoalTimelineResult(boolean available, Long minimumWeeks, Long maximumWeeks,
                                     LocalDate earliestEstimatedDate, LocalDate latestEstimatedDate,
                                     LocalDate requestedDate, String requestedDateStatus,
                                     String note, String assumptions) {
        private static GoalTimelineResult unavailable(LocalDate requestedDate, String note) {
            return new GoalTimelineResult(false, null, null, null, null, requestedDate,
                    "UNAVAILABLE", note, "No suitable deterministic calculation is available for this goal.");
        }
    }

    public record HydrationRequest(Instant consumedAt, Integer waterMl) {}

    public record HydrationLogView(Long id, Instant consumedAt, int waterMl) {}

    public record HydrationView(LocalDate date, int consumedMl, int targetMl,
                                List<HydrationLogView> logs, String targetAssumptions) {}

    public record MacroTargetResult(int calorieTarget, double proteinGrams, double carbohydrateGrams,
                                    double fatGrams, double estimatedCalories, String method) {}

    public enum GoalCategory {
        WEIGHT_LOSS,
        MAINTENANCE,
        MUSCLE_GAIN
    }

    public record EnergyEstimateResult(boolean available, Double bmrCaloriesPerDay,
                                       Double tdeeCaloriesPerDay, String goalCategory,
                                       Double activityFactor, Integer goalAdjustedCalories,
                                       String note) {}

    public record AiMealRequest(String goal, Integer targetCalories, String dietStyle,
                                List<String> restrictions, List<String> dislikedFoods,
                                List<String> preferredIngredients, String budgetPreference,
                                Integer maxPreparationMinutes, String cookingPreference) {
        public AiMealRequest(String goal, Integer targetCalories, String dietStyle, List<String> restrictions) {
            this(goal, targetCalories, dietStyle, restrictions, List.of(), List.of(), null, null, null);
        }
    }

    public record GeneratedFood(String name, Double servings, Long foodId) {}

    public record NutritionEstimate(Double calories, Double proteinGrams,
                                    Double carbohydrateGrams, Double fatGrams,
                                    Double fiberGrams, Double sodiumMg) {
        public NutritionEstimate add(NutritionEstimate other, double servings) {
            NutritionEstimate total = new NutritionEstimate(calories + other.calories * servings,
                    proteinGrams + other.proteinGrams * servings,
                    carbohydrateGrams + other.carbohydrateGrams * servings,
                    fatGrams + other.fatGrams * servings,
                    fiberGrams + other.fiberGrams * servings,
                    sodiumMg + other.sodiumMg * servings);
            if (!isEstimateValue(total.calories()) || !isEstimateValue(total.proteinGrams())
                    || !isEstimateValue(total.carbohydrateGrams()) || !isEstimateValue(total.fatGrams())
                    || !isEstimateValue(total.fiberGrams()) || !isEstimateValue(total.sodiumMg())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Generated nutrition totals exceed supported numeric limits.");
            }
            return total;
        }

        private static boolean isEstimateValue(Double value) {
            return value != null && Double.isFinite(value) && value >= 0;
        }
    }

    public record GeneratedMeal(String name, MealPlan.MealType mealType, Integer servingSizeGrams,
                                List<GeneratedFood> foods,
                                String preparationInstructions, NutritionEstimate estimatedNutrition,
                                String nutritionSource) {}

    public record GeneratedMealPlan(String planName, String goal, List<GeneratedMeal> meals,
                                    NutritionEstimate dailyNutrition, List<String> shoppingList,
                                    List<String> alternatives) {}

    public record MealPlanSuggestion(boolean available, String status,
                                     GeneratedMealPlan plan, String note,
                                     String generationContextId) {
        public MealPlanSuggestion(boolean available, String status, GeneratedMealPlan plan, String note) {
            this(available, status, plan, note, null);
        }
    }

    public record SaveGeneratedMealPlanRequest(String generationContextId, String planName, String goal,
                                               List<String> restrictions,
                                               List<SaveGeneratedMealRequest> meals) {}

    public record SaveGeneratedMealRequest(String name, MealPlan.MealType mealType,
                                           Integer servingSizeGrams, String preparationInstructions,
                                           List<GeneratedFood> ingredients, NutritionEstimate estimatedNutrition) {}
}
