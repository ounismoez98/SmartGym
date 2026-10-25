import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  BodyMeasurement,
  CreateNutritionMealPlan,
  NutritionApiService,
  NutritionFood,
  NutritionJournalEntry,
  NutritionProfile,
  SaveGeneratedMealPlan
} from './nutrition-api.service';

describe('NutritionApiService', () => {
  let service: NutritionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(NutritionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets foods from the nutrition collection', () => {
    service.getFoods().subscribe((foods) => expect(foods).toEqual([]));
    http.expectOne({ method: 'GET', url: '/api/nutrition/foods' }).flush([]);
  });

  it('creates a food with the supplied body', () => {
    const payload: Partial<NutritionFood> = { name: 'Oats', calories: 150 };
    service.createFood(payload).subscribe();
    const request = http.expectOne('/api/nutrition/foods');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ ...payload, id: 4 });
  });

  it('updates and deletes a food by id', () => {
    const payload: Partial<NutritionFood> = { name: 'Rolled oats' };
    service.updateFood(4, payload).subscribe();
    const update = http.expectOne('/api/nutrition/foods/4');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual(payload);
    update.flush({ ...payload, id: 4 });

    service.deleteFood(4).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/nutrition/foods/4' }).flush(null);
  });

  it('lists, creates, updates, and deletes meal plans', () => {
    const payload: CreateNutritionMealPlan = {
      name: 'Breakfast', description: 'Simple meal', mealType: 'BREAKFAST',
      targetCalories: 400, foods: [{ foodId: 3, servings: 1.5 }]
    };
    service.getMealPlans().subscribe((plans) => expect(plans).toEqual([]));
    http.expectOne({ method: 'GET', url: '/api/nutrition/meal-plans' }).flush([]);

    service.createMealPlan(payload).subscribe();
    const create = http.expectOne('/api/nutrition/meal-plans');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush({ ...payload, id: 7, foods: [] });

    service.updateMealPlan(7, payload).subscribe();
    const update = http.expectOne('/api/nutrition/meal-plans/7');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual(payload);
    update.flush({ ...payload, id: 7, foods: [] });

    service.deleteMealPlan(7).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/nutrition/meal-plans/7' }).flush(null);
  });

  it('lists, creates, updates, and deletes food journal entries', () => {
    const payload: Partial<NutritionJournalEntry> = {
      foodId: 3, mealType: 'LUNCH', entryDate: '2026-04-16', servings: 2, notes: 'After training'
    };
    service.getJournal().subscribe((entries) => expect(entries).toEqual([]));
    http.expectOne({ method: 'GET', url: '/api/nutrition/food-journal' }).flush([]);

    service.createJournalEntry(payload).subscribe();
    const create = http.expectOne('/api/nutrition/food-journal');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush({ ...payload, id: 11 });

    service.updateJournalEntry(11, payload).subscribe();
    const update = http.expectOne('/api/nutrition/food-journal/11');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual(payload);
    update.flush({ ...payload, id: 11 });

    service.deleteJournalEntry(11).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/nutrition/food-journal/11' }).flush(null);
  });

  it('lists, creates, updates, and deletes body measurements', () => {
    const payload: Partial<BodyMeasurement> = {
      measurementDate: '2026-04-16', weightKg: 72, waistCm: 80
    };
    service.getMeasurements().subscribe((entries) => expect(entries).toEqual([]));
    http.expectOne({ method: 'GET', url: '/api/nutrition/body-measurements' }).flush([]);

    service.createMeasurement(payload).subscribe();
    const create = http.expectOne('/api/nutrition/body-measurements');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush({ ...payload, id: 12 });

    service.updateMeasurement(12, payload).subscribe();
    const update = http.expectOne('/api/nutrition/body-measurements/12');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual(payload);
    update.flush({ ...payload, id: 12 });

    service.deleteMeasurement(12).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/nutrition/body-measurements/12' }).flush(null);
  });

  it('gets and saves a member nutrition profile', () => {
    const profile: Partial<NutritionProfile> = { profileName: 'My profile', targetCalories: 2100 };
    service.getProfile().subscribe((result) => expect(result.profileName).toBe('My profile'));
    http.expectOne({ method: 'GET', url: '/api/nutrition/profiles' }).flush({ id: 2, ...profile });

    service.saveProfile(profile).subscribe();
    const request = http.expectOne('/api/nutrition/profiles');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(profile);
    request.flush({ id: 2, ...profile });
  });

  it('encodes repeated food ids and serving quantities for nutrition calculations', () => {
    service.calculateNutrition([3, 8], [1, 2.5]).subscribe();
    const request = http.expectOne('/api/nutrition/calculations?foodIds=3&foodIds=8&servings=1&servings=2.5');
    expect(request.request.method).toBe('GET');
    request.flush({ calories: 0, proteinGrams: 0, carbohydrateGrams: 0, fatGrams: 0, fiberGrams: 0, sodiumMg: 0 });
  });

  it('requests daily totals for the selected date', () => {
    service.getDailyNutrition('2026-04-16').subscribe();
    const request = http.expectOne('/api/nutrition/food-journal/daily?date=2026-04-16');
    expect(request.request.method).toBe('GET');
    request.flush({ date: '2026-04-16', totals: {}, meals: [] });
  });

  it('requests BMI, macro targets, and energy estimates from their API routes', () => {
    service.calculateBmi().subscribe();
    http.expectOne({ method: 'GET', url: '/api/nutrition/calculations/bmi' }).flush({ bmi: 22, note: 'BMI' });
    service.calculateMacroTargets().subscribe();
    http.expectOne({ method: 'GET', url: '/api/nutrition/calculations/macros' }).flush({
      calorieTarget: 2000, proteinGrams: 100, carbohydrateGrams: 250, fatGrams: 60,
      estimatedCalories: 1940, method: 'saved target'
    });
    service.calculateEnergyEstimate().subscribe();
    http.expectOne({ method: 'GET', url: '/api/nutrition/calculations/energy' }).flush({
      available: false, bmrCaloriesPerDay: null, tdeeCaloriesPerDay: null,
      goalCategory: null, activityFactor: null, note: 'Unavailable'
    });
  });

  it('generates and saves a reviewed meal plan using the assistant routes', () => {
    const restrictions = { restrictions: ['peanuts', 'shellfish'] };
    service.generatePlan(restrictions).subscribe();
    const generate = http.expectOne('/api/nutrition/assistant/plan');
    expect(generate.request.method).toBe('POST');
    expect(generate.request.body).toEqual(restrictions);
    generate.flush({ available: false, status: 'UNAVAILABLE', plan: null, note: 'Not configured' });

    const payload: SaveGeneratedMealPlan = {
      planName: 'Plan', goal: 'maintenance', restrictions: ['peanuts'],
      meals: [{ name: 'Breakfast', mealType: 'BREAKFAST', preparationInstructions: 'Prepare',
        foods: [{ foodId: 3, servings: 1 }] }]
    };
    service.saveGeneratedPlan(payload).subscribe();
    const save = http.expectOne('/api/nutrition/assistant/plan/save');
    expect(save.request.method).toBe('POST');
    expect(save.request.body).toEqual(payload);
    save.flush([]);
  });

  it('propagates HTTP status codes and network errors without remapping them', () => {
    [400, 401, 403, 404, 409, 429, 500, 503].forEach((status) => {
      let actualStatus: number | undefined;
      service.getFoods().subscribe({ error: (error: { status: number }) => actualStatus = error.status });
      http.expectOne('/api/nutrition/foods').flush({}, { status, statusText: 'Request failed' });
      expect(actualStatus).toBe(status);
    });

    let networkStatus: number | undefined;
    service.getFoods().subscribe({ error: (error: { status: number }) => networkStatus = error.status });
    http.expectOne('/api/nutrition/foods').error(new ProgressEvent('network error'));
    expect(networkStatus).toBe(0);
  });
});
