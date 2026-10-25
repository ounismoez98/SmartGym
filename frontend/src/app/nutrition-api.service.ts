import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface NutritionFood {
  id: number | null;
  name: string;
  brand: string;
  servingSizeGrams: number;
  calories: number;
  proteinGrams: number;
  carbohydrateGrams: number;
  fatGrams: number;
  fiberGrams: number;
  sodiumMg: number;
}

export interface NutritionMealPlan {
  id: number | null;
  name: string;
  description: string;
  mealType: 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK' | 'PRE_WORKOUT' | 'POST_WORKOUT';
  targetCalories: number;
  foods: { foodId: number; servings: number; foodName: string }[];
}

export type NutritionMealType = NutritionMealPlan['mealType'];

export interface CreateNutritionMealPlan {
  name: string;
  description: string;
  mealType: NutritionMealPlan['mealType'];
  targetCalories: number;
  foods: { foodId: number; servings: number }[];
}

export interface NutritionJournalEntry {
  id: number | null;
  foodId: number;
  mealType: string;
  entryDate: string;
  servings: number;
  notes: string;
}

export interface BodyMeasurement {
  id: number | null;
  measurementDate: string;
  weightKg: number;
  waistCm: number;
  hipCm: number;
  chestCm: number;
  armCm: number;
  thighCm: number;
  notes: string;
}

export interface NutritionProfile {
  id: number | null;
  profileName: string;
  goal: string;
  dietaryPreference: string;
  activityLevel: string;
  age: number | null;
  heightCm: number | null;
  weightKg: number | null;
  targetCalories: number | null;
  calorieTargetMode: 'AUTO' | 'MANUAL';
  energyEquationSex: 'FEMALE' | 'MALE' | null;
  targetWeightKg: number | null;
  targetDate: string | null;
  allergies: string[];
  intolerances: string[];
  dislikedFoods: string[];
  preferredIngredients: string[];
  budgetPreference: 'LOW' | 'MODERATE' | 'FLEXIBLE' | null;
  maxPreparationMinutes: number | null;
  cookingPreference: string | null;
}

export interface NutritionCalculation {
  calories: number;
  proteinGrams: number;
  carbohydrateGrams: number;
  fatGrams: number;
  fiberGrams: number;
  sodiumMg: number;
}

export interface DailyNutrition {
  date: string;
  totals: NutritionCalculation;
  meals: { mealType: NutritionJournalEntry['mealType']; totals: NutritionCalculation }[];
}

export interface NutritionBmiResult {
  bmi: number;
  note: string;
}

export interface NutritionMacroTargets {
  calorieTarget: number;
  proteinGrams: number;
  carbohydrateGrams: number;
  fatGrams: number;
  estimatedCalories: number;
  method: string;
}

export interface NutritionEnergyEstimate {
  available: boolean;
  bmrCaloriesPerDay: number | null;
  tdeeCaloriesPerDay: number | null;
  goalCategory: string | null;
  activityFactor: number | null;
  goalAdjustedCalories: number | null;
  note: string;
}

export interface NutritionGoalTimeline {
  available: boolean;
  minimumWeeks: number | null;
  maximumWeeks: number | null;
  earliestEstimatedDate: string | null;
  latestEstimatedDate: string | null;
  requestedDate: string | null;
  requestedDateStatus: 'NOT_SET' | 'EARLIER_THAN_ESTIMATE' | 'WITHIN_ESTIMATE' | 'LATER_THAN_ESTIMATE' | 'UNAVAILABLE';
  note: string;
  assumptions: string;
}

export interface HydrationLog {
  id: number;
  consumedAt: string;
  waterMl: number;
}

export interface HydrationDay {
  date: string;
  consumedMl: number;
  targetMl: number;
  logs: HydrationLog[];
  targetAssumptions: string;
}

export interface AiMealPlanResponse {
  available: boolean;
  status: string;
  plan: GeneratedAiMealPlan | null;
  note: string;
  generationContextId: string | null;
  planType?: NutritionPlanType;
  startDate?: string;
  days?: GeneratedNutritionPlanDay[];
}

export type NutritionPlanType = 'DAILY' | 'WEEKLY';

export interface GeneratedNutritionPlanDay {
  date: string;
  meals: GeneratedAiMeal[];
  dailyNutrition: EstimatedMealNutrition;
  shoppingList: string[];
  alternatives: string[];
}

export interface NutritionPlanView {
  id: number;
  name: string;
  goal: string;
  planType: NutritionPlanType;
  startDate: string;
  endDate: string;
  version: number;
  days: NutritionPlanDayView[];
}

export interface NutritionPlanDayView {
  id: number;
  date: string;
  meals: NutritionPlanMealView[];
}

