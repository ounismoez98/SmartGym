import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth.service';
import {
  EquipmentCategory,
  Exercise,
  ExerciseDifficulty,
  ExerciseFilters,
  MuscleGroup,
  TrainingApiService
} from './training-api.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <main class="content training-page">
      <a class="back-link" routerLink="/dashboard">← Dashboard</a>
      <p class="eyebrow">M3 · TRAINING</p>
      <h1>Exercise catalogue</h1>
      <p class="lead">Browse training exercises. Coaches and administrators can maintain the shared catalogue.</p>
      <nav class="training-links" aria-label="Training tools">
        <a class="button quiet" routerLink="/training/programmes">Programmes</a>
        @if (auth.hasAnyRole(['MEMBER'])) {
          <a class="button quiet" routerLink="/training/workouts">Workout history</a>
        }
      </nav>

      @if (error()) {
        <div class="notice error" role="alert">{{ error() }}</div>
      }
      @if (success()) {
        <div class="notice" role="status">{{ success() }}</div>
      }

      <section class="account-card" aria-labelledby="filter-heading">
        <div class="section-heading">
          <h2 id="filter-heading">Find an exercise</h2>
          <button class="button quiet" type="button" (click)="clearFilters()">Clear filters</button>
        </div>
        <form class="filters" (submit)="$event.preventDefault(); search()">
          <label>Search name
            <input type="search" [value]="filters().name" (input)="changeFilter('name', $event)">
          </label>
          <label>Muscle group
            <select [value]="filters().muscleGroup" (change)="changeFilter('muscleGroup', $event)">
              <option value="">All groups</option>
              @for (value of muscleGroups; track value) { <option [value]="value">{{ humanize(value) }}</option> }
            </select>
          </label>
          <label>Difficulty
            <select [value]="filters().difficulty" (change)="changeFilter('difficulty', $event)">
              <option value="">All levels</option>
              @for (value of difficulties; track value) { <option [value]="value">{{ humanize(value) }}</option> }
            </select>
          </label>
          <label>Equipment
            <select [value]="filters().equipmentCategory" (change)="changeFilter('equipmentCategory', $event)">
              <option value="">All equipment</option>
              @for (value of equipmentCategories; track value) { <option [value]="value">{{ humanize(value) }}</option> }
            </select>
          </label>
          <button class="button primary" type="submit">Search</button>
        </form>
      </section>

      @if (canManage) {
        <section class="account-card editor" aria-labelledby="editor-heading">
          <h2 id="editor-heading">{{ editing() ? 'Edit exercise' : 'Add an exercise' }}</h2>
          <form [formGroup]="form" (ngSubmit)="save()">
            <div class="form-grid">
              <label>Name
                <input formControlName="name" maxlength="120">
                @if (form.controls.name.touched && form.controls.name.invalid) {
                  <small class="field-error">Enter a name up to 120 characters.</small>
                }
              </label>
              <label>Description
                <input formControlName="description" maxlength="1200">
              </label>
              <label>Muscle group
                <select formControlName="primaryMuscleGroup">
                  @for (value of muscleGroups; track value) { <option [value]="value">{{ humanize(value) }}</option> }
                </select>
              </label>
              <label>Difficulty
                <select formControlName="difficulty">
                  @for (value of difficulties; track value) { <option [value]="value">{{ humanize(value) }}</option> }
                </select>
              </label>
              <label>Equipment
                <select formControlName="equipmentCategory">
                  @for (value of equipmentCategories; track value) { <option [value]="value">{{ humanize(value) }}</option> }
                </select>
              </label>
            </div>
            <label class="instructions">Instructions
              <textarea formControlName="instructions" maxlength="3000" rows="4"></textarea>
              @if (form.controls.instructions.touched && form.controls.instructions.invalid) {
                <small class="field-error">Instructions are required (maximum 3000 characters).</small>
              }
            </label>
            <div class="form-actions">
              <button class="button primary" type="submit" [disabled]="saving()">
                {{ saving() ? 'Saving…' : editing() ? 'Save changes' : 'Create exercise' }}
              </button>
              @if (editing()) {
                <button class="button quiet" type="button" (click)="resetEditor()">Cancel edit</button>
              }
            </div>
          </form>
        </section>
      }

      <section class="catalogue" aria-labelledby="catalogue-heading">
        <div class="section-heading">
          <h2 id="catalogue-heading">Exercises</h2>
          <span class="muted">{{ totalElements() }} result{{ totalElements() === 1 ? '' : 's' }}</span>
        </div>
        @if (loading()) {
          <p class="account-card" role="status">Loading exercises…</p>
        } @else if (exercises().length === 0) {
          <p class="account-card empty">No active exercises match these filters.</p>
        } @else {
          <div class="exercise-grid">
            @for (exercise of exercises(); track exercise.id) {
              <article class="account-card exercise-card">
                <div class="exercise-heading">
                  <h3>{{ exercise.name }}</h3>
                  <span class="tag">{{ humanize(exercise.difficulty) }}</span>
                </div>
                @if (exercise.description) { <p>{{ exercise.description }}</p> }
                <dl>
                  <div><dt>Muscle</dt><dd>{{ humanize(exercise.primaryMuscleGroup) }}</dd></div>
                  <div><dt>Equipment</dt><dd>{{ humanize(exercise.equipmentCategory) }}</dd></div>
                </dl>
                <details>
                  <summary>Instructions</summary>
                  <p>{{ exercise.instructions }}</p>
                </details>
                @if (canManage) {
                  <div class="form-actions">
                    <button class="button quiet" type="button" (click)="edit(exercise)">Edit</button>
                    <button class="button quiet" type="button" (click)="archive(exercise)" [disabled]="saving()">Archive</button>
                  </div>
                }
              </article>
            }
          </div>
        }
        <nav class="pagination" aria-label="Exercise pages">
          <button class="button quiet" type="button" (click)="changePage(-1)" [disabled]="loading() || filters().page === 0">Previous</button>
          <span class="muted">Page {{ filters().page + 1 }} of {{ totalPages() || 1 }}</span>
          <button class="button quiet" type="button" (click)="changePage(1)" [disabled]="loading() || filters().page + 1 >= totalPages()">Next</button>
        </nav>
      </section>
    </main>
  `,
  styles: [`
    .training-page { max-width: 1160px; }
    .training-links { display: flex; gap: 10px; margin: 22px 0; }
    .account-card { margin-top: 18px; }
    .filters, .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 14px; align-items: end; }
    label { display: grid; gap: 7px; color: var(--muted); font-size: .88rem; font-weight: 600; }
    input, select, textarea { width: 100%; min-height: 44px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; color: #eef4ee; background: #101a15; font: inherit; }
    textarea { resize: vertical; }
    .primary { margin-top: 0; }
    .editor { margin-top: 22px; }
    .instructions { margin-top: 14px; }
    .form-actions, .pagination, .exercise-heading { display: flex; align-items: center; gap: 12px; }
    .form-actions { margin-top: 16px; }
    .section-heading { margin-bottom: 12px; }
    .section-heading h2, .exercise-heading h3 { margin: 0; }
    .catalogue { margin-top: 38px; }
    .exercise-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(270px, 1fr)); gap: 14px; }
    .exercise-card { margin: 0; }
    .exercise-heading { justify-content: space-between; }
    .exercise-card > p { color: var(--muted); }
    .tag { padding: 4px 9px; border-radius: 999px; color: var(--accent); background: rgb(163 230 53 / 10%); font-size: .76rem; }
    dl { display: flex; flex-wrap: wrap; gap: 20px; }
    dl div { display: grid; gap: 3px; }
    dt, .muted { color: var(--muted); font-size: .84rem; }
    dd { margin: 0; }
    details { margin-top: 12px; }
    summary { cursor: pointer; color: var(--accent); }
    .pagination { justify-content: center; margin-top: 24px; }
    .empty { color: var(--muted); }
    .field-error { color: #ffc4b7; }
    button:disabled { opacity: .55; cursor: not-allowed; }
    @media (max-width: 600px) { .filters .button { width: 100%; } .pagination { flex-wrap: wrap; } }
  `]
})
export class TrainingComponent implements OnInit {
  private readonly api = inject(TrainingApiService);
  readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly muscleGroups: MuscleGroup[] = [
    'CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'CORE',
    'QUADRICEPS', 'HAMSTRINGS', 'GLUTES', 'CALVES', 'FULL_BODY', 'OTHER'
  ];
  readonly difficulties: ExerciseDifficulty[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
  readonly equipmentCategories: EquipmentCategory[] = [
    'BODYWEIGHT', 'DUMBBELL', 'BARBELL', 'KETTLEBELL', 'CABLE', 'MACHINE', 'RESISTANCE_BAND', 'OTHER'
  ];
  readonly canManage = this.auth.hasAnyRole(['COACH', 'ADMIN']);
  readonly exercises = signal<Exercise[]>([]);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly editing = signal<Exercise | null>(null);
  readonly filters = signal<ExerciseFilters>({
    name: '',
    muscleGroup: '',
    difficulty: '',
    equipmentCategory: '',
    page: 0
  });
  readonly form = this.fb.group({
    name: this.fb.control('', [Validators.required, Validators.maxLength(120)]),
    description: this.fb.control('', [Validators.maxLength(1200)]),
    instructions: this.fb.control('', [Validators.required, Validators.maxLength(3000)]),
    primaryMuscleGroup: this.fb.control<MuscleGroup>('CHEST', Validators.required),
    difficulty: this.fb.control<ExerciseDifficulty>('BEGINNER', Validators.required),
    equipmentCategory: this.fb.control<EquipmentCategory>('BODYWEIGHT', Validators.required)
  });

  ngOnInit(): void {
    this.loadExercises();
  }

  search(): void {
    this.filters.update((current) => ({ ...current, page: 0 }));
    this.loadExercises();
  }

  clearFilters(): void {
    this.filters.set({ name: '', muscleGroup: '', difficulty: '', equipmentCategory: '', page: 0 });
    this.loadExercises();
  }

  changeFilter(field: keyof Omit<ExerciseFilters, 'page'>, event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLSelectElement).value;
    this.filters.update((current) => ({ ...current, [field]: value }));
  }

  changePage(offset: number): void {
    this.filters.update((current) => ({ ...current, page: current.page + offset }));
    this.loadExercises();
  }

  humanize(value: string): string {
    return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  edit(exercise: Exercise): void {
    this.error.set('');
    this.success.set('');
    this.editing.set(exercise);
    this.form.setValue({
      name: exercise.name,
      description: exercise.description ?? '',
      instructions: exercise.instructions,
      primaryMuscleGroup: exercise.primaryMuscleGroup,
      difficulty: exercise.difficulty,
      equipmentCategory: exercise.equipmentCategory
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  resetEditor(): void {
    this.editing.set(null);
    this.form.reset({
      name: '',
      description: '',
      instructions: '',
      primaryMuscleGroup: 'CHEST',
      difficulty: 'BEGINNER',
      equipmentCategory: 'BODYWEIGHT'
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.success.set('');
    const exercise = this.editing();
    const input = {
      ...this.form.getRawValue(),
      description: this.form.controls.description.value.trim(),
      name: this.form.controls.name.value.trim(),
      instructions: this.form.controls.instructions.value.trim()
    };
    const request = exercise
      ? this.api.updateExercise(exercise.id, input, exercise.version)
      : this.api.createExercise(input);
    request.subscribe({
      next: () => {
        this.success.set(exercise ? 'Exercise updated.' : 'Exercise added to the catalogue.');
        this.resetEditor();
        this.saving.set(false);
        this.loadExercises();
      },
      error: (error: unknown) => {
        this.error.set(this.errorMessage(error, 'The exercise could not be saved.'));
        this.saving.set(false);
      }
    });
  }

  archive(exercise: Exercise): void {
    if (!window.confirm(`Archive "${exercise.name}"? It will remain available in historical records.`)) return;
    this.saving.set(true);
    this.error.set('');
    this.success.set('');
    this.api.archiveExercise(exercise.id).subscribe({
      next: () => {
        this.success.set('Exercise archived. Existing history is preserved.');
        this.saving.set(false);
        this.loadExercises();
      },
      error: (error: unknown) => {
        this.error.set(this.errorMessage(error, 'The exercise could not be archived.'));
        this.saving.set(false);
      }
    });
  }

  private loadExercises(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.getExercises(this.filters()).subscribe({
      next: (page) => {
        this.exercises.set(page.content);
        this.totalElements.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(this.errorMessage(error, 'The exercise catalogue could not be loaded.'));
        this.loading.set(false);
      }
    });
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      return 'This exercise was changed elsewhere. Reload the catalogue and try again.';
    }
    if (error instanceof HttpErrorResponse && error.status === 400) {
      return 'Some exercise details are invalid. Check the form and try again.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) return 'This exercise no longer exists.';
    return fallback;
  }
}
