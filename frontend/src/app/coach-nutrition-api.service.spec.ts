import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CoachNutritionApiService } from './coach-nutrition-api.service';
import { CreateNutritionMealPlan, NutritionPlanView } from './nutrition-api.service';

describe('CoachNutritionApiService', () => {
  let service: CoachNutritionApiService;
  let http: HttpTestingController;
  const memberId = 'member-profile-id';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CoachNutritionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('updates saved plan details through the member-scoped review endpoint', () => {
    const plan: CreateNutritionMealPlan = {
      name: 'Reviewed breakfast',
      description: 'Oats and fruit. Cook until soft.',
      mealType: 'BREAKFAST',
      targetCalories: 420,
      foods: [{ foodId: 12, servings: 1 }]
    };
    service.updateMealPlan(memberId, 8, plan).subscribe();
    const request = http.expectOne(`/api/nutrition/coach/members/${memberId}/meal-plans/8`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(plan);
    request.flush({
      id: 8,
      ...plan,
      foods: [{ foodId: 12, servings: 1, foodName: 'Oat bowl' }]
    });
  });

  it('loads persistent member plans and saves an edit for one assigned plan meal', () => {
    const plan: NutritionPlanView = {
      id: 14, name: 'Weekly plan', goal: 'maintenance', planType: 'WEEKLY',
      startDate: '2026-10-25', endDate: '2026-10-31', version: 2, days: []
    };
    service.getNutritionPlans(memberId).subscribe((plans) => expect(plans).toEqual([plan]));
    http.expectOne(`/api/nutrition/coach/members/${memberId}/plans`).flush([plan]);

    const meal = {
      name: 'Reviewed oats', mealType: 'BREAKFAST' as const, servingSizeGrams: 350,
      preparationInstructions: 'Cook gently.', estimatedNutrition: {
        calories: 340, proteinGrams: 12, carbohydrateGrams: 52, fatGrams: 8, fiberGrams: 6, sodiumMg: 120
      }, nutritionSource: 'MODEL_ESTIMATE',
      foods: [{ foodId: null, name: 'Oats', servings: 1 }]
    };
    service.updateNutritionPlanMeal(memberId, 14, '2026-10-26', 45, 2, meal).subscribe();
    const request = http.expectOne(
      `/api/nutrition/coach/members/${memberId}/plans/14/days/2026-10-26/meals/45`
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ expectedVersion: 2, meal });
    request.flush(plan);
  });
});