export interface NutritionPlanMealView {
  id: number;
  name: string;
  mealType: NutritionMealType;
  servingSizeGrams: number;
  preparationInstructions: string;
  estimatedNutrition: EstimatedMealNutrition;
  nutritionSource: string;
  foods: { foodId: number | null; name: string; servings: number }[];
}

export interface SaveNutritionPlan {
  generationContextId: string;
  planType: NutritionPlanType;
  startDate: string;
  planName: string;
  goal: string;
  days: {
    date: string;
    meals: {
      name: string;
      mealType: NutritionMealType;
      servingSizeGrams: number;
      preparationInstructions: string;
      foods: { name: string; servings: number; foodId: number | null }[];
      estimatedNutrition: EstimatedMealNutrition;
      nutritionSource: string;
    }[];
  }[];
}

export interface GeneratedAiMealPlan {
  planName: string;
  goal: string;
  meals: GeneratedAiMeal[];
  dailyNutrition: EstimatedMealNutrition;
  shoppingList: string[];
  alternatives: string[];
}

export interface GeneratedAiMeal {
  name: string;
  mealType: NutritionMealType;
  servingSizeGrams: number;
  foods: { name: string; servings: number; foodId: number | null }[];
  preparationInstructions: string;
  estimatedNutrition: EstimatedMealNutrition;
  nutritionSource: 'SAVED_FOOD_DATA' | 'MODEL_ESTIMATE' | 'REVIEW_REQUIRED';
}

export interface EstimatedMealNutrition {
  calories: number;
  proteinGrams: number;
  carbohydrateGrams: number;
  fatGrams: number;
  fiberGrams: number;
  sodiumMg: number;
}

export interface SaveGeneratedMealPlan {
  generationContextId: string;
  planName: string;
  goal: string;
  meals: {
    name: string;
    mealType: NutritionMealType;
    servingSizeGrams: number;
    preparationInstructions: string;
    ingredients: { name: string; servings: number }[];
    estimatedNutrition: EstimatedMealNutrition;
  }[];
}

@Injectable({ providedIn: 'root' })
export class NutritionApiService {
  private readonly http = inject(HttpClient);

  getFoods(): Observable<NutritionFood[]> {
    return this.http.get<NutritionFood[]>('/api/nutrition/foods');
  }

  createFood(payload: Partial<NutritionFood>): Observable<NutritionFood> {
    return this.http.post<NutritionFood>('/api/nutrition/foods', payload);
  }

  updateFood(foodId: number, payload: Partial<NutritionFood>): Observable<NutritionFood> {
    return this.http.put<NutritionFood>(`/api/nutrition/foods/${foodId}`, payload);
  }

  deleteFood(foodId: number): Observable<void> {
    return this.http.delete<void>(`/api/nutrition/foods/${foodId}`);
  }

  getMealPlans(): Observable<NutritionMealPlan[]> {
    return this.http.get<NutritionMealPlan[]>('/api/nutrition/meal-plans');
  }

  getNutritionPlans(): Observable<NutritionPlanView[]> {
    return this.http.get<NutritionPlanView[]>('/api/nutrition/plans');
  }

  getNutritionPlan(planId: number): Observable<NutritionPlanView> {
    return this.http.get<NutritionPlanView>(`/api/nutrition/plans/${planId}`);
  }

  generatePersistentPlan(payload: {
    planType: NutritionPlanType;
    startDate: string;
    restrictions: string[];
  }): Observable<AiMealPlanResponse> {
    return this.http.post<AiMealPlanResponse>('/api/nutrition/assistant/plans/draft', payload);
  }

  savePersistentPlan(payload: SaveNutritionPlan): Observable<NutritionPlanView> {
    return this.http.post<NutritionPlanView>('/api/nutrition/plans', payload);
  }

  regeneratePlanDay(planId: number, date: string, expectedVersion: number): Observable<NutritionPlanView> {
    return this.http.post<NutritionPlanView>(
      `/api/nutrition/plans/${planId}/days/${encodeURIComponent(date)}/regenerate`,
      { expectedVersion }
    );
  }

  replacePlanMeal(planId: number, mealId: number, expectedVersion: number): Observable<NutritionPlanView> {
    return this.http.post<NutritionPlanView>(
      `/api/nutrition/plans/${planId}/meals/${mealId}/replace`,
      { expectedVersion }
    );
  }

  createMealPlan(payload: CreateNutritionMealPlan): Observable<NutritionMealPlan> {
    return this.http.post<NutritionMealPlan>('/api/nutrition/meal-plans', payload);
  }

  updateMealPlan(mealPlanId: number, payload: CreateNutritionMealPlan): Observable<NutritionMealPlan> {
    return this.http.put<NutritionMealPlan>(`/api/nutrition/meal-plans/${mealPlanId}`, payload);
  }

