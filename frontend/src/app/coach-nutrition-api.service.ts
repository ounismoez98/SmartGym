import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateNutritionMealPlan,
  NutritionFood,
  NutritionMealPlan,
  NutritionPlanMealView,
  NutritionPlanView
} from './nutrition-api.service';

@Injectable({ providedIn: 'root' })
export class CoachNutritionApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/nutrition/coach/members';

  getFoods(memberProfileId: string): Observable<NutritionFood[]> {
    return this.http.get<NutritionFood[]>(`${this.memberUrl(memberProfileId)}/foods`);
  }

  updateFood(memberProfileId: string, foodId: number, food: Omit<NutritionFood, 'id'>): Observable<NutritionFood> {
    return this.http.put<NutritionFood>(`${this.memberUrl(memberProfileId)}/foods/${foodId}`, food);
  }

  deleteFood(memberProfileId: string, foodId: number): Observable<void> {
    return this.http.delete<void>(`${this.memberUrl(memberProfileId)}/foods/${foodId}`);
  }

  getMealPlans(memberProfileId: string): Observable<NutritionMealPlan[]> {
    return this.http.get<NutritionMealPlan[]>(`${this.memberUrl(memberProfileId)}/meal-plans`);
  }

  getNutritionPlans(memberProfileId: string): Observable<NutritionPlanView[]> {
    return this.http.get<NutritionPlanView[]>(`${this.memberUrl(memberProfileId)}/plans`);
  }

  updateNutritionPlanMeal(
    memberProfileId: string,
    planId: number,
    date: string,
    mealId: number,
    expectedVersion: number,
    meal: Omit<NutritionPlanMealView, 'id'>
  ): Observable<NutritionPlanView> {
    return this.http.put<NutritionPlanView>(
      `${this.memberUrl(memberProfileId)}/plans/${planId}/days/${encodeURIComponent(date)}/meals/${mealId}`,
      { expectedVersion, meal }
    );
  }

  updateMealPlan(
    memberProfileId: string,
    mealPlanId: number,
    plan: CreateNutritionMealPlan
  ): Observable<NutritionMealPlan> {
    return this.http.put<NutritionMealPlan>(
      `${this.memberUrl(memberProfileId)}/meal-plans/${mealPlanId}`,
      plan
    );
  }

  private memberUrl(memberProfileId: string): string {
    return `${this.baseUrl}/${encodeURIComponent(memberProfileId)}`;
  }
}
