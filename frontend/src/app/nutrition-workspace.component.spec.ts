import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import {
  AiMealPlanResponse,
  NutritionApiService,
  NutritionPlanView,
  NutritionProfile
} from './nutrition-api.service';
import { NutritionWorkspaceComponent } from './nutrition-workspace.component';

describe('NutritionWorkspaceComponent', () => {
  let fixture: ComponentFixture<NutritionWorkspaceComponent>;
  let component: NutritionWorkspaceComponent;
  let api: jasmine.SpyObj<NutritionApiService>;

  const profile: NutritionProfile = {
    id: 2,
    profileName: 'My nutrition profile',
    goal: 'maintenance',
    dietaryPreference: 'balanced',
    activityLevel: 'moderate',
    age: 31,
    heightCm: 168,
    weightKg: 68,
    targetCalories: 2050,
    calorieTargetMode: 'MANUAL',
    energyEquationSex: null,
    targetWeightKg: null,
    targetDate: null,
    allergies: [],
    intolerances: [],
    dislikedFoods: [],
    preferredIngredients: [],
    budgetPreference: null,
    maxPreparationMinutes: null,
    cookingPreference: null
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<NutritionApiService>('NutritionApiService', [
      'getFoods', 'getMealPlans', 'getNutritionPlans', 'getNutritionPlan',
      'getJournal', 'getMeasurements', 'getProfile',
      'getDailyNutrition', 'calculateBmi', 'calculateMacroTargets',
      'calculateEnergyEstimate', 'calculateGoalTimeline', 'getHydrationDay',
      'getHydrationHistory', 'addHydrationLog', 'correctHydrationLog', 'deleteHydrationLog',
      'saveProfile', 'generatePersistentPlan',
      'savePersistentPlan', 'regeneratePlanDay', 'replacePlanMeal',
      'generatePlan', 'saveGeneratedPlan'
    ]);
    api.getFoods.and.returnValue(of([]));
    api.getMealPlans.and.returnValue(of([]));
    api.getNutritionPlans.and.returnValue(of([]));
    api.getJournal.and.returnValue(of([]));
    api.getMeasurements.and.returnValue(of([]));
    api.getProfile.and.returnValue(of(profile));
    api.getDailyNutrition.and.returnValue(of({
      date: '2026-04-16',
      totals: { calories: 0, proteinGrams: 0, carbohydrateGrams: 0, fatGrams: 0, fiberGrams: 0, sodiumMg: 0 },
      meals: []
    }));
    api.calculateBmi.and.returnValue(of({ bmi: 24.1, note: 'Estimate based on saved height and weight.' }));
    api.calculateMacroTargets.and.returnValue(of({
      calorieTarget: 2050, proteinGrams: 108, carbohydrateGrams: 230, fatGrams: 57,
      estimatedCalories: 2050, method: 'Saved calorie target'
    }));
    api.calculateEnergyEstimate.and.returnValue(of({
      available: false, bmrCaloriesPerDay: null, tdeeCaloriesPerDay: null,
      goalCategory: null, activityFactor: null, goalAdjustedCalories: null,
      note: 'Energy estimates are unavailable.'
    }));
    api.calculateGoalTimeline.and.returnValue(of({
      available: false, minimumWeeks: null, maximumWeeks: null,
      earliestEstimatedDate: null, latestEstimatedDate: null, requestedDate: null,
      requestedDateStatus: 'UNAVAILABLE',
      note: 'A suitable estimate is not available.', assumptions: 'No supported method'
    }));
    api.getHydrationDay.and.returnValue(of({
      date: '2026-04-16', consumedMl: 0, targetMl: 2000, logs: [],
      targetAssumptions: 'General estimate'
    }));
    api.saveProfile.and.returnValue(of(profile));
    api.generatePersistentPlan.and.returnValue(of({
      available: false, status: 'UNAVAILABLE', plan: null, note: 'Assistant unavailable.',
      generationContextId: null
    }));
    api.saveGeneratedPlan.and.returnValue(of([]));
    api.savePersistentPlan.and.returnValue(of({
      id: 1, name: 'Plan', goal: 'maintenance', planType: 'DAILY',
      startDate: '2026-04-16', endDate: '2026-04-16', version: 0, days: []
    }));
    api.regeneratePlanDay.and.returnValue(of({
      id: 7, name: 'Week', goal: 'maintenance', planType: 'WEEKLY',
      startDate: '2026-04-16', endDate: '2026-04-22', version: 5, days: []
    }));
    api.replacePlanMeal.and.returnValue(of({
      id: 1, name: 'Plan', goal: 'maintenance', planType: 'DAILY',
      startDate: '2026-04-16', endDate: '2026-04-16', version: 1, days: []
    }));

    await TestBed.configureTestingModule({
      imports: [NutritionWorkspaceComponent],
      providers: [provideRouter([]), { provide: NutritionApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(NutritionWorkspaceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows one focused nutrition workspace at a time', () => {
    expect(fixture.nativeElement.textContent).toContain("Today's nutrition");
    expect(fixture.nativeElement.querySelector('[name="foodName"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[name="journalFood"]')).toBeNull();

    component.setView('library');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Food Library');
    expect(fixture.nativeElement.querySelector('[name="foodName"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[name="journalFood"]')).toBeNull();
  });

  it('offers the six-step onboarding and persists profile data before the estimate step', () => {
    component.startOnboarding();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('STEP 1 OF 6');

    component.bodyPresentation = 'female';
    component.nextOnboardingStep();
    component.profileForm.age = 31;
    component.profileForm.heightCm = 168;
    component.profileForm.weightKg = 68;
    component.profileForm.activityLevel = 'moderate';
    component.nextOnboardingStep();
    fixture.detectChanges();

    expect(api.saveProfile).toHaveBeenCalled();
    expect(component.wizardStep).toBe(3);
    expect(component.bmiEstimate?.bmi).toBe(24.1);
    expect(fixture.nativeElement.textContent).toContain('Backend-calculated BMI');
    expect(fixture.nativeElement.textContent).toContain('A suitable estimate is not available');
  });

  it('does not advance onboarding without required selections', () => {
    component.startOnboarding();
    component.nextOnboardingStep();
    fixture.detectChanges();

    expect(component.wizardStep).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Choose an illustration preference');
  });

  it('offers saved daily and weekly plans and invalidates stale suggestions when restrictions change', () => {
    component.setView('plan');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Seven-day plan');
    expect(fixture.nativeElement.textContent).toContain('Generate daily plan');

    component.aiPlan = {
      available: true,
      status: 'AVAILABLE',
      generationContextId: 'ctx',
      note: 'Review before saving.',
      plan: {
        planName: 'Ideas',
        goal: 'maintenance',
        meals: [],
        dailyNutrition: { calories: 1000, proteinGrams: 50, carbohydrateGrams: 120, fatGrams: 30, fiberGrams: 15, sodiumMg: 200 },
        shoppingList: [],
        alternatives: []
      }
    };
    component.onAiRestrictionsChanged();

    expect(component.aiPlan).toBeNull();
    expect(component.aiError).toContain('Restrictions changed');
  });

  it('generates a selected weekly draft, saves the complete days, and loads saved plans', () => {
    const meal = {
      name: 'Oats', mealType: 'BREAKFAST' as const, servingSizeGrams: 300,
      foods: [{ name: 'Oats', servings: 1, foodId: null }],
      preparationInstructions: 'Cook the oats.',
      estimatedNutrition: {
        calories: 300, proteinGrams: 10, carbohydrateGrams: 40, fatGrams: 5, fiberGrams: 4, sodiumMg: 10
      }, nutritionSource: 'MODEL_ESTIMATE' as const
    };
    const draft: AiMealPlanResponse = {
      available: true, status: 'AI_GENERATED', note: 'Review the draft.',
      generationContextId: 'ctx', planType: 'WEEKLY', startDate: '2026-04-16',
      plan: { planName: 'Week', goal: 'maintenance', meals: [],
        dailyNutrition: { calories: 0, proteinGrams: 0, carbohydrateGrams: 0, fatGrams: 0, fiberGrams: 0, sodiumMg: 0 },
        shoppingList: [], alternatives: [] },
      days: Array.from({ length: 7 }, (_, index) => ({
        date: `2026-04-${String(16 + index).padStart(2, '0')}`,
        meals: [{ ...meal, foods: [{ ...meal.foods[0] }] }], dailyNutrition: {
          calories: 300, proteinGrams: 10, carbohydrateGrams: 40, fatGrams: 5, fiberGrams: 4, sodiumMg: 10
        }, shoppingList: [], alternatives: []
      }))
    };
    const saved: NutritionPlanView = {
      id: 3, name: 'Week', goal: 'maintenance', planType: 'WEEKLY',
      startDate: '2026-04-16', endDate: '2026-04-22', version: 0, days: []
    };
    api.generatePersistentPlan.and.returnValue(of(draft));
    api.savePersistentPlan.and.returnValue(of(saved));
    api.getNutritionPlans.and.returnValue(of([saved]));
    component.setView('plan');
    component.selectedPlanType = 'WEEKLY';
    component.generateAiPlan();
    fixture.detectChanges();

    expect(api.generatePersistentPlan).toHaveBeenCalledWith(jasmine.objectContaining({
      planType: 'WEEKLY'
    }));
    expect(component.aiPlan?.days).toHaveSize(7);
    expect(fixture.nativeElement.textContent).toContain('Day 7');
    expect(component.canSaveGeneratedPlan()).toBeTrue();
    component.saveGeneratedAiPlan();
    expect(api.savePersistentPlan).toHaveBeenCalled();
    expect(component.savedNutritionPlans[0]).toEqual(saved);
  });

  it('regenerates one saved day with its current concurrency version', () => {
    const original: NutritionPlanView = {
      id: 7, name: 'Week', goal: 'maintenance', planType: 'WEEKLY',
      startDate: '2026-04-16', endDate: '2026-04-22', version: 4, days: []
    };
    component.savedNutritionPlans = [original];
    component.regenerateSavedDay(original, '2026-04-18');

    expect(api.regeneratePlanDay).toHaveBeenCalledWith(7, '2026-04-18', 4);
    expect(component.savedNutritionPlans[0].version).toBe(5);
    expect(component.successMessage).toContain('Other days were left unchanged');
  });
});
