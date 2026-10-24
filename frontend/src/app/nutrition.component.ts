import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import {
  AiMealPlanResponse,
  BodyMeasurement,
  CreateNutritionMealPlan,
  DailyNutrition,
  NutritionApiService,
  NutritionBmiResult,
  NutritionCalculation,
  NutritionEnergyEstimate,
  NutritionFood,
  NutritionJournalEntry,
  NutritionMacroTargets,
  NutritionMealPlan,
  NutritionMealType,
  NutritionProfile
} from './nutrition-api.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <main class="content nutrition-page">
      <a class="back-link" routerLink="/dashboard">← Dashboard</a>
      <header class="page-hero">
        <div>
          <p class="eyebrow">MODULE 04 · NUTRITION &amp; BODY TRACKING</p>
          <h1>Your nutrition space</h1>
          <p class="lead">A practical view of your profile, food choices, daily log, and progress.</p>
        </div>
        <a class="button primary" href="#journal">Log a meal</a>
      </header>

      @if (loading) {
        <p class="notice" role="status">Loading your private nutrition data…</p>
      }
      @if (pageError) {
        <p class="notice error" role="alert">{{ pageError }}</p>
      }
      @if (successMessage) {
        <p class="notice" role="status">{{ successMessage }}</p>
      }

      <nav class="section-nav" aria-label="Nutrition sections">
        <a href="#overview">Overview</a><a href="#profile">Profile</a><a href="#foods">Foods</a>
        <a href="#plans">Meal plans</a><a href="#journal">Food journal</a>
        <a href="#progress">Body tracking</a><a href="#assistant">AI assistant</a>
      </nav>

      <section id="overview" class="dashboard-grid" aria-label="Nutrition dashboard">
        <article class="account-card dashboard-main">
          <div class="section-title">
            <div><p class="eyebrow">TODAY · {{ dailyNutritionDate }}</p><h2>Daily nutrition</h2></div>
            <label class="date-control">Date
              <input type="date" [(ngModel)]="dailyNutritionDate" name="overviewDate"
                (change)="loadDailyNutrition()" />
            </label>
          </div>
          @if (dailyNutritionLoading) {
            <p role="status">Loading daily totals…</p>
          } @else if (dailyNutrition) {
            <div class="nutrition-stats">
              <div class="stat-card primary-stat">
                <span>Calories</span><strong>{{ dailyNutrition.totals.calories | number:'1.0-0' }}</strong>
                <small>kcal consumed</small>
              </div>
              <div class="stat-card"><span>Protein</span><strong>{{ dailyNutrition.totals.proteinGrams | number:'1.0-1' }} g</strong></div>
              <div class="stat-card"><span>Carbohydrates</span><strong>{{ dailyNutrition.totals.carbohydrateGrams | number:'1.0-1' }} g</strong></div>
              <div class="stat-card"><span>Fat</span><strong>{{ dailyNutrition.totals.fatGrams | number:'1.0-1' }} g</strong></div>
            </div>
            @if (remainingCalories !== null) {
              <p class="target-line">
                Saved target: {{ profile!.targetCalories }} kcal ·
                @if (remainingCalories >= 0) { {{ remainingCalories | number:'1.0-0' }} kcal remaining }
                @else { {{ -remainingCalories | number:'1.0-0' }} kcal over target }
              </p>
            } @else {
              <p class="muted">Save a calorie target in your profile to compare it with today's logged total.</p>
            }
            @if (dailyNutrition.meals.length) {
              <div class="meal-totals">
                @for (meal of dailyNutrition.meals; track meal.mealType) {
                  <div><span>{{ meal.mealType.replace('_', ' ') }}</span><strong>{{ meal.totals.calories | number:'1.0-0' }} kcal</strong></div>
                }
              </div>
            } @else {
              <p class="empty-state">No journal entries for this date yet. Add your first meal below.</p>
            }
          } @else {
            <p class="empty-state">Daily totals are not available right now.</p>
          }
        </article>

        <article class="account-card profile-summary">
          <p class="eyebrow">YOUR PROFILE</p>
          @if (profile) {
            <h2>{{ profile.profileName }}</h2>
            <p>{{ profile.goal }} · {{ profile.dietaryPreference }}</p>
            <dl>
              <div><dt>Weight</dt><dd>{{ profile.weightKg ?? 'Not set' }}{{ profile.weightKg ? ' kg' : '' }}</dd></div>
              <div><dt>Activity</dt><dd>{{ profile.activityLevel }}</dd></div>
              <div><dt>Calorie target</dt><dd>{{ profile.targetCalories ?? 'Not set' }}{{ profile.targetCalories ? ' kcal' : '' }}</dd></div>
            </dl>
            <a class="text-link" href="#profile">Edit profile →</a>
          } @else {
            <h2>Start with your profile</h2>
            <p class="muted">Add your supported profile details to personalize estimates.</p>
            <a class="text-link" href="#profile">Set up profile →</a>
          }
        </article>

        <article class="account-card dashboard-wide">
          <div class="section-title">
            <div><p class="eyebrow">RECENT ACTIVITY</p><h2>Latest food journal entries</h2></div>
            <a class="text-link" href="#journal">Open journal →</a>
          </div>
          @if (recentJournalEntries.length) {
            <div class="recent-list">
              @for (entry of recentJournalEntries; track entry.id) {
                <div class="recent-row">
                  <span class="meal-dot" aria-hidden="true"></span>
                  <div><strong>{{ foodName(entry.foodId) }}</strong><small>{{ entry.entryDate }} · {{ entry.mealType.replace('_', ' ') }}</small></div>
                  <span>{{ entry.servings }} serving{{ entry.servings === 1 ? '' : 's' }}</span>
                </div>
              }
            </div>
          } @else {
            <p class="empty-state">Your latest entries will appear here after you log food.</p>
          }
        </article>

        <article class="account-card">
          <p class="eyebrow">BODY PROGRESS</p><h2>Measurement history</h2>
          @if (measurements.length) {
            <p>Latest weight <strong>{{ measurements[0].weightKg }} kg</strong> · {{ measurements[0].measurementDate }}</p>
            @if (weightChartPoints) {
              <svg class="weight-chart" viewBox="0 0 500 170" role="img"
                aria-label="Weight history chart based on your saved measurements">
                <line x1="16" y1="145" x2="484" y2="145" class="chart-axis"></line>
                <polyline [attr.points]="weightChartPoints" class="chart-line"></polyline>
                @for (point of weightChartDots; track $index) {
                  <circle [attr.cx]="point.x" [attr.cy]="point.y" r="4" class="chart-dot"></circle>
                }
              </svg>
              <p class="chart-caption">{{ chartStartDate }} <span>Weight in kg · saved measurements</span> {{ chartEndDate }}</p>
            } @else {
              <p class="muted">Add another measurement to see your weight trend.</p>
            }
            <a class="text-link" href="#progress">View full history →</a>
          } @else {
            <p class="empty-state">No measurements saved yet.</p>
            <a class="text-link" href="#progress">Record a measurement →</a>
          }
        </article>

        <article class="account-card">
          <p class="eyebrow">ESTIMATES</p><h2>BMI &amp; macro targets</h2>
          @if (bmiEstimate) {
            <p>BMI <strong>{{ bmiEstimate.bmi | number:'1.1-1' }}</strong></p>
            <p class="muted">{{ bmiEstimate.note }}</p>
          } @else {
            <p class="muted">{{ calculationErrors.bmi || 'BMI needs a saved height and weight in your profile.' }}</p>
          }
          @if (macroTargets) {
            <div class="macro-row"><span>Protein</span><strong>{{ macroTargets.proteinGrams | number:'1.0-0' }} g</strong></div>
            <div class="macro-row"><span>Carbohydrates</span><strong>{{ macroTargets.carbohydrateGrams | number:'1.0-0' }} g</strong></div>
            <div class="macro-row"><span>Fat</span><strong>{{ macroTargets.fatGrams | number:'1.0-0' }} g</strong></div>
            <small class="muted">Based on your saved calorie target, not TDEE.</small>
          } @else {
            <p class="muted">{{ calculationErrors.macros || 'Macro estimates need a saved calorie target and weight.' }}</p>
          }
          @if (energyEstimate) {
            <p class="notice">{{ energyEstimate.note }}</p>
          }
        </article>

        <div class="quick-actions dashboard-wide" aria-label="Quick actions">
          <a class="quick-action" href="#foods"><span>＋</span><strong>Add a food</strong><small>Build your library</small></a>
          <a class="quick-action" href="#journal"><span>↗</span><strong>Log a meal</strong><small>Track today's intake</small></a>
          <a class="quick-action" href="#progress"><span>⌁</span><strong>Record progress</strong><small>Update measurements</small></a>
          <a class="quick-action" href="#assistant"><span>✦</span><strong>Meal assistant</strong><small>Review a suggestion</small></a>
        </div>
      </section>

      <section id="profile" class="account-card page-section">
        <div class="section-title">
          <div><p class="eyebrow">YOUR STARTING POINT</p><h2>Guided body profile</h2></div>
          <span class="section-index">01</span>
        </div>
        <p class="muted">Profile details are saved to your authenticated account. Optional presentation choices below are only for this screen and are not used to infer health or body composition.</p>
        <fieldset class="presentation-choice">
          <legend>Illustration preference <span class="muted">(UI only)</span></legend>
          <button type="button" class="presentation-card" [class.selected]="bodyPresentation === 'female'"
            [attr.aria-pressed]="bodyPresentation === 'female'" (click)="bodyPresentation = 'female'">
            <svg viewBox="0 0 80 100" aria-hidden="true"><circle cx="40" cy="16" r="12"></circle><path d="M30 33 Q40 28 50 33 L55 52 51 58 60 70 51 75 47 93 41 93 40 78 39 93 33 93 29 75 20 70 29 58 25 52Z"></path><path d="M28 48 L19 66 M52 48 L61 66" fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round"></path></svg>
            <span>Female illustration</span>
          </button>
          <button type="button" class="presentation-card" [class.selected]="bodyPresentation === 'male'"
            [attr.aria-pressed]="bodyPresentation === 'male'" (click)="bodyPresentation = 'male'">
            <svg viewBox="0 0 80 100" aria-hidden="true"><circle cx="40" cy="16" r="12"></circle><path d="M24 33 Q40 27 56 33 L63 56 55 61 57 93 44 93 40 64 36 93 23 93 25 61 17 56Z"></path><path d="M25 44 L16 65 M55 44 L64 65" fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round"></path></svg>
            <span>Male illustration</span>
          </button>
        </fieldset>
        <form class="form-grid" (ngSubmit)="saveProfile()">
          <label>Profile name<input [(ngModel)]="profileForm.profileName" name="profileName" required maxlength="120" /></label>
          <label>Age <span class="unit">years</span><input type="number" min="1" [(ngModel)]="profileForm.age" name="age" /></label>
          <label>Height <span class="unit">cm</span><input type="number" min="1" step="0.1" [(ngModel)]="profileForm.heightCm" name="heightCm" /></label>
          <label>Current weight <span class="unit">kg</span><input type="number" min="1" step="0.1" [(ngModel)]="profileForm.weightKg" name="weightKg" /></label>
          <label>Calorie target <span class="unit">kcal/day · optional</span><input type="number" min="1" [(ngModel)]="profileForm.targetCalories" name="targetCalories" /></label>
          <label>Fitness goal
            <select [(ngModel)]="profileForm.goal" name="goal" required>
              <option value="">Choose a goal</option><option value="weight loss">Weight loss</option>
              <option value="maintenance">Maintenance</option><option value="muscle gain">Muscle gain</option>
            </select>
          </label>
          <label>Activity level
            <select [(ngModel)]="profileForm.activityLevel" name="activityLevel" required>
              <option value="">Choose activity level</option><option value="sedentary">Sedentary</option>
              <option value="light">Light</option><option value="moderate">Moderate</option>
              <option value="active">Active</option><option value="very active">Very active</option>
            </select>
          </label>
          <label>Dietary preference
            <select [(ngModel)]="profileForm.dietaryPreference" name="dietaryPreference" required>
              <option value="">Choose preference</option><option value="balanced">Balanced</option>
              <option value="vegetarian">Vegetarian</option><option value="vegan">Vegan</option>
              <option value="pescatarian">Pescatarian</option><option value="other">Other</option>
            </select>
          </label>
          <label>Target weight <span class="unit">kg · session only</span><input type="number" min="1" step="0.1" [(ngModel)]="sessionPreferences.targetWeightKg" name="targetWeight" /></label>
          <label>Training frequency <span class="unit">sessions/week · session only</span><input type="number" min="0" max="14" [(ngModel)]="sessionPreferences.trainingFrequency" name="trainingFrequency" /></label>
          <div class="form-actions"><button class="button primary" type="submit" [disabled]="savingProfile">Save supported profile details</button></div>
        </form>
        <p class="notice">Target weight and training frequency are not stored or used by the current API. BMR/TDEE remain unavailable because the profile does not contain the required equation input.</p>
        @if (profileError) { <p class="notice error" role="alert">{{ profileError }}</p> }
      </section>

      <section id="foods" class="account-card page-section">
        <div class="section-title">
          <div><p class="eyebrow">YOUR FOOD LIBRARY</p><h2>{{ editingFoodId ? 'Edit food' : 'Manage foods' }}</h2></div>
          <span class="section-index">02</span>
        </div>
        <form class="form-grid" (ngSubmit)="saveFood()">
          <label>Food name<input [(ngModel)]="foodForm.name" name="foodName" required maxlength="150" /></label>
          <label>Brand<input [(ngModel)]="foodForm.brand" name="foodBrand" maxlength="150" /></label>
          <label>Serving size <span class="unit">grams</span><input type="number" min="1" [(ngModel)]="foodForm.servingSizeGrams" name="foodServingSize" required /></label>
          <label>Calories <span class="unit">per serving</span><input type="number" min="0" step="0.1" [(ngModel)]="foodForm.calories" name="foodCalories" required /></label>
          <label>Protein <span class="unit">g/serving</span><input type="number" min="0" step="0.1" [(ngModel)]="foodForm.proteinGrams" name="foodProtein" required /></label>
          <label>Carbohydrates <span class="unit">g/serving</span><input type="number" min="0" step="0.1" [(ngModel)]="foodForm.carbohydrateGrams" name="foodCarbs" required /></label>
          <label>Fat <span class="unit">g/serving</span><input type="number" min="0" step="0.1" [(ngModel)]="foodForm.fatGrams" name="foodFat" required /></label>
          <label>Fiber <span class="unit">g/serving</span><input type="number" min="0" step="0.1" [(ngModel)]="foodForm.fiberGrams" name="foodFiber" required /></label>
          <label>Sodium <span class="unit">mg/serving</span><input type="number" min="0" step="0.1" [(ngModel)]="foodForm.sodiumMg" name="foodSodium" required /></label>
          <div class="form-actions">
            <button class="button primary" type="submit" [disabled]="savingFood">{{ editingFoodId ? 'Update food' : 'Add food' }}</button>
            @if (editingFoodId) { <button class="button quiet" type="button" (click)="cancelFoodEdit()">Cancel</button> }
          </div>
        </form>
        @if (foodError) { <p class="notice error" role="alert">{{ foodError }}</p> }
        <div class="list-toolbar">
          <h3>Saved foods <span class="count">{{ filteredFoods.length }}</span></h3>
          <label class="search-control">Search foods
            <input type="search" [(ngModel)]="foodSearch" name="foodSearch" placeholder="Name or brand" />
          </label>
        </div>
        @if (loading) { <p role="status">Loading foods…</p> }
        @else if (!filteredFoods.length) { <p class="empty-state">{{ foodSearch ? 'No foods match your search.' : 'Your food library is empty. Add a food above to get started.' }}</p> }
        @else {
          <div class="table-wrap"><table>
            <thead><tr><th>Food</th><th>Serving</th><th>Calories</th><th>Protein</th><th>Actions</th></tr></thead>
            <tbody>@for (food of filteredFoods; track food.id) {
              <tr><td><strong>{{ food.name }}</strong><small>{{ food.brand || 'No brand' }}</small></td>
                <td>{{ food.servingSizeGrams }} g</td><td>{{ food.calories | number:'1.0-0' }} kcal</td>
                <td>{{ food.proteinGrams | number:'1.0-1' }} g</td>
                <td class="actions"><button class="button quiet small" type="button" (click)="editFood(food)">Edit</button>
                  <button class="button danger small" type="button" (click)="deleteFood(food)">Delete</button></td>
              </tr>
            }</tbody>
          </table></div>
        }
      </section>

      <section id="plans" class="account-card page-section">
        <div class="section-title">
          <div><p class="eyebrow">PLAN &amp; PREPARE</p><h2>{{ editingMealPlanId ? 'Edit meal plan' : 'Meal plans' }}</h2></div>
          <span class="section-index">03</span>
        </div>
        <form class="form-grid" (ngSubmit)="saveMealPlan()">
          <label>Plan or meal name<input [(ngModel)]="mealPlanForm.name" name="planName" required maxlength="120" /></label>
          <label>Meal type<select [(ngModel)]="mealPlanForm.mealType" name="planMealType" required>
            @for (type of mealTypes; track type) { <option [ngValue]="type">{{ type.replace('_', ' ') }}</option> }
          </select></label>
          <label>Calories target<input type="number" min="0" [(ngModel)]="mealPlanForm.targetCalories" name="planCalories" required /></label>
          <label>Description / preparation<input [(ngModel)]="mealPlanForm.description" name="planDescription" maxlength="500" /></label>
          @for (item of mealPlanItems; track $index) {
            <div class="food-picker">
              <label>Saved food<select [(ngModel)]="item.foodId" [name]="'planFood' + $index" required>
                <option [ngValue]="0">Choose food</option>@for (food of foods; track food.id) {
                  <option [ngValue]="food.id">{{ food.name }} · {{ food.calories }} kcal</option>
                }
              </select></label>
              <label>Servings<input type="number" min="0.01" step="0.1" [(ngModel)]="item.servings" [name]="'planServings' + $index" required /></label>
              @if (mealPlanItems.length > 1) { <button class="button quiet small remove-item" type="button" (click)="removePlanFood($index)" aria-label="Remove food">×</button> }
            </div>
          }
          <div class="form-actions">
            <button class="button quiet" type="button" (click)="addPlanFood()">Add another food</button>
            <button class="button primary" type="submit">{{ editingMealPlanId ? 'Update plan' : 'Save meal plan' }}</button>
            @if (editingMealPlanId) { <button class="button quiet" type="button" (click)="cancelMealPlanEdit()">Cancel</button> }
          </div>
        </form>
        @if (mealPlanError) { <p class="notice error" role="alert">{{ mealPlanError }}</p> }
        @if (!mealPlans.length) { <p class="empty-state">No meal plans saved yet. Create one using foods in your library.</p> }
        <div class="card-list">@for (plan of mealPlans; track plan.id) {
          <article class="record-card">
            <div class="record-heading"><div><span class="pill">{{ plan.mealType.replace('_', ' ') }}</span><h3>{{ plan.name }}</h3></div>
              <strong>{{ plan.targetCalories | number:'1.0-0' }} <small>kcal</small></strong></div>
            @if (plan.description) { <p class="muted">{{ plan.description }}</p> }
            <ul class="compact-list">@for (item of plan.foods; track $index) { <li>{{ item.foodName }} · {{ item.servings }} servings</li> }</ul>
            <div class="actions"><button class="button quiet small" type="button" (click)="editMealPlan(plan)">Edit</button>
              <button class="button danger small" type="button" (click)="deleteMealPlan(plan)">Delete</button></div>
          </article>
        }</div>
      </section>

      <section id="journal" class="account-card page-section">
        <div class="section-title">
          <div><p class="eyebrow">DAILY TRACKING</p><h2>{{ editingJournalId ? 'Edit journal entry' : 'Food journal' }}</h2></div>
          <span class="section-index">04</span>
        </div>
        <p class="muted">Daily totals are calculated by the backend from your saved foods and serving quantities.</p>
        @if (!foods.length) { <p class="notice">Add at least one food to your library before logging a meal.</p> }
        <form class="form-grid" (ngSubmit)="saveJournalEntry()">
          <label>Food<select [(ngModel)]="journalForm.foodId" name="journalFood" required>
            <option [ngValue]="0">Choose food</option>@for (food of foods; track food.id) {
              <option [ngValue]="food.id">{{ food.name }} · {{ food.calories }} kcal/serving</option>
            }
          </select></label>
          <label>Meal type<select [(ngModel)]="journalForm.mealType" name="journalMealType" required>
            @for (type of mealTypes; track type) { <option [ngValue]="type">{{ type.replace('_', ' ') }}</option> }
          </select></label>
          <label>Date<input type="date" [(ngModel)]="journalForm.entryDate" name="journalDate" required /></label>
          <label>Servings<input type="number" min="0.01" step="0.1" [(ngModel)]="journalForm.servings" name="journalServings" required /></label>
          <label class="full-width">Notes<input [(ngModel)]="journalForm.notes" name="journalNotes" maxlength="300" /></label>
          <div class="form-actions"><button class="button primary" type="submit" [disabled]="!foods.length">{{ editingJournalId ? 'Update entry' : 'Log food' }}</button>
            @if (editingJournalId) { <button class="button quiet" type="button" (click)="cancelJournalEdit()">Cancel</button> }</div>
        </form>
        @if (journalError) { <p class="notice error" role="alert">{{ journalError }}</p> }
        <div class="section-title list-toolbar"><h3>Entries for {{ dailyNutritionDate }}</h3>
          <button class="button quiet small" type="button" (click)="loadDailyNutrition()">Refresh daily totals</button></div>
        @if (journalForSelectedDate.length) {
          <div class="table-wrap"><table><thead><tr><th>Food</th><th>Meal</th><th>Servings</th><th>Actions</th></tr></thead>
            <tbody>@for (entry of journalForSelectedDate; track entry.id) {
              <tr><td><strong>{{ foodName(entry.foodId) }}</strong><small>{{ entry.entryDate }}</small></td>
                <td>{{ entry.mealType.replace('_', ' ') }}</td><td>{{ entry.servings }}</td>
                <td class="actions"><button class="button quiet small" type="button" (click)="editJournalEntry(entry)">Edit</button>
                  <button class="button danger small" type="button" (click)="deleteJournalEntry(entry)">Delete</button></td>
              </tr>
            }</tbody></table></div>
        } @else { <p class="empty-state">No entries recorded for this date.</p> }
        @if (dailyNutrition) {
          <div class="daily-summary"><strong>{{ dailyNutrition.totals.calories | number:'1.0-0' }} kcal</strong>
            <span>Protein {{ dailyNutrition.totals.proteinGrams | number:'1.0-1' }} g</span>
            <span>Carbs {{ dailyNutrition.totals.carbohydrateGrams | number:'1.0-1' }} g</span>
            <span>Fat {{ dailyNutrition.totals.fatGrams | number:'1.0-1' }} g</span>
            <span>Fiber {{ dailyNutrition.totals.fiberGrams | number:'1.0-1' }} g</span>
            <span>Sodium {{ dailyNutrition.totals.sodiumMg | number:'1.0-0' }} mg</span></div>
        }
      </section>

      <section id="progress" class="account-card page-section">
        <div class="section-title">
          <div><p class="eyebrow">YOUR HISTORY</p><h2>{{ editingMeasurementId ? 'Edit measurement' : 'Body tracking' }}</h2></div>
          <span class="section-index">05</span>
        </div>
        <p class="muted">Measurement values are your own entries. The chart shows only saved weight measurements; it does not infer body composition.</p>
        <form class="form-grid" (ngSubmit)="saveMeasurement()">
          <label>Measurement date<input type="date" [(ngModel)]="measurementForm.measurementDate" name="measurementDate" required /></label>
          <label>Weight <span class="unit">kg</span><input type="number" min="0.1" step="0.1" [(ngModel)]="measurementForm.weightKg" name="measurementWeight" required /></label>
          <label>Waist <span class="unit">cm</span><input type="number" min="0.1" step="0.1" [(ngModel)]="measurementForm.waistCm" name="measurementWaist" required /></label>
          <label>Hip <span class="unit">cm</span><input type="number" min="0.1" step="0.1" [(ngModel)]="measurementForm.hipCm" name="measurementHip" required /></label>
          <label>Chest <span class="unit">cm</span><input type="number" min="0.1" step="0.1" [(ngModel)]="measurementForm.chestCm" name="measurementChest" required /></label>
          <label>Arm <span class="unit">cm</span><input type="number" min="0.1" step="0.1" [(ngModel)]="measurementForm.armCm" name="measurementArm" required /></label>
          <label>Thigh <span class="unit">cm</span><input type="number" min="0.1" step="0.1" [(ngModel)]="measurementForm.thighCm" name="measurementThigh" required /></label>
          <label class="full-width">Notes<input [(ngModel)]="measurementForm.notes" name="measurementNotes" maxlength="300" /></label>
          <div class="form-actions"><button class="button primary" type="submit">{{ editingMeasurementId ? 'Update measurement' : 'Save measurement' }}</button>
            @if (editingMeasurementId) { <button class="button quiet" type="button" (click)="cancelMeasurementEdit()">Cancel</button> }</div>
        </form>
        @if (measurementError) { <p class="notice error" role="alert">{{ measurementError }}</p> }
        @if (!measurements.length) { <p class="empty-state">No measurements saved. Your history will build as you add entries.</p> }
        @else {
          <div class="table-wrap"><table><thead><tr><th>Date</th><th>Weight</th><th>Waist</th><th>Hip</th><th>Actions</th></tr></thead>
            <tbody>@for (measurement of measurements; track measurement.id) {
              <tr><td><strong>{{ measurement.measurementDate }}</strong></td><td>{{ measurement.weightKg }} kg</td>
                <td>{{ measurement.waistCm }} cm</td><td>{{ measurement.hipCm }} cm</td>
                <td class="actions"><button class="button quiet small" type="button" (click)="editMeasurement(measurement)">Edit</button>
                  <button class="button danger small" type="button" (click)="deleteMeasurement(measurement)">Delete</button></td>
              </tr>
            }</tbody></table></div>
        }
      </section>

      <section class="account-card page-section calculation-section">
        <div class="section-title"><div><p class="eyebrow">CALCULATIONS</p><h2>Nutrition estimates</h2></div></div>
        <p class="muted">Values below come from authenticated backend calculation endpoints; the browser does not estimate BMI, macros, or energy expenditure.</p>
        <div class="two-col">
          <article class="estimate-card">
            <h3>BMI</h3>
            @if (bmiEstimate) { <strong class="estimate-value">{{ bmiEstimate.bmi | number:'1.1-1' }}</strong><p>{{ bmiEstimate.note }}</p> }
            @else { <p>{{ calculationErrors.bmi || 'BMI is not available until profile height and weight are saved.' }}</p> }
          </article>
          <article class="estimate-card">
            <h3>Daily macro targets</h3>
            @if (macroTargets) {
              <p>Saved target: {{ macroTargets.calorieTarget }} kcal</p>
              <p>Protein {{ macroTargets.proteinGrams | number:'1.0-1' }} g · Carbs {{ macroTargets.carbohydrateGrams | number:'1.0-1' }} g · Fat {{ macroTargets.fatGrams | number:'1.0-1' }} g</p>
              <small>{{ macroTargets.method }}</small>
            } @else { <p>{{ calculationErrors.macros || 'Save weight and a target calorie value to request macro estimates.' }}</p> }
          </article>
          <article class="estimate-card">
            <h3>BMR &amp; TDEE</h3>
            <p>{{ energyEstimate?.note || 'Energy estimates are not available.' }}</p>
          </article>
          <article class="estimate-card">
            <h3>Selected food calculation</h3>
            <label>Food<select [(ngModel)]="calculationFoodId" name="calculationFood">
              <option [ngValue]="0">Choose saved food</option>@for (food of foods; track food.id) {
                <option [ngValue]="food.id">{{ food.name }}</option>
              }
            </select></label>
            <label>Servings<input type="number" min="0.01" step="0.1" [(ngModel)]="calculationServings" name="calculationServings" /></label>
            <button class="button quiet" type="button" (click)="calculateSelectedFood()">Calculate from saved food</button>
            @if (calculation) { <p>{{ calculation.calories | number:'1.0-0' }} kcal · protein {{ calculation.proteinGrams | number:'1.0-1' }} g · carbs {{ calculation.carbohydrateGrams | number:'1.0-1' }} g · fat {{ calculation.fatGrams | number:'1.0-1' }} g</p> }
          </article>
        </div>
      </section>

      <section id="assistant" class="account-card page-section assistant-section">
        <div class="section-title">
          <div><p class="eyebrow">OPTIONAL LOCAL MODEL</p><h2>AI meal-plan assistant</h2></div>
          <span class="section-index">06</span>
        </div>
        <p>Uses your saved profile, food library, and the restrictions you enter here. Allergies must be explicitly listed. Suggestions are not medical advice or proof of allergy safety.</p>
        <p class="notice">Budget, cuisine, meals per day, disliked foods, and cooking preferences are not stored or supported by the current API. They are not collected or sent. Meal plans can be generated only when a compatible local LLM endpoint is configured.</p>
        <form class="form-grid" (ngSubmit)="generateAiPlan()">
          <label class="full-width">Allergies and ingredient restrictions
            <input [(ngModel)]="aiRestrictions" name="aiRestrictions" placeholder="Example: peanuts, shellfish" />
          </label>
          <div class="form-actions">
            <button class="button primary" type="submit" [disabled]="isGeneratingAiPlan || isSavingGeneratedPlan || !profile">
              {{ isGeneratingAiPlan ? 'Generating…' : aiPlan ? 'Regenerate suggestion' : 'Generate suggestion' }}
            </button>
            @if (!profile) { <small class="muted">Save a profile with a goal and calorie target first.</small> }
          </div>
        </form>
        @if (aiError) { <p class="notice error" role="alert">{{ aiError }}</p> }
        @if (aiPlan) {
          <p class="notice" [class.error]="!aiPlan.available" role="status">{{ aiPlan.note }}</p>
          @if (aiPlan.available && aiPlan.plan; as plan) {
            <h3>{{ plan.planName }} <span class="pill">{{ plan.goal }}</span></h3>
            <div class="nutrition-stats compact-stats">
              <div class="stat-card"><span>Daily calories</span><strong>{{ plan.dailyNutrition.calories | number:'1.0-0' }} kcal</strong></div>
              <div class="stat-card"><span>Protein</span><strong>{{ plan.dailyNutrition.proteinGrams | number:'1.0-1' }} g</strong></div>
              <div class="stat-card"><span>Carbs</span><strong>{{ plan.dailyNutrition.carbohydrateGrams | number:'1.0-1' }} g</strong></div>
              <div class="stat-card"><span>Fat</span><strong>{{ plan.dailyNutrition.fatGrams | number:'1.0-1' }} g</strong></div>
            </div>
            @for (meal of plan.meals; track $index) {
              <article class="record-card ai-meal">
                <div class="record-heading"><label>Meal name<input [(ngModel)]="meal.name" [name]="'aiName' + $index" /></label>
                  <label>Meal type<select [(ngModel)]="meal.mealType" [name]="'aiType' + $index">
                    @for (type of mealTypes; track type) { <option [ngValue]="type">{{ type.replace('_', ' ') }}</option> }
                  </select></label></div>
                <p class="muted">Nutrition source: {{ meal.nutritionSource.replace('_', ' ').toLowerCase() }}.
                  Editing foods marks model estimates for review.</p>
                @for (item of meal.foods; track $index) {
                  <div class="food-picker">
                    <label>Replace with saved food<select [(ngModel)]="item.foodId" (ngModelChange)="refreshAiNutrition()" [name]="'aiFood' + $index">
                      <option [ngValue]="null">Select saved food</option>@for (food of foods; track food.id) {
                        <option [ngValue]="food.id">{{ food.name }} · {{ food.calories }} kcal/serving</option>
                      }
                    </select></label>
                    <label>Servings<input type="number" min="0.01" step="0.1" [(ngModel)]="item.servings" (ngModelChange)="refreshAiNutrition()" [name]="'aiServings' + $index" /></label>
                  </div>
                }
                <label>Preparation instructions<textarea [(ngModel)]="meal.preparationInstructions" [name]="'aiPreparation' + $index" maxlength="350"></textarea></label>
                <p>Meal estimate: {{ meal.estimatedNutrition.calories | number:'1.0-0' }} kcal · protein {{ meal.estimatedNutrition.proteinGrams | number:'1.0-1' }} g · carbs {{ meal.estimatedNutrition.carbohydrateGrams | number:'1.0-1' }} g · fat {{ meal.estimatedNutrition.fatGrams | number:'1.0-1' }} g</p>
              </article>
            }
            @if (plan.shoppingList.length) { <h3>Shopping list</h3><ul class="compact-list">@for (item of plan.shoppingList; track $index) { <li>{{ item }}</li> }</ul> }
            @if (plan.alternatives.length) { <h3>Alternatives and substitutions</h3><ul class="compact-list">@for (item of plan.alternatives; track $index) { <li>{{ item }}</li> }</ul> }
            @if (hasAiNutritionReview) { <p class="notice">Choose a saved food for every meal item to recalculate estimated nutrition from your library before saving.</p> }
            <div class="form-actions"><button class="button primary" type="button" (click)="saveGeneratedAiPlan()" [disabled]="!canSaveGeneratedPlan()">
                {{ isSavingGeneratedPlan ? 'Saving…' : 'Save reviewed meals' }}</button>
              <button class="button quiet" type="button" (click)="generateAiPlan()" [disabled]="isGeneratingAiPlan || isSavingGeneratedPlan">Regenerate</button></div>
            <p class="muted">The current data model saves each meal separately and stores instructions in its description. The shopping list and alternatives are shown for review but are not persisted.</p>
          }
        } @else if (!loading) {
          <p class="empty-state">No generated suggestion yet. If no local model endpoint is configured, the API will explain how to enable it and will not return a fabricated plan.</p>
        }
      </section>

      <footer class="privacy-note">Your nutrition records are loaded through authenticated, member-scoped APIs. This page does not accept or send another member's identifier.</footer>
    </main>
  `,
  styles: [`
    .nutrition-page { padding-top: 38px; }
    .page-hero, .section-title, .section-heading, .record-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; }
    .page-hero { margin-bottom:30px; padding:30px; border:1px solid var(--border); border-radius:22px; background:linear-gradient(125deg, rgb(163 230 53 / 12%), rgb(23 35 28 / 80%) 55%); }
    .page-hero h1 { max-width:700px; margin-bottom:8px; }
    .page-hero .lead { margin-bottom:0; }
    .section-nav { display:flex; gap:8px; overflow-x:auto; margin:0 0 22px; padding:4px 0 12px; }
    .section-nav a { flex:none; padding:9px 13px; border:1px solid var(--border); border-radius:999px; color:var(--muted); font-size:.85rem; }
    .section-nav a:hover, .text-link:hover { color:var(--accent); border-color:var(--accent); }
    .dashboard-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
    .dashboard-main { grid-column:span 2; }
    .dashboard-wide { grid-column:span 2; }
    .dashboard-grid .account-card, .page-section { margin-bottom:18px; }
    .page-section { scroll-margin-top:20px; }
    .section-index { display:grid; width:42px; height:42px; place-items:center; border:1px solid var(--border); border-radius:50%; color:var(--accent); font-weight:700; }
    .section-title h2, .section-heading h2 { margin-bottom:0; }
    .muted, .unit, .chart-caption { color:var(--muted); }
    .target-line { margin:16px 0 0; color:#d7f7a0; font-weight:600; }
    .nutrition-stats { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; margin-top:18px; }
    .stat-card { display:grid; gap:6px; padding:16px; border:1px solid var(--border); border-radius:12px; background:rgb(10 18 14 / 28%); }
    .stat-card span, .stat-card small { color:var(--muted); font-size:.82rem; }
    .stat-card strong { font:600 1.4rem 'Space Grotesk',sans-serif; }
    .primary-stat { border-color:rgb(163 230 53 / 35%); }
    .meal-totals { display:flex; flex-wrap:wrap; gap:10px; margin-top:18px; }
    .meal-totals div { display:flex; gap:14px; align-items:center; padding:10px 12px; border-radius:9px; background:rgb(255 255 255 / 4%); }
    .meal-totals span, .recent-row small { color:var(--muted); font-size:.82rem; }
    .profile-summary dl { margin:16px 0; }
    .profile-summary dl div, .macro-row { display:flex; justify-content:space-between; gap:12px; padding:8px 0; border-bottom:1px solid var(--border); }
    dt, .macro-row span { color:var(--muted); } dd { margin:0; font-weight:600; }
    .text-link { color:var(--accent); font-weight:700; }
    .recent-list { display:grid; }
    .recent-row { display:flex; align-items:center; gap:12px; padding:12px 0; border-bottom:1px solid var(--border); }
    .recent-row > div { display:grid; flex:1; gap:3px; }
    .meal-dot { width:9px; height:9px; border-radius:50%; background:var(--accent); }
    .quick-actions { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; }
    .quick-action { display:grid; gap:4px; padding:15px; border:1px solid var(--border); border-radius:12px; background:rgb(23 35 28 / 65%); }
    .quick-action > span { color:var(--accent); font-size:1.4rem; }
    .quick-action small { color:var(--muted); }
    .empty-state { padding:16px; border:1px dashed var(--border); border-radius:10px; color:var(--muted); }
    .presentation-choice { display:flex; flex-wrap:wrap; gap:12px; margin:22px 0; padding:0; border:0; }
    .presentation-choice legend { width:100%; margin-bottom:10px; font-weight:700; }
    .presentation-card { display:grid; min-width:160px; justify-items:center; gap:8px; padding:14px 22px; border:1px solid var(--border); border-radius:14px; color:#eef4ee; background:rgb(10 18 14 / 26%); cursor:pointer; }
    .presentation-card.selected { border-color:var(--accent); background:rgb(163 230 53 / 9%); }
    .presentation-card svg { width:46px; height:58px; fill:var(--accent); }
    .form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:15px; margin-top:20px; }
    label { display:grid; gap:7px; color:#e5eee6; font-size:.9rem; font-weight:600; }
    input, select, textarea { width:100%; min-height:43px; padding:9px 12px; border:1px solid var(--border); border-radius:9px; color:#eef4ee; background:#111c16; font:inherit; }
    input:focus, select:focus, textarea:focus, button:focus-visible, a:focus-visible { outline:2px solid var(--accent); outline-offset:2px; }
    input[type="date"] { color-scheme:dark; }
    textarea { min-height:80px; resize:vertical; }
    .unit { font-size:.78rem; font-weight:400; }
    .full-width, .form-actions { grid-column:1 / -1; }
    .form-actions { display:flex; flex-wrap:wrap; align-items:center; gap:10px; }
    .button:disabled { opacity:.55; cursor:not-allowed; }
    .button.danger { color:#ffc4b7; border:1px solid rgb(255 137 112 / 35%); background:transparent; }
    .small { min-height:34px; padding:0 11px; font-size:.8rem; }
    .list-toolbar { display:flex; align-items:end; justify-content:space-between; gap:18px; margin:26px 0 12px; }
    .list-toolbar h3 { margin:0; }
    .search-control { width:min(100%,300px); }
    .count, .pill { display:inline-block; padding:4px 9px; border-radius:999px; color:#d7f7a0; background:rgb(163 230 53 / 10%); font-size:.76rem; }
    .table-wrap { overflow-x:auto; margin-top:14px; }
    table { width:100%; border-collapse:collapse; text-align:left; }
    th, td { padding:12px 10px; border-bottom:1px solid var(--border); white-space:nowrap; }
    th { color:var(--muted); font-size:.78rem; font-weight:600; }
    td small { display:block; margin-top:3px; color:var(--muted); }
    .actions { display:flex; flex-wrap:wrap; gap:6px; }
    .card-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; margin-top:18px; }
    .record-card, .estimate-card { padding:18px; border:1px solid var(--border); border-radius:12px; background:rgb(10 18 14 / 22%); }
    .record-card h3 { margin:9px 0 4px; }
    .record-heading > strong { color:var(--accent); white-space:nowrap; }
    .record-heading > strong small { color:var(--muted); }
    .compact-list { margin:12px 0; padding-left:20px; color:var(--muted); }
    .food-picker { display:grid; grid-template-columns:1fr 160px auto; align-items:end; gap:12px; grid-column:1 / -1; }
    .remove-item { margin-bottom:2px; }
    .daily-summary { display:flex; flex-wrap:wrap; gap:12px 22px; margin-top:18px; padding:16px; border-radius:10px; background:rgb(163 230 53 / 6%); }
    .daily-summary strong { color:var(--accent); }
    .weight-chart { width:100%; height:auto; margin-top:12px; overflow:visible; }
    .chart-axis { stroke:var(--border); stroke-width:1; }
    .chart-line { fill:none; stroke:var(--accent); stroke-width:3; stroke-linecap:round; stroke-linejoin:round; }
    .chart-dot { fill:var(--accent); stroke:#17231c; stroke-width:2; }
    .chart-caption { display:flex; justify-content:space-between; font-size:.72rem; }
    .chart-caption span { text-align:center; }
    .estimate-value { color:var(--accent); font:600 2rem 'Space Grotesk',sans-serif; }
    .estimate-card small { color:var(--muted); }
    .calculation-section .two-col { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; margin-top:18px; }
    .estimate-card .button { margin-top:10px; }
    .assistant-section { border-color:rgb(163 230 53 / 25%); }
    .ai-meal { display:grid; gap:12px; margin:14px 0; }
    .ai-meal .record-heading { align-items:start; }
    .ai-meal .record-heading label { flex:1; }
    .compact-stats { margin:16px 0; }
    .privacy-note { padding:14px 0 28px; color:var(--muted); font-size:.82rem; }
    @media (max-width:760px) {
      .dashboard-grid { grid-template-columns:1fr; }
      .dashboard-main, .dashboard-wide { grid-column:auto; }
      .nutrition-stats, .quick-actions { grid-template-columns:repeat(2,minmax(0,1fr)); }
      .card-list, .calculation-section .two-col { grid-template-columns:1fr; }
    }
    @media (max-width:540px) {
      .page-hero { align-items:flex-start; flex-direction:column; padding:22px; }
      .page-hero .button { width:100%; }
      .form-grid { grid-template-columns:1fr; }
      .full-width, .form-actions { grid-column:auto; }
      .food-picker { grid-template-columns:1fr 100px auto; gap:7px; }
      .section-title { align-items:flex-start; }
      .list-toolbar { align-items:stretch; flex-direction:column; }
      .search-control { width:100%; }
      .quick-actions { grid-template-columns:repeat(2,minmax(0,1fr)); }
      th, td { padding:10px 7px; }
      .ai-meal .record-heading { flex-direction:column; }
    }
  `]
})
export class NutritionComponent implements OnInit {
  private readonly nutritionApi = inject(NutritionApiService);
  readonly mealTypes: NutritionMealType[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK', 'PRE_WORKOUT', 'POST_WORKOUT'];
  readonly activityLevels = ['sedentary', 'light', 'moderate', 'active', 'very active'];
  readonly fitnessGoals = ['weight loss', 'maintenance', 'muscle gain'];

  loading = true;
  savingProfile = false;
  savingFood = false;
  dailyNutritionLoading = false;
  isGeneratingAiPlan = false;
  isSavingGeneratedPlan = false;
  pageError = '';
  successMessage = '';
  profileError = '';
  foodError = '';
  mealPlanError = '';
  journalError = '';
  measurementError = '';
  aiError = '';
  calculationErrors: { bmi: string; macros: string } = { bmi: '', macros: '' };

  foods: NutritionFood[] = [];
  mealPlans: NutritionMealPlan[] = [];
  journal: NutritionJournalEntry[] = [];
  measurements: BodyMeasurement[] = [];
  profile: NutritionProfile | null = null;
  dailyNutrition: DailyNutrition | null = null;
  bmiEstimate: NutritionBmiResult | null = null;
  macroTargets: NutritionMacroTargets | null = null;
  energyEstimate: NutritionEnergyEstimate | null = null;
  calculation: NutritionCalculation | null = null;
  aiPlan: AiMealPlanResponse | null = null;
  private generatedPlanRestrictions: string[] = [];

  dailyNutritionDate = new Date().toISOString().slice(0, 10);
  foodSearch = '';
  bodyPresentation: 'female' | 'male' | null = null;
  calculationFoodId = 0;
  calculationServings = 1;
  editingFoodId: number | null = null;
  editingMealPlanId: number | null = null;
  editingJournalId: number | null = null;
  editingMeasurementId: number | null = null;
  aiRestrictions = '';
  sessionPreferences = { targetWeightKg: null as number | null, trainingFrequency: null as number | null };

  profileForm = {
    profileName: 'My nutrition profile',
    goal: '',
    dietaryPreference: '',
    activityLevel: '',
    age: null as number | null,
    heightCm: null as number | null,
    weightKg: null as number | null,
    targetCalories: null as number | null
  };

  foodForm = this.emptyFoodForm();
  mealPlanForm = {
    name: '',
    description: '',
    mealType: 'BREAKFAST' as NutritionMealType,
    targetCalories: 0
  };
  mealPlanItems: { foodId: number; servings: number }[] = [{ foodId: 0, servings: 1 }];
  journalForm = this.emptyJournalForm();
  measurementForm = this.emptyMeasurementForm();

  ngOnInit(): void {
    this.loadData();
  }

  get filteredFoods(): NutritionFood[] {
    const term = this.foodSearch.trim().toLowerCase();
    return this.foods.filter((food) => !term
      || food.name.toLowerCase().includes(term)
      || (food.brand ?? '').toLowerCase().includes(term));
  }

  get journalForSelectedDate(): NutritionJournalEntry[] {
    return this.journal.filter((entry) => entry.entryDate === this.dailyNutritionDate);
  }

  get recentJournalEntries(): NutritionJournalEntry[] {
    return [...this.journal].sort((a, b) => b.entryDate.localeCompare(a.entryDate)).slice(0, 5);
  }

  get remainingCalories(): number | null {
    if (this.profile?.targetCalories == null || !this.dailyNutrition) return null;
    return this.profile.targetCalories - this.dailyNutrition.totals.calories;
  }

  get orderedMeasurements(): BodyMeasurement[] {
    return [...this.measurements].sort((a, b) => a.measurementDate.localeCompare(b.measurementDate));
  }

  get weightChartDots(): { x: number; y: number }[] {
    const values = this.orderedMeasurements;
    if (values.length < 2) return [];
    const weights = values.map((entry) => entry.weightKg);
    const min = Math.min(...weights);
    const max = Math.max(...weights);
    const spread = Math.max(max - min, 1);
    return values.map((entry, index) => ({
      x: 20 + index * (460 / (values.length - 1)),
      y: 130 - ((entry.weightKg - min) / spread) * 100
    }));
  }

  get weightChartPoints(): string {
    return this.weightChartDots.map((point) => `${point.x},${point.y}`).join(' ');
  }

  get chartStartDate(): string {
    return this.orderedMeasurements[0]?.measurementDate ?? '';
  }

  get chartEndDate(): string {
    return this.orderedMeasurements.at(-1)?.measurementDate ?? '';
  }

  get hasAiNutritionReview(): boolean {
    return !!this.aiPlan?.plan?.meals.some((meal) => meal.nutritionSource === 'REVIEW_REQUIRED');
  }

  private loadData(): void {
    this.loading = true;
    this.pageError = '';
    forkJoin({
      foods: this.nutritionApi.getFoods(),
      mealPlans: this.nutritionApi.getMealPlans(),
      journal: this.nutritionApi.getJournal(),
      measurements: this.nutritionApi.getMeasurements(),
      profile: this.nutritionApi.getProfile().pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ foods, mealPlans, journal, measurements, profile }) => {
        this.foods = foods;
        this.mealPlans = mealPlans;
        this.journal = journal;
        this.measurements = measurements;
        this.profile = profile;
        if (profile) this.setProfileForm(profile);
        this.loading = false;
        this.loadDashboardCalculations();
        this.loadDailyNutrition();
      },
      error: () => {
        this.loading = false;
        this.pageError = 'Could not load your nutrition records. Check your connection and sign-in, then reload this page.';
      }
    });
  }

  private setProfileForm(profile: NutritionProfile): void {
    this.profileForm = {
      profileName: profile.profileName,
      goal: profile.goal,
      dietaryPreference: profile.dietaryPreference,
      activityLevel: profile.activityLevel,
      age: profile.age,
      heightCm: profile.heightCm,
      weightKg: profile.weightKg,
      targetCalories: profile.targetCalories
    };
  }

  private loadDashboardCalculations(): void {
    this.calculationErrors = { bmi: '', macros: '' };
    this.nutritionApi.calculateBmi().subscribe({
      next: (result) => this.bmiEstimate = result,
      error: (error: { error?: { message?: string } }) => {
        this.bmiEstimate = null;
        this.calculationErrors.bmi = error.error?.message ?? 'BMI is not available from the calculation API.';
      }
    });
    this.nutritionApi.calculateMacroTargets().subscribe({
      next: (result) => this.macroTargets = result,
      error: (error: { error?: { message?: string } }) => {
        this.macroTargets = null;
        this.calculationErrors.macros = error.error?.message ?? 'Macro estimates are not available from the calculation API.';
      }
    });
    this.nutritionApi.calculateEnergyEstimate().subscribe({
      next: (result) => this.energyEstimate = result,
      error: () => this.energyEstimate = null
    });
  }

  saveProfile(): void {
    this.profileError = '';
    this.successMessage = '';
    this.savingProfile = true;
    this.nutritionApi.saveProfile(this.profileForm).subscribe({
      next: (profile) => {
        this.profile = profile;
        this.savingProfile = false;
        this.successMessage = 'Your supported profile details were saved.';
        this.loadDashboardCalculations();
        this.loadDailyNutrition();
      },
      error: (error: { error?: { message?: string }; message?: string }) => {
        this.savingProfile = false;
        this.profileError = error.error?.message ?? error.message ?? 'Could not save the nutrition profile.';
      }
    });
  }

  private emptyFoodForm(): Omit<NutritionFood, 'id'> {
    return { name: '', brand: '', servingSizeGrams: 100, calories: 0, proteinGrams: 0,
      carbohydrateGrams: 0, fatGrams: 0, fiberGrams: 0, sodiumMg: 0 };
  }

  saveFood(): void {
    this.foodError = '';
    const request = this.editingFoodId
      ? this.nutritionApi.updateFood(this.editingFoodId, this.foodForm)
      : this.nutritionApi.createFood(this.foodForm);
    request.subscribe({
      next: () => {
        this.successMessage = this.editingFoodId ? 'Food updated.' : 'Food added to your library.';
        this.cancelFoodEdit();
        this.loadData();
      },
      error: (error: { error?: { message?: string }; message?: string }) => {
        this.foodError = error.error?.message ?? error.message ?? 'Could not save this food.';
      }
    });
  }

  editFood(food: NutritionFood): void {
    if (food.id == null) return;
    this.editingFoodId = food.id;
    this.foodForm = { ...food, brand: food.brand ?? '' };
    document.getElementById('foods')?.scrollIntoView({ behavior: 'smooth' });
  }

  cancelFoodEdit(): void {
    this.editingFoodId = null;
    this.foodForm = this.emptyFoodForm();
    this.foodError = '';
  }

  deleteFood(food: NutritionFood): void {
    if (food.id == null || !window.confirm(`Delete ${food.name} from your food library?`)) return;
    this.foodError = '';
    this.nutritionApi.deleteFood(food.id).subscribe({
      next: () => { this.successMessage = 'Food deleted.'; this.loadData(); },
      error: (error: { status?: number; error?: { message?: string } }) => {
        this.foodError = error.status === 409
          ? 'This food is referenced by a meal plan or journal entry and cannot be deleted. Update or remove those records first.'
          : error.error?.message ?? 'Could not delete this food.';
      }
    });
  }

  private emptyMealPlanForm(): typeof this.mealPlanForm {
    return { name: '', description: '', mealType: 'BREAKFAST', targetCalories: 0 };
  }

  addPlanFood(): void {
    this.mealPlanItems.push({ foodId: 0, servings: 1 });
  }

  removePlanFood(index: number): void {
    if (this.mealPlanItems.length > 1) this.mealPlanItems.splice(index, 1);
  }

  saveMealPlan(): void {
    this.mealPlanError = '';
    const payload: CreateNutritionMealPlan = {
      ...this.mealPlanForm,
      foods: this.mealPlanItems.map((item) => ({ ...item }))
    };
    const request = this.editingMealPlanId
      ? this.nutritionApi.updateMealPlan(this.editingMealPlanId, payload)
      : this.nutritionApi.createMealPlan(payload);
    request.subscribe({
      next: () => {
        this.successMessage = this.editingMealPlanId ? 'Meal plan updated.' : 'Meal plan saved.';
        this.cancelMealPlanEdit();
        this.loadData();
      },
      error: (error: { error?: { message?: string }; message?: string }) => {
        this.mealPlanError = error.error?.message ?? error.message ?? 'Could not save this meal plan.';
      }
    });
  }

  editMealPlan(plan: NutritionMealPlan): void {
    if (plan.id == null) return;
    this.editingMealPlanId = plan.id;
    this.mealPlanForm = { name: plan.name, description: plan.description ?? '',
      mealType: plan.mealType, targetCalories: plan.targetCalories };
    this.mealPlanItems = plan.foods.map((food) => ({ foodId: food.foodId, servings: food.servings }));
    document.getElementById('plans')?.scrollIntoView({ behavior: 'smooth' });
  }

  cancelMealPlanEdit(): void {
    this.editingMealPlanId = null;
    this.mealPlanForm = this.emptyMealPlanForm();
    this.mealPlanItems = [{ foodId: 0, servings: 1 }];
  }

  deleteMealPlan(plan: NutritionMealPlan): void {
    if (plan.id == null || !window.confirm(`Delete meal plan "${plan.name}"?`)) return;
    this.nutritionApi.deleteMealPlan(plan.id).subscribe({
      next: () => { this.successMessage = 'Meal plan deleted.'; this.loadData(); },
      error: (error: { error?: { message?: string } }) => {
        this.mealPlanError = error.error?.message ?? 'Could not delete this meal plan.';
      }
    });
  }

  private emptyJournalForm(): Omit<NutritionJournalEntry, 'id'> {
    return { foodId: 0, mealType: 'BREAKFAST', entryDate: this.dailyNutritionDate, servings: 1, notes: '' };
  }

  saveJournalEntry(): void {
    this.journalError = '';
    const request = this.editingJournalId
      ? this.nutritionApi.updateJournalEntry(this.editingJournalId, this.journalForm)
      : this.nutritionApi.createJournalEntry(this.journalForm);
    request.subscribe({
      next: () => {
        this.dailyNutritionDate = this.journalForm.entryDate;
        this.successMessage = this.editingJournalId ? 'Journal entry updated.' : 'Food logged.';
        this.cancelJournalEdit();
        this.loadData();
      },
      error: (error: { error?: { message?: string }; message?: string }) => {
        this.journalError = error.error?.message ?? error.message ?? 'Could not save this journal entry.';
      }
    });
  }

  editJournalEntry(entry: NutritionJournalEntry): void {
    if (entry.id == null) return;
    this.editingJournalId = entry.id;
    this.journalForm = { foodId: entry.foodId, mealType: entry.mealType,
      entryDate: entry.entryDate, servings: entry.servings, notes: entry.notes ?? '' };
  }

  cancelJournalEdit(): void {
    this.editingJournalId = null;
    this.journalForm = this.emptyJournalForm();
    this.journalError = '';
  }

  deleteJournalEntry(entry: NutritionJournalEntry): void {
    if (entry.id == null || !window.confirm('Delete this food journal entry?')) return;
    this.nutritionApi.deleteJournalEntry(entry.id).subscribe({
      next: () => { this.successMessage = 'Journal entry deleted.'; this.loadData(); },
      error: (error: { error?: { message?: string } }) => {
        this.journalError = error.error?.message ?? 'Could not delete this journal entry.';
      }
    });
  }

  loadDailyNutrition(): void {
    this.dailyNutritionLoading = true;
    this.nutritionApi.getDailyNutrition(this.dailyNutritionDate).subscribe({
      next: (result) => { this.dailyNutrition = result; this.dailyNutritionLoading = false; },
      error: (error: { error?: { message?: string } }) => {
        this.dailyNutrition = null;
        this.dailyNutritionLoading = false;
        this.pageError = error.error?.message ?? 'Could not load daily nutrition totals.';
      }
    });
  }

  private emptyMeasurementForm(): Omit<BodyMeasurement, 'id'> {
    return { measurementDate: new Date().toISOString().slice(0, 10), weightKg: 0,
      waistCm: 0, hipCm: 0, chestCm: 0, armCm: 0, thighCm: 0, notes: '' };
  }

  saveMeasurement(): void {
    this.measurementError = '';
    const request = this.editingMeasurementId
      ? this.nutritionApi.updateMeasurement(this.editingMeasurementId, this.measurementForm)
      : this.nutritionApi.createMeasurement(this.measurementForm);
    request.subscribe({
      next: () => {
        this.successMessage = this.editingMeasurementId ? 'Measurement updated.' : 'Measurement saved.';
        this.cancelMeasurementEdit();
        this.loadData();
      },
      error: (error: { error?: { message?: string }; message?: string }) => {
        this.measurementError = error.error?.message ?? error.message ?? 'Could not save this measurement.';
      }
    });
  }

  editMeasurement(measurement: BodyMeasurement): void {
    if (measurement.id == null) return;
    this.editingMeasurementId = measurement.id;
    this.measurementForm = { ...measurement, notes: measurement.notes ?? '' };
  }

  cancelMeasurementEdit(): void {
    this.editingMeasurementId = null;
    this.measurementForm = this.emptyMeasurementForm();
    this.measurementError = '';
  }

  deleteMeasurement(measurement: BodyMeasurement): void {
    if (measurement.id == null || !window.confirm(`Delete measurement from ${measurement.measurementDate}?`)) return;
    this.nutritionApi.deleteMeasurement(measurement.id).subscribe({
      next: () => { this.successMessage = 'Measurement deleted.'; this.loadData(); },
      error: (error: { error?: { message?: string } }) => {
        this.measurementError = error.error?.message ?? 'Could not delete this measurement.';
      }
    });
  }

  foodName(foodId: number): string {
    return this.foods.find((food) => food.id === foodId)?.name ?? 'Saved food';
  }

  calculateSelectedFood(): void {
    if (!this.calculationFoodId || this.calculationServings <= 0) return;
    this.nutritionApi.calculateNutrition([this.calculationFoodId], [this.calculationServings]).subscribe({
      next: (result) => this.calculation = result,
      error: (error: { error?: { message?: string } }) => {
        this.pageError = error.error?.message ?? 'Could not calculate this food total.';
      }
    });
  }

  generateAiPlan(): void {
    if (!this.profile || this.isGeneratingAiPlan || this.isSavingGeneratedPlan) return;
    const restrictions = this.aiRestrictions.split(',').map((item) => item.trim()).filter(Boolean);
    this.aiError = '';
    this.isGeneratingAiPlan = true;
    this.nutritionApi.generatePlan({ restrictions }).subscribe({
      next: (result) => {
        this.aiPlan = result;
        this.generatedPlanRestrictions = restrictions;
        this.isGeneratingAiPlan = false;
      },
      error: (error: { status?: number }) => {
        this.aiError = this.mealPlanRequestError(error, 'Could not contact the meal-plan assistant.');
        this.isGeneratingAiPlan = false;
      }
    });
  }

  refreshAiNutrition(): void {
    const plan = this.aiPlan?.plan;
    if (!plan) return;
    for (const meal of plan.meals) {
      const selected = meal.foods.map((item) => this.foods.find((food) => food.id === item.foodId));
      if (selected.some((food) => !food)) {
        meal.nutritionSource = 'REVIEW_REQUIRED';
        continue;
      }
      meal.estimatedNutrition = meal.foods.reduce((total, item, index) => {
        const food = selected[index]!;
        return {
          calories: total.calories + food.calories * item.servings,
          proteinGrams: total.proteinGrams + food.proteinGrams * item.servings,
          carbohydrateGrams: total.carbohydrateGrams + food.carbohydrateGrams * item.servings,
          fatGrams: total.fatGrams + food.fatGrams * item.servings
        };
      }, { calories: 0, proteinGrams: 0, carbohydrateGrams: 0, fatGrams: 0 });
      meal.nutritionSource = 'SAVED_FOOD_DATA';
    }
    plan.dailyNutrition = plan.meals.reduce((total, meal) => ({
      calories: total.calories + meal.estimatedNutrition.calories,
      proteinGrams: total.proteinGrams + meal.estimatedNutrition.proteinGrams,
      carbohydrateGrams: total.carbohydrateGrams + meal.estimatedNutrition.carbohydrateGrams,
      fatGrams: total.fatGrams + meal.estimatedNutrition.fatGrams
    }), { calories: 0, proteinGrams: 0, carbohydrateGrams: 0, fatGrams: 0 });
  }

  canSaveGeneratedPlan(): boolean {
    return !this.isGeneratingAiPlan && !this.isSavingGeneratedPlan
      && !!this.aiPlan?.available && !!this.aiPlan.plan && !this.hasAiNutritionReview
      && this.aiPlan.plan.meals.every((meal) => meal.name.trim().length > 0
        && meal.preparationInstructions.trim().length > 0
        && meal.foods.length > 0
        && meal.foods.every((food) => !!food.foodId && Number.isFinite(food.servings) && food.servings > 0));
  }

  saveGeneratedAiPlan(): void {
    const plan = this.aiPlan?.plan;
    if (!plan || this.isSavingGeneratedPlan || !this.canSaveGeneratedPlan()) {
      this.aiError = 'Choose a saved food for every ingredient and confirm each serving quantity before saving.';
      return;
    }
    this.aiError = '';
    this.isSavingGeneratedPlan = true;
    this.nutritionApi.saveGeneratedPlan({
      planName: plan.planName,
      goal: plan.goal,
      restrictions: this.generatedPlanRestrictions,
      meals: plan.meals.map((meal) => ({
        name: meal.name,
        mealType: meal.mealType,
        preparationInstructions: meal.preparationInstructions,
        foods: meal.foods.map((food) => ({ foodId: food.foodId!, servings: food.servings }))
      }))
    }).subscribe({
      next: () => {
        this.isSavingGeneratedPlan = false;
        this.aiPlan = null;
        this.generatedPlanRestrictions = [];
        this.successMessage = 'Reviewed meals saved to your member-owned meal plans.';
        this.loadData();
      },
      error: (error: { status?: number }) => {
        this.aiError = this.mealPlanRequestError(error, 'Could not save the reviewed meal plan.');
        this.isSavingGeneratedPlan = false;
      }
    });
  }

  private mealPlanRequestError(error: { status?: number }, fallback: string): string {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.';
    if (error.status === 403) return 'Your account is not authorized to use this nutrition feature.';
    if (error.status === 503) return 'The meal-plan service is temporarily busy or unavailable. Please try again shortly.';
    return fallback;
  }
}
