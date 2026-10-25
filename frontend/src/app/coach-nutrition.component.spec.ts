import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { CoachNutritionApiService } from './coach-nutrition-api.service';
import { AssignedMember, MembershipApiService } from './membership-api.service';
import { NutritionFood, NutritionMealPlan, NutritionPlanView } from './nutrition-api.service';
import { CoachNutritionComponent } from './coach-nutrition.component';

describe('CoachNutritionComponent', () => {
  let fixture: ComponentFixture<CoachNutritionComponent>;
  let component: CoachNutritionComponent;
  let api: jasmine.SpyObj<CoachNutritionApiService>;
  let membershipApi: jasmine.SpyObj<MembershipApiService>;

  const member: AssignedMember = { memberProfileId: 'assigned-member', displayName: 'Assigned Member' };
  const food: NutritionFood = {
    id: 12, name: 'Oat bowl', brand: 'AI estimate', servingSizeGrams: 350, calories: 420,
    proteinGrams: 20, carbohydrateGrams: 55, fatGrams: 12, fiberGrams: 8, sodiumMg: 300
  };
  const plan: NutritionMealPlan = {
    id: 8,
    name: 'Breakfast suggestion',
    description: 'Oats and fruit. Cook until soft.',
    mealType: 'BREAKFAST',
    targetCalories: 420,
    foods: [{ foodId: 12, servings: 1, foodName: 'Oat bowl' }]
  };
  const nutritionPlan: NutritionPlanView = {
    id: 14, name: 'Weekly plan', goal: 'maintenance', planType: 'WEEKLY',
    startDate: '2026-10-25', endDate: '2026-10-31', version: 2,
    days: [{ id: 16, date: '2026-10-25', meals: [{
      id: 18, name: 'Oat breakfast', mealType: 'BREAKFAST', servingSizeGrams: 350,
      preparationInstructions: 'Cook oats.', estimatedNutrition: {
        calories: 340, proteinGrams: 12, carbohydrateGrams: 52, fatGrams: 8, fiberGrams: 6, sodiumMg: 120
      }, nutritionSource: 'MODEL_ESTIMATE',
      foods: [{ foodId: null, name: 'Oats', servings: 1 }]
    }] }]
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<CoachNutritionApiService>(
      'CoachNutritionApiService',
      ['getFoods', 'updateFood', 'deleteFood', 'getMealPlans', 'updateMealPlan',
        'getNutritionPlans', 'updateNutritionPlanMeal']
    );
    membershipApi = jasmine.createSpyObj<MembershipApiService>(
      'MembershipApiService',
      ['getAssignedMembers']
    );
    membershipApi.getAssignedMembers.and.returnValue(of([member]));
    api.getFoods.and.returnValue(of([food]));
    api.getMealPlans.and.returnValue(of([plan]));
    api.getNutritionPlans.and.returnValue(of([nutritionPlan]));
    api.updateMealPlan.and.returnValue(of(plan));
    api.updateNutritionPlanMeal.and.returnValue(of({
      ...nutritionPlan, version: 3, days: [{ ...nutritionPlan.days[0], meals: [{
        ...nutritionPlan.days[0].meals[0], name: 'Coach reviewed oats'
      }] }]
    }));

    await TestBed.configureTestingModule({
      imports: [CoachNutritionComponent],
      providers: [
        provideRouter([]),
        { provide: CoachNutritionApiService, useValue: api },
        { provide: MembershipApiService, useValue: membershipApi }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CoachNutritionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('reviews saved meal details and nutrition for an assigned member', () => {
    expect(component.selectedMemberId).toBe(member.memberProfileId);
    expect(component.mealPlans).toEqual([plan]);
    expect(fixture.nativeElement.textContent).toContain('Oats and fruit. Cook until soft.');
    expect(fixture.nativeElement.textContent).toContain('420 kcal');
    expect(component.nutritionPlans).toEqual([nutritionPlan]);
    expect(fixture.nativeElement.textContent).toContain('Weekly plan');
  });

  it('edits a persisted plan meal for the selected member using the plan version', () => {
    const day = nutritionPlan.days[0];
    const meal = day.meals[0];
    component.editNutritionPlanMeal(nutritionPlan, day.date, meal);
    component.nutritionMealForm.name = 'Coach reviewed oats';
    component.saveNutritionPlanMeal();

    expect(api.updateNutritionPlanMeal).toHaveBeenCalledWith(
      'assigned-member', nutritionPlan.id, day.date, meal.id, nutritionPlan.version,
      jasmine.objectContaining({ name: 'Coach reviewed oats' })
    );
    expect(component.nutritionPlans[0].days[0].meals[0].name).toBe('Coach reviewed oats');
    expect(component.successMessage).toContain('Original plan restrictions remain attached');
  });

  it('saves edits against the selected assigned member plan', () => {
    component.editMealPlan(plan);
    component.mealPlanForm.description = 'Oats, berries, and yogurt. Mix and serve chilled.';
    component.mealPlanForm.targetCalories = 390;
    component.saveMealPlan();

    expect(api.updateMealPlan).toHaveBeenCalledWith('assigned-member', 8, {
      name: 'Breakfast suggestion',
      description: 'Oats, berries, and yogurt. Mix and serve chilled.',
      mealType: 'BREAKFAST',
      targetCalories: 390,
      foods: [{ foodId: 12, servings: 1 }]
    });
    expect(component.successMessage).toContain('updated');
    expect(component.savingMealPlan).toBeFalse();
  });

  it('shows an error and clears pending state when a plan update fails', () => {
    api.updateMealPlan.and.returnValue(throwError(() => new Error('request failed')));
    component.editMealPlan(plan);
    component.saveMealPlan();

    expect(component.mealPlanError).toContain('Could not save this meal plan');
    expect(component.savingMealPlan).toBeFalse();
  });

  it('clears the previous member data while the next assigned member loads', () => {
    api.getFoods.and.returnValue(new Subject<NutritionFood[]>());
    api.getMealPlans.and.returnValue(new Subject<NutritionMealPlan[]>());
    api.getNutritionPlans.and.returnValue(new Subject<NutritionPlanView[]>());
    component.editFood(food);
    component.editMealPlan(plan);
    component.editNutritionPlanMeal(nutritionPlan, nutritionPlan.days[0].date,
      nutritionPlan.days[0].meals[0]);
    component.selectedMemberId = 'another-assigned-member';
    component.selectMember();

    expect(component.loadingMemberData).toBeTrue();
    expect(component.foods).toEqual([]);
    expect(component.mealPlans).toEqual([]);
    expect(component.nutritionPlans).toEqual([]);
    expect(component.editingFoodId).toBeNull();
    expect(component.editingMealPlanId).toBeNull();
    expect(component.editingNutritionMeal).toBeNull();
  });
});
