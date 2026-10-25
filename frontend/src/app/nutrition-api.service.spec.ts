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
  SaveGeneratedMealPlan,
  SaveNutritionPlan
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

  it('requests the deterministic goal timeline and hydration history and persists corrections', () => {
    service.calculateGoalTimeline().subscribe((result) => {
      expect(result.available).toBeTrue();
      expect(result.minimumWeeks).toBe(20);
    });
    http.expectOne('/api/nutrition/calculations/goal-timeline').flush({
      available: true, minimumWeeks: 20, maximumWeeks: 40,
      earliestEstimatedDate: '2027-03-14', latestEstimatedDate: '2027-08-01',
      requestedDate: null, requestedDateStatus: 'NOT_SET', note: 'Approximate',
      assumptions: '0.25–0.5 kg/week'
    });

    service.getHydrationDay('2026-10-25').subscribe((day) => expect(day.consumedMl).toBe(250));
    const daily = http.expectOne('/api/nutrition/hydration?date=2026-10-25');
    expect(daily.request.method).toBe('GET');
    daily.flush({ date: '2026-10-25', consumedMl: 250, targetMl: 2000, logs: [], targetAssumptions: 'General estimate' });

    service.getHydrationHistory('2026-10-01', '2026-10-25').subscribe((logs) => expect(logs.length).toBe(1));
    const history = http.expectOne('/api/nutrition/hydration/history?from=2026-10-01&to=2026-10-25');
    expect(history.request.method).toBe('GET');
    history.flush([{ id: 8, consumedAt: '2026-10-25T12:00:00Z', waterMl: 250 }]);

    service.addHydrationLog({ waterMl: 300 }).subscribe();
    const create = http.expectOne('/api/nutrition/hydration');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ waterMl: 300 });
    create.flush({ date: '2026-10-25', consumedMl: 550, targetMl: 2000, logs: [], targetAssumptions: '' });

    service.correctHydrationLog(8, { consumedAt: '2026-10-25T12:00:00Z', waterMl: 400 }).subscribe();
    const correct = http.expectOne('/api/nutrition/hydration/8');
    expect(correct.request.method).toBe('PUT');
    expect(correct.request.body.waterMl).toBe(400);
    correct.flush({ date: '2026-10-25', consumedMl: 400, targetMl: 2000, logs: [], targetAssumptions: '' });

    service.deleteHydrationLog(8).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/nutrition/hydration/8' }).flush(null);
  });

  it('generates and saves a reviewed meal plan using the assistant routes', () => {
    const restrictions = { restrictions: ['peanuts', 'shellfish'] };
    service.generatePlan(restrictions).subscribe();
    const generate = http.expectOne('/api/nutrition/assistant/plan');
    expect(generate.request.method).toBe('POST');
    expect(generate.request.body).toEqual(restrictions);
    generate.flush({
      available: false, status: 'UNAVAILABLE', plan: null, note: 'Not configured',
      generationContextId: null
    });

    const payload: SaveGeneratedMealPlan = {
      planName: 'Plan', goal: 'maintenance',
      generationContextId: '01234567-89ab-cdef-0123-456789abcdef',
      meals: [{
        name: 'Breakfast', mealType: 'BREAKFAST', servingSizeGrams: 350,
        preparationInstructions: 'Prepare', ingredients: [{ name: 'Oats', servings: 50 }],
        estimatedNutrition: {
          calories: 340, proteinGrams: 12, carbohydrateGrams: 52,
          fatGrams: 8, fiberGrams: 6, sodiumMg: 120
        }
      }]
    };
    service.saveGeneratedPlan(payload).subscribe();
    const save = http.expectOne('/api/nutrition/assistant/plan/save');
    expect(save.request.method).toBe('POST');
    expect(save.request.body).toEqual(payload);
    save.flush([]);
  });

  it('generates dated daily or weekly drafts and persists/retrieves plans', () => {
    const request = {
      planType: 'WEEKLY' as const,
      startDate: '2026-10-25',
      restrictions: ['peanuts']
    };
    service.generatePersistentPlan(request).subscribe();
    const generate = http.expectOne('/api/nutrition/assistant/plans/draft');
    expect(generate.request.method).toBe('POST');
    expect(generate.request.body).toEqual(request);
    generate.flush({
      available: true, status: 'AI_GENERATED', note: 'Review this draft.',
      generationContextId: 'context', planType: 'WEEKLY', startDate: request.startDate,
      planName: 'Week', goal: 'maintenance', plan: null, days: []
    });

    const payload: SaveNutritionPlan = {
      generationContextId: 'context',
      planType: 'WEEKLY',
      startDate: request.startDate,
      planName: 'Week',
      goal: 'maintenance',
      days: []
    };
    service.savePersistentPlan(payload).subscribe();
    const save = http.expectOne('/api/nutrition/plans');
    expect(save.request.method).toBe('POST');
    expect(save.request.body).toEqual(payload);
    save.flush({ id: 4, name: 'Week', goal: 'maintenance', planType: 'WEEKLY',
      startDate: request.startDate, endDate: '2026-10-31', version: 0, days: [] });

    service.getNutritionPlans().subscribe((plans) => expect(plans).toHaveSize(1));
    http.expectOne({ method: 'GET', url: '/api/nutrition/plans' }).flush([{
      id: 4, name: 'Week', goal: 'maintenance', planType: 'WEEKLY',
      startDate: request.startDate, endDate: '2026-10-31', version: 0, days: []
    }]);
    service.getNutritionPlan(4).subscribe();
    http.expectOne({ method: 'GET', url: '/api/nutrition/plans/4' }).flush({
      id: 4, name: 'Week', goal: 'maintenance', planType: 'WEEKLY',
      startDate: request.startDate, endDate: '2026-10-31', version: 0, days: []
    });
  });

  it('sends plan versions when regenerating a day or replacing one meal', () => {
    service.regeneratePlanDay(4, '2026-10-27', 3).subscribe();
    const regenerate = http.expectOne('/api/nutrition/plans/4/days/2026-10-27/regenerate');
    expect(regenerate.request.method).toBe('POST');
    expect(regenerate.request.body).toEqual({ expectedVersion: 3 });
    regenerate.flush({});

    service.replacePlanMeal(4, 11, 4).subscribe();
    const replace = http.expectOne('/api/nutrition/plans/4/meals/11/replace');
    expect(replace.request.method).toBe('POST');
    expect(replace.request.body).toEqual({ expectedVersion: 4 });
    replace.flush({});
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
