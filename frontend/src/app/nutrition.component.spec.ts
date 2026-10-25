import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import {
  AiMealPlanResponse,
  BodyMeasurement,
  DailyNutrition,
  HydrationDay,
  NutritionApiService,
  NutritionBmiResult,
  NutritionCalculation,
  NutritionEnergyEstimate,
  NutritionGoalTimeline,
  NutritionFood,
  NutritionJournalEntry,
  NutritionMacroTargets,
  NutritionMealPlan,
  NutritionPlanView,
  NutritionProfile
} from './nutrition-api.service';
import { NutritionComponent } from './nutrition.component';

describe('NutritionComponent', () => {
  let fixture: ComponentFixture<NutritionComponent>;
  let component: NutritionComponent;
  let api: jasmine.SpyObj<NutritionApiService>;

  const food: NutritionFood = {
    id: 3, name: 'Oats', brand: 'Farm', servingSizeGrams: 50, calories: 190,
    proteinGrams: 7, carbohydrateGrams: 32, fatGrams: 4, fiberGrams: 5, sodiumMg: 2
  };
  const profile: NutritionProfile = {
    id: 5, profileName: 'My profile', goal: 'maintenance', dietaryPreference: 'balanced',
    activityLevel: 'moderate', age: 30, heightCm: 170, weightKg: 70, targetCalories: 2100,
    calorieTargetMode: 'MANUAL', energyEquationSex: null,
    targetWeightKg: 65, targetDate: '2027-10-25',
    allergies: ['peanuts'], intolerances: ['lactose'], dislikedFoods: ['olives'],
    preferredIngredients: ['lentils'], budgetPreference: 'LOW',
    maxPreparationMinutes: 30, cookingPreference: 'One-pan cooking'
  };
  const timeline: NutritionGoalTimeline = {
    available: false, minimumWeeks: null, maximumWeeks: null,
    earliestEstimatedDate: null, latestEstimatedDate: null, requestedDate: null,
    requestedDateStatus: 'UNAVAILABLE', note: 'Unavailable', assumptions: 'No estimate'
  };
  const hydrationDay: HydrationDay = {
    date: '2026-04-16', consumedMl: 350, targetMl: 2000,
    logs: [{ id: 17, consumedAt: '2026-04-16T10:00:00Z', waterMl: 350 }],
    targetAssumptions: 'General estimate; drinks only'
  };
  const daily: DailyNutrition = {
    date: '2026-04-16',
    totals: { calories: 500, proteinGrams: 20, carbohydrateGrams: 60, fatGrams: 15, fiberGrams: 8, sodiumMg: 400 },
    meals: [{ mealType: 'BREAKFAST', totals: {
      calories: 500, proteinGrams: 20, carbohydrateGrams: 60, fatGrams: 15, fiberGrams: 8, sodiumMg: 400
    } }]
  };
  const bmi: NutritionBmiResult = { bmi: 24.2, note: 'Within range' };
  const macros: NutritionMacroTargets = {
    calorieTarget: 2100, proteinGrams: 131, carbohydrateGrams: 236, fatGrams: 70,
    estimatedCalories: 2100, method: 'Saved calorie target'
  };
  const energy: NutritionEnergyEstimate = {
    available: false, bmrCaloriesPerDay: null, tdeeCaloriesPerDay: null,
    goalCategory: null, activityFactor: null, goalAdjustedCalories: null,
    note: 'BMR and TDEE are unavailable.'
  };
  const calculation: NutritionCalculation = {
    calories: 190, proteinGrams: 7, carbohydrateGrams: 32, fatGrams: 4, fiberGrams: 5, sodiumMg: 2
  };
  const measurement: BodyMeasurement = {
    id: 8, measurementDate: '2026-04-16', weightKg: 70, waistCm: 80, hipCm: 96,
    chestCm: 90, armCm: 30, thighCm: 54, notes: ''
  };
  const journalEntry: NutritionJournalEntry = {
    id: 9, foodId: 3, mealType: 'BREAKFAST', entryDate: '2026-04-16', servings: 1, notes: ''
  };

  function generatedPlan(): AiMealPlanResponse {
    const meal = {
      name: 'Oat breakfast',
      mealType: 'BREAKFAST' as const,
      servingSizeGrams: 350,
      foods: [{ name: 'Oats', servings: 1, foodId: 3 }],
      preparationInstructions: 'Cook the oats.',
      estimatedNutrition: {
        calories: 340, proteinGrams: 12, carbohydrateGrams: 52, fatGrams: 8, fiberGrams: 6, sodiumMg: 120
      },
      nutritionSource: 'REVIEW_REQUIRED' as const
    };
    const nutrition = {
      calories: 340, proteinGrams: 12, carbohydrateGrams: 52, fatGrams: 8, fiberGrams: 6, sodiumMg: 120
    };
    return {
      available: true,
      status: 'AVAILABLE',
      note: 'Review every meal before saving.',
      generationContextId: '01234567-89ab-cdef-0123-456789abcdef',
      planType: 'DAILY',
      startDate: '2026-04-16',
      days: [{ date: '2026-04-16', meals: [meal], dailyNutrition: nutrition, shoppingList: ['Oats'], alternatives: [] }],
      plan: {
        planName: 'Member plan',
        goal: 'maintenance',
        meals: [meal],
        dailyNutrition: nutrition,
        shoppingList: ['Oats'],
        alternatives: []
      }
    };
  }

  const savedPlan: NutritionPlanView = {
    id: 18, name: 'Member plan', goal: 'maintenance', planType: 'DAILY',
    startDate: '2026-04-16', endDate: '2026-04-16', version: 0,
    days: [{ id: 20, date: '2026-04-16', meals: [{
      id: 21, name: 'Oat breakfast', mealType: 'BREAKFAST', servingSizeGrams: 350,
      preparationInstructions: 'Cook the oats.', estimatedNutrition: {
        calories: 340, proteinGrams: 12, carbohydrateGrams: 52, fatGrams: 8, fiberGrams: 6, sodiumMg: 120
      }, nutritionSource: 'MODEL_ESTIMATE', foods: [{ foodId: 3, name: 'Oats', servings: 1 }]
    }]}]
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<NutritionApiService>('NutritionApiService', [
      'getFoods', 'createFood', 'updateFood', 'deleteFood',
      'getMealPlans', 'getNutritionPlans', 'getNutritionPlan', 'generatePersistentPlan',
      'savePersistentPlan', 'regeneratePlanDay', 'replacePlanMeal',
      'createMealPlan', 'updateMealPlan', 'deleteMealPlan',
      'getJournal', 'createJournalEntry', 'updateJournalEntry', 'deleteJournalEntry',
      'getMeasurements', 'createMeasurement', 'updateMeasurement', 'deleteMeasurement',
      'getProfile', 'saveProfile', 'calculateNutrition', 'getDailyNutrition',
      'calculateBmi', 'calculateMacroTargets', 'calculateEnergyEstimate', 'calculateGoalTimeline',
      'getHydrationDay', 'getHydrationHistory', 'addHydrationLog', 'correctHydrationLog', 'deleteHydrationLog',
      'generatePlan', 'saveGeneratedPlan'
    ]);
    api.getFoods.and.returnValue(of([food]));
    api.getMealPlans.and.returnValue(of([]));
    api.getNutritionPlans.and.returnValue(of([]));
    api.getJournal.and.returnValue(of([journalEntry]));
    api.getMeasurements.and.returnValue(of([measurement]));
    api.getProfile.and.returnValue(of(profile));
    api.calculateGoalTimeline.and.returnValue(of(timeline));
    api.getHydrationDay.and.returnValue(of(hydrationDay));
    api.saveProfile.and.returnValue(of(profile));
    api.createFood.and.returnValue(of(food));
    api.updateFood.and.returnValue(of(food));
    api.deleteFood.and.returnValue(of(void 0));
    api.createMealPlan.and.returnValue(of({ id: 2, name: 'Breakfast', description: '',
      mealType: 'BREAKFAST', targetCalories: 400, foods: [] }));
    api.updateMealPlan.and.returnValue(of({ id: 2, name: 'Breakfast', description: '',
      mealType: 'BREAKFAST', targetCalories: 400, foods: [] }));
    api.deleteMealPlan.and.returnValue(of(void 0));
    api.createJournalEntry.and.returnValue(of(journalEntry));
    api.updateJournalEntry.and.returnValue(of(journalEntry));
    api.deleteJournalEntry.and.returnValue(of(void 0));
    api.createMeasurement.and.returnValue(of(measurement));
    api.updateMeasurement.and.returnValue(of(measurement));
    api.deleteMeasurement.and.returnValue(of(void 0));
    api.calculateNutrition.and.returnValue(of(calculation));
    api.getDailyNutrition.and.returnValue(of(daily));
    api.calculateBmi.and.returnValue(of(bmi));
    api.calculateMacroTargets.and.returnValue(of(macros));
    api.calculateEnergyEstimate.and.returnValue(of(energy));
    api.generatePersistentPlan.and.returnValue(of(generatedPlan()));
    api.saveGeneratedPlan.and.returnValue(of([]));
    api.savePersistentPlan.and.returnValue(of(savedPlan));
    api.regeneratePlanDay.and.returnValue(of(savedPlan));
    api.replacePlanMeal.and.returnValue(of(savedPlan));

    await TestBed.configureTestingModule({
      imports: [NutritionComponent],
      providers: [provideRouter([]), { provide: NutritionApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(NutritionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads nutrition records and displays profile, food, totals, and unavailable energy estimates', () => {
    expect(component.loading).toBeFalse();
    expect(component.foods).toEqual([food]);
    expect(component.profile?.profileName).toBe('My profile');
    expect(component.dailyNutrition).toEqual(daily);
    expect(component.hydration).toEqual(hydrationDay);
    expect(component.goalTimeline).toEqual(timeline);
    expect(component.bmiEstimate).toEqual(bmi);
    expect(component.macroTargets).toEqual(macros);
    expect(component.energyEstimate?.available).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Oats');
    expect(fixture.nativeElement.textContent).toContain('BMR and TDEE are unavailable.');
  });

  it('keeps the page usable with an absent profile and renders empty list states', async () => {
    api.getProfile.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    api.getFoods.and.returnValue(of([]));
    api.getJournal.and.returnValue(of([]));
    api.getMeasurements.and.returnValue(of([]));
    fixture = TestBed.createComponent(NutritionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.profile).toBeNull();
    expect(component.foods).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('No measurements saved yet.');
    expect(fixture.nativeElement.textContent).toContain('Your latest entries will appear here');
  });

  it('shows a clear page error when a required nutrition list fails to load', () => {
    api.getFoods.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture = TestBed.createComponent(NutritionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    fixture.detectChanges();

    expect(component.loading).toBeFalse();
    expect(component.pageError).toContain('Could not load your nutrition records');
  });

  it('marks required profile fields and saves supported profile values', () => {
    const nameInput = fixture.nativeElement.querySelector('#profile [name="profileName"]') as HTMLInputElement;
    expect(nameInput.required).toBeTrue();
    component.profileForm = {
      ...component.profileForm, profileName: 'Updated profile',
      targetCalories: 2200, calorieTargetMode: 'MANUAL'
    };
    component.profileForm.targetWeightKg = 64;
    component.profileForm.allergiesText = 'peanuts, sesame';
    component.profileForm.preferredIngredientsText = 'lentils, oats';

    component.saveProfile();

    expect(api.saveProfile).toHaveBeenCalledWith(jasmine.objectContaining({
      targetWeightKg: 64, targetDate: '2027-10-25',
      allergies: ['peanuts', 'sesame'], intolerances: ['lactose'],
      dislikedFoods: ['olives'], preferredIngredients: ['lentils', 'oats'],
      budgetPreference: 'LOW', maxPreparationMinutes: 30,
      cookingPreference: 'One-pan cooking'
    }));
    expect(component.successMessage).toBe('Your supported profile details were saved.');
    expect(component.savingProfile).toBeFalse();
  });

  it('loads persisted target and preference values and saves water logs with loading and error states', () => {
    expect(component.profileForm.targetWeightKg).toBe(65);
    expect(component.profileForm.targetDate).toBe('2027-10-25');
    expect(component.profileForm.allergiesText).toBe('peanuts');
    expect(component.hydrationProgressPercent).toBe(18);

    const pending = new Subject<HydrationDay>();
    api.addHydrationLog.and.returnValue(pending);
    component.hydrationWaterMl = 300;
    component.saveHydrationLog();
    expect(component.isSavingHydration).toBeTrue();
    expect(api.addHydrationLog).toHaveBeenCalledWith({ waterMl: 300 });
    pending.next({ ...hydrationDay, consumedMl: 650 });
    pending.complete();
    expect(component.isSavingHydration).toBeFalse();
    expect(component.hydration?.consumedMl).toBe(650);

    api.addHydrationLog.and.returnValue(throwError(() => ({ error: { message: 'Invalid water amount' } })));
    component.saveHydrationLog();
    expect(component.hydrationError).toBe('Invalid water amount');
    expect(component.isSavingHydration).toBeFalse();
  });

  it('corrects a saved hydration log and rejects unreasonable client input', () => {
    api.correctHydrationLog.and.returnValue(of(hydrationDay));
    component.editHydrationLog(hydrationDay.logs[0]);
    component.hydrationWaterMl = 500;
    component.saveHydrationLog();
    expect(api.correctHydrationLog).toHaveBeenCalledWith(17, {
      consumedAt: '2026-04-16T10:00:00Z', waterMl: 500
    });
    component.hydrationWaterMl = 2501;
    component.saveHydrationLog();
    expect(component.hydrationError).toContain('1 and 2,000');
    expect(api.addHydrationLog).not.toHaveBeenCalled();
  });

  it('reports profile save errors and clears the saving state', () => {
    api.saveProfile.and.returnValue(throwError(() => ({ error: { message: 'Invalid target' } })));
    component.saveProfile();

    expect(component.profileError).toBe('Invalid target');
    expect(component.savingProfile).toBeFalse();
  });

  it('creates and updates foods through the corresponding operations', () => {
    component.foodForm.name = 'Oats';
    const newFood = { ...component.foodForm };
    component.saveFood();
    expect(api.createFood).toHaveBeenCalledWith(newFood);

    component.editFood(food);
    expect(component.editingFoodId).toBe(3);
    component.foodForm.name = 'Updated oats';
    const updatedFood = { ...component.foodForm };
    component.saveFood();
    expect(api.updateFood).toHaveBeenCalledWith(3, updatedFood);
    expect(component.editingFoodId).toBeNull();
  });

  it('shows a conflict message when a referenced food cannot be deleted', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    api.deleteFood.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { message: 'Referenced' }
    })));

    component.deleteFood(food);

    expect(api.deleteFood).toHaveBeenCalledWith(3);
    expect(component.foodError).toContain('referenced by a meal plan or journal entry');
  });

  it('filters foods by case-insensitive name and brand', () => {
    component.foods = [food, { ...food, id: 4, name: 'Rice', brand: 'Grain House' }];
    component.foodSearch = 'GRAIN';
    expect(component.filteredFoods.map((item) => item.id)).toEqual([4]);
  });

  it('creates and updates meal plans with selected food quantities', () => {
    component.mealPlanForm = { name: 'Breakfast', description: '', mealType: 'BREAKFAST', targetCalories: 400 };
    component.mealPlanItems = [{ foodId: 3, servings: 1.5 }];
    const createPayload = { ...component.mealPlanForm, foods: [{ foodId: 3, servings: 1.5 }] };
    component.saveMealPlan();
    expect(api.createMealPlan).toHaveBeenCalledWith(createPayload);

    const plan: NutritionMealPlan = {
      id: 14, name: 'Plan', description: 'Details', mealType: 'LUNCH',
      targetCalories: 600, foods: [{ foodId: 3, servings: 2, foodName: 'Oats' }]
    };
    component.editMealPlan(plan);
    component.saveMealPlan();
    expect(api.updateMealPlan).toHaveBeenCalledWith(14, {
      name: 'Plan', description: 'Details', mealType: 'LUNCH', targetCalories: 600,
      foods: [{ foodId: 3, servings: 2 }]
    });

    spyOn(window, 'confirm').and.returnValue(true);
    component.deleteMealPlan(plan);
    expect(api.deleteMealPlan).toHaveBeenCalledWith(14);
  });

  it('creates, updates, filters by date, and deletes journal entries', () => {
    component.journalForm = { ...component.journalForm, foodId: 3, entryDate: '2026-04-16', servings: 2 };
    const createPayload = { ...component.journalForm };
    component.saveJournalEntry();
    expect(api.createJournalEntry).toHaveBeenCalledWith(createPayload);
    expect(component.journalForSelectedDate).toEqual([journalEntry]);

    component.editJournalEntry(journalEntry);
    component.journalForm.notes = 'Updated';
    const updatePayload = { ...component.journalForm };
    component.saveJournalEntry();
    expect(api.updateJournalEntry).toHaveBeenCalledWith(9, updatePayload);

    spyOn(window, 'confirm').and.returnValue(true);
    component.deleteJournalEntry(journalEntry);
    expect(api.deleteJournalEntry).toHaveBeenCalledWith(9);
  });

  it('loads daily totals for the selected date and handles unavailable totals', () => {
    component.dailyNutritionDate = '2026-04-17';
    component.loadDailyNutrition();
    expect(api.getDailyNutrition).toHaveBeenCalledWith('2026-04-17');
    expect(component.dailyNutrition).toEqual(daily);

    api.getDailyNutrition.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.loadDailyNutrition();
    expect(component.dailyNutrition).toBeNull();
    expect(component.pageError).toContain('Could not load daily nutrition totals');
  });

  it('creates and updates body measurements and presents ordered chart data', () => {
    component.measurementForm = { ...component.measurementForm, weightKg: 71, waistCm: 81 };
    const createPayload = { ...component.measurementForm };
    component.saveMeasurement();
    expect(api.createMeasurement).toHaveBeenCalledWith(createPayload);

    const older = { ...measurement, id: 7, measurementDate: '2026-04-10', weightKg: 72 };
    component.measurements = [measurement, older];
    expect(component.chartStartDate).toBe('2026-04-10');
    expect(component.chartEndDate).toBe('2026-04-16');
    expect(component.weightChartDots).toHaveSize(2);

    component.editMeasurement(measurement);
    const updatePayload = { ...component.measurementForm };
    component.saveMeasurement();
    expect(api.updateMeasurement).toHaveBeenCalledWith(8, updatePayload);
    spyOn(window, 'confirm').and.returnValue(true);
    component.deleteMeasurement(measurement);
    expect(api.deleteMeasurement).toHaveBeenCalledWith(8);
  });

  it('calculates selected saved-food servings and rejects invalid selection locally', () => {
    component.calculationFoodId = 0;
    component.calculateSelectedFood();
    expect(api.calculateNutrition).not.toHaveBeenCalled();

    component.calculationFoodId = 3;
    component.calculationServings = 1.5;
    component.calculateSelectedFood();
    expect(api.calculateNutrition).toHaveBeenCalledWith([3], [1.5]);
    expect(component.calculation).toEqual(calculation);
  });

  it('prevents duplicate AI generation and sends trimmed explicit restrictions', () => {
    const response = new Subject<AiMealPlanResponse>();
    api.generatePersistentPlan.and.returnValue(response);
    component.aiRestrictions = ' peanuts, , shellfish ';

    component.generateAiPlan();
    component.generateAiPlan();
    expect(api.generatePersistentPlan).toHaveBeenCalledTimes(1);
    expect(api.generatePersistentPlan).toHaveBeenCalledWith(jasmine.objectContaining({
      planType: 'DAILY', restrictions: ['peanuts', 'shellfish']
    }));
    expect(component.isGeneratingAiPlan).toBeTrue();
    response.next(generatedPlan());
    expect(component.isGeneratingAiPlan).toBeFalse();
  });

  it('shows unavailable AI responses and maps service errors to safe messages', () => {
    api.generatePersistentPlan.and.returnValue(of({
      available: false, status: 'UNAVAILABLE', plan: null, note: 'No model is configured.',
      generationContextId: null
    }));
    component.generateAiPlan();
    fixture.detectChanges();
    expect(component.aiPlan?.available).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('No model is configured.');

    api.generatePersistentPlan.and.returnValue(throwError(() => ({ status: 503 })));
    component.generateAiPlan();
    expect(component.aiError).toContain('temporarily busy or unavailable');
  });

  it('does not generate without a saved profile and reflects generation errors', () => {
    component.profile = null;
    component.generateAiPlan();
    expect(api.generatePersistentPlan).not.toHaveBeenCalled();

    component.profile = profile;
    api.generatePersistentPlan.and.returnValue(throwError(() => ({ status: 401 })));
    component.generateAiPlan();
    expect(component.aiError).toContain('session has expired');
  });

  it('allows members to edit generated ingredients and quantities without saved food matches', () => {
    const response = generatedPlan();
    response.days![0].meals[0].foods[0].foodId = null;
    response.days![0].meals[0].nutritionSource = 'MODEL_ESTIMATE';
    component.aiPlan = response;

    expect(component.canSaveGeneratedPlan()).toBeTrue();
    component.addAiIngredient(0);
    expect(response.days![0].meals[0].foods.length).toBe(2);
    component.removeAiIngredient(0, 1);
    expect(response.days![0].meals[0].foods.length).toBe(1);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Save reviewed meals');
    expect(fixture.nativeElement.textContent).toContain('assigned coach can review and edit');
  });

  it('validates recipe contents before saving generated meals', () => {
    component.aiPlan = generatedPlan();
    expect(component.canSaveGeneratedPlan()).toBeTrue();

    component.aiPlan!.days![0].meals[0].foods[0].servings = 0;
    expect(component.canSaveGeneratedPlan()).toBeFalse();
    component.saveGeneratedAiPlan();
    expect(api.savePersistentPlan).not.toHaveBeenCalled();
    expect(component.aiError).toContain('Check each recipe');
  });

  it('does not save a suggestion without its server-issued generation context', () => {
    component.aiPlan = generatedPlan();
    component.aiPlan.generationContextId = null;

    expect(component.canSaveGeneratedPlan()).toBeFalse();
    component.saveGeneratedAiPlan();

    expect(api.savePersistentPlan).not.toHaveBeenCalled();
  });

  it('saves the reviewed plan once with its calendar day and meal contents', () => {
    const pending = new Subject<NutritionPlanView>();
    api.savePersistentPlan.and.returnValue(pending);
    api.getNutritionPlans.and.returnValue(of([savedPlan]));
    component.aiRestrictions = 'peanuts';
    component.generateAiPlan();

    component.saveGeneratedAiPlan();
    component.saveGeneratedAiPlan();

    expect(api.savePersistentPlan).toHaveBeenCalledTimes(1);
    expect(api.savePersistentPlan).toHaveBeenCalledWith({
      planName: 'Member plan', goal: 'maintenance',
      generationContextId: '01234567-89ab-cdef-0123-456789abcdef',
      planType: 'DAILY',
      startDate: '2026-04-16',
      days: [{
        date: '2026-04-16',
        meals: [{
          name: 'Oat breakfast', mealType: 'BREAKFAST', servingSizeGrams: 350,
          preparationInstructions: 'Cook the oats.',
          foods: [{ name: 'Oats', servings: 1, foodId: 3 }],
          estimatedNutrition: {
            calories: 340, proteinGrams: 12, carbohydrateGrams: 52,
            fatGrams: 8, fiberGrams: 6, sodiumMg: 120
          },
          nutritionSource: 'REVIEW_REQUIRED'
        }]
      }]
    });
    expect(component.isSavingGeneratedPlan).toBeTrue();
    pending.next(savedPlan);
    expect(component.isSavingGeneratedPlan).toBeFalse();
    expect(component.aiPlan).toBeNull();
    expect(component.savedNutritionPlans[0]).toEqual(savedPlan);
  });

  it('maps authorization failures when saving generated meals', () => {
    api.savePersistentPlan.and.returnValue(throwError(() => ({ status: 403 })));
    component.aiPlan = generatedPlan();
    component.saveGeneratedAiPlan();

    expect(component.aiError).toContain('not authorized');
    expect(component.isSavingGeneratedPlan).toBeFalse();
  });
});