  deleteMealPlan(mealPlanId: number): Observable<void> {
    return this.http.delete<void>(`/api/nutrition/meal-plans/${mealPlanId}`);
  }

  getJournal(): Observable<NutritionJournalEntry[]> {
    return this.http.get<NutritionJournalEntry[]>('/api/nutrition/food-journal');
  }

  createJournalEntry(payload: Partial<NutritionJournalEntry>): Observable<NutritionJournalEntry> {
    return this.http.post<NutritionJournalEntry>('/api/nutrition/food-journal', payload);
  }

  updateJournalEntry(entryId: number, payload: Partial<NutritionJournalEntry>): Observable<NutritionJournalEntry> {
    return this.http.put<NutritionJournalEntry>(`/api/nutrition/food-journal/${entryId}`, payload);
  }

  deleteJournalEntry(entryId: number): Observable<void> {
    return this.http.delete<void>(`/api/nutrition/food-journal/${entryId}`);
  }

  getMeasurements(): Observable<BodyMeasurement[]> {
    return this.http.get<BodyMeasurement[]>('/api/nutrition/body-measurements');
  }

  createMeasurement(payload: Partial<BodyMeasurement>): Observable<BodyMeasurement> {
    return this.http.post<BodyMeasurement>('/api/nutrition/body-measurements', payload);
  }

  updateMeasurement(measurementId: number, payload: Partial<BodyMeasurement>): Observable<BodyMeasurement> {
    return this.http.put<BodyMeasurement>(`/api/nutrition/body-measurements/${measurementId}`, payload);
  }

  deleteMeasurement(measurementId: number): Observable<void> {
    return this.http.delete<void>(`/api/nutrition/body-measurements/${measurementId}`);
  }

  getProfile(): Observable<NutritionProfile> {
    return this.http.get<NutritionProfile>('/api/nutrition/profiles');
  }

  saveProfile(payload: Partial<NutritionProfile>): Observable<NutritionProfile> {
    return this.http.post<NutritionProfile>('/api/nutrition/profiles', payload);
  }

  calculateGoalTimeline(): Observable<NutritionGoalTimeline> {
    return this.http.get<NutritionGoalTimeline>('/api/nutrition/calculations/goal-timeline');
  }

  getHydrationDay(date: string): Observable<HydrationDay> {
    return this.http.get<HydrationDay>('/api/nutrition/hydration', {
      params: new HttpParams().set('date', date)
    });
  }

  getHydrationHistory(from: string, to: string): Observable<HydrationLog[]> {
    return this.http.get<HydrationLog[]>('/api/nutrition/hydration/history', {
      params: new HttpParams().set('from', from).set('to', to)
    });
  }

  addHydrationLog(payload: { consumedAt?: string | null; waterMl: number }): Observable<HydrationDay> {
    return this.http.post<HydrationDay>('/api/nutrition/hydration', payload);
  }

  correctHydrationLog(logId: number, payload: { consumedAt?: string | null; waterMl: number }): Observable<HydrationDay> {
    return this.http.put<HydrationDay>(`/api/nutrition/hydration/${logId}`, payload);
  }

  deleteHydrationLog(logId: number): Observable<void> {
    return this.http.delete<void>(`/api/nutrition/hydration/${logId}`);
  }

  calculateNutrition(foodIds: number[], servings?: number[]): Observable<NutritionCalculation> {
    let params = new HttpParams();
    foodIds.forEach((foodId) => {
      params = params.append('foodIds', foodId);
    });
    servings?.forEach((quantity) => {
      params = params.append('servings', quantity);
    });
    return this.http.get<NutritionCalculation>('/api/nutrition/calculations', { params });
  }

  getDailyNutrition(date: string): Observable<DailyNutrition> {
    return this.http.get<DailyNutrition>('/api/nutrition/food-journal/daily', {
      params: new HttpParams().set('date', date)
    });
  }

  calculateBmi(): Observable<NutritionBmiResult> {
    return this.http.get<NutritionBmiResult>('/api/nutrition/calculations/bmi');
  }

  calculateMacroTargets(): Observable<NutritionMacroTargets> {
    return this.http.get<NutritionMacroTargets>('/api/nutrition/calculations/macros');
  }

  calculateEnergyEstimate(): Observable<NutritionEnergyEstimate> {
    return this.http.get<NutritionEnergyEstimate>('/api/nutrition/calculations/energy');
  }

  generatePlan(payload: { restrictions: string[] }): Observable<AiMealPlanResponse> {
    return this.http.post<AiMealPlanResponse>('/api/nutrition/assistant/plan', payload);
  }

  saveGeneratedPlan(payload: SaveGeneratedMealPlan): Observable<NutritionMealPlan[]> {
    return this.http.post<NutritionMealPlan[]>('/api/nutrition/assistant/plan/save', payload);
  }
}
