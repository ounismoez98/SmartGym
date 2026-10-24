import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Exercise } from './training-api.service';
import { Progress, TrainingWorkflowsApiService, WorkoutLog } from './training-workflows-api.service';

@Component({
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink],
  template: `
    <main class="content">
      <a class="back-link" routerLink="/training">← Exercise catalogue</a>
      <p class="eyebrow">M3 · WORKOUTS</p>
      <h1>Workout history</h1>
      <p class="lead">Record actual repetitions, durations and loads. Progress values are calculated from your saved logs.</p>
      @if (error()) { <div class="notice error" role="alert">{{ error() }}</div> }
      @if (notice()) { <div class="notice" role="status">{{ notice() }}</div> }

      @if (progress(); as summary) {
        <section class="metrics" aria-label="Progress summary">
          <article class="account-card"><span>Workouts</span><strong>{{ summary.completedWorkouts }}</strong></article>
          <article class="account-card"><span>Recorded reps</span><strong>{{ summary.totalRepetitions }}</strong></article>
          <article class="account-card"><span>Loaded volume (kg × reps)</span><strong>{{ summary.trainingVolumeKgRepetitions }}</strong></article>
        </section>
      }
      <section class="account-card">
        <h2>{{ editing() ? 'Edit workout log' : 'Log a workout' }}</h2>
        <p class="muted">This simple form records a standalone workout with one actual set. Timed sets and sets without a load do not create invented load metrics.</p>
        <form [formGroup]="form" (ngSubmit)="save()">
          <div class="form-grid">
            <label>Exercise
              <select formControlName="exerciseId">
                <option value="">Select exercise</option>
                @for (exercise of exercises(); track exercise.id) { <option [value]="exercise.id">{{ exercise.name }}</option> }
              </select>
            </label>
            <label>Performed at <input type="datetime-local" formControlName="performedAt"></label>
            <label>Repetitions (leave blank for timed)
              <input type="number" min="1" max="100" formControlName="repetitions">
            </label>
            <label>Duration (seconds, leave blank for reps)
              <input type="number" min="1" max="7200" formControlName="durationSeconds">
            </label>
            <label>Actual load (kg, optional) <input type="number" min="0.01" step="0.01" formControlName="loadKg"></label>
            <label>Notes <input formControlName="notes" maxlength="2000"></label>
          </div>
          <button class="button primary" type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : editing() ? 'Save log' : 'Save workout' }}</button>
          @if (editing()) { <button class="button quiet" type="button" (click)="cancelEdit()">Cancel</button> }
        </form>
      </section>

      <section class="history">
        <h2>Recent workouts</h2>
        @if (loading()) { <p class="account-card">Loading workout history…</p> }
        @else if (logs().length === 0) { <p class="account-card muted">No workouts logged yet.</p> }
        @else {
          @for (log of logs(); track log.id) {
            <article class="account-card log-card">
              <div><h3>{{ log.performedAt | date:'medium' }}</h3>
                @if (log.notes) { <p>{{ log.notes }}</p> }
              </div>
              <ul>@for (set of log.sets; track set.setNumber) {
                <li>{{ exerciseName(set.exerciseId) }}: {{ set.repetitions ?? (set.durationSeconds + ' seconds') }}
                  @if (set.loadKg !== null) { · {{ set.loadKg }} kg }</li>
              }</ul>
              @if (!log.programmeId) {
                <div class="actions">
                  @if (log.sets.length === 1) {
                    <button class="button quiet" type="button" (click)="edit(log)">Edit</button>
                  }
                  <button class="button quiet" type="button" (click)="remove(log)">Delete</button>
                </div>
              }
            </article>
          }
        }
      </section>
    </main>
  `,
  styles: [`
    .metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin-bottom: 24px; }
    .metrics article { display: grid; gap: 8px; margin: 0; color: var(--muted); }
    .metrics strong { color: var(--accent); font: 700 1.6rem 'Space Grotesk', sans-serif; }
    .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; margin: 18px 0; }
    label { display: grid; gap: 7px; color: var(--muted); font-size: .9rem; }
    input, select { min-height: 42px; padding: 9px 11px; border: 1px solid var(--border); border-radius: 8px; color: #eef4ee; background: #101a15; font: inherit; }
    .muted { color: var(--muted); }
    .history { margin-top: 34px; }
    .log-card { margin: 14px 0; }
    .log-card h3 { margin-bottom: 8px; }
    .actions { display: flex; gap: 10px; }
    .primary { margin: 0; }
    @media (max-width: 600px) { .actions .button { flex: 1; } }
  `]
})
export class WorkoutsComponent implements OnInit {
  private readonly api = inject(TrainingWorkflowsApiService);
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly exercises = signal<Exercise[]>([]);
  readonly logs = signal<WorkoutLog[]>([]);
  readonly progress = signal<Progress | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editing = signal<WorkoutLog | null>(null);
  readonly form = this.fb.group({
    exerciseId: this.fb.control('', Validators.required),
    performedAt: this.fb.control(this.localDateTime(), Validators.required),
    repetitions: this.fb.control<number | null>(10, [Validators.min(1), Validators.max(100)]),
    durationSeconds: this.fb.control<number | null>(null, [Validators.min(1), Validators.max(7200)]),
    loadKg: this.fb.control<number | null>(null, [Validators.min(0.01)]),
    notes: this.fb.control('', Validators.maxLength(2000))
  });

  ngOnInit(): void {
    this.api.getExercises().subscribe({
      next: (page) => this.exercises.set(page.content),
      error: () => this.error.set('Could not load exercises for workout logging.')
    });
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.api.getWorkoutLogs().subscribe({
      next: (page) => { this.logs.set(page.content); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(this.message(error)); this.loading.set(false); }
    });
    this.api.getProgress().subscribe({
      next: (progress) => this.progress.set(progress),
      error: (error: unknown) => this.error.set(this.message(error))
    });
  }

  exerciseName(id: string): string {
    return this.exercises().find((exercise) => exercise.id === id)?.name ?? 'Exercise';
  }

  save(): void {
    const reps = this.form.controls.repetitions.value;
    const duration = this.form.controls.durationSeconds.value;
    if (this.form.invalid || (reps === null) === (duration === null)) {
      this.form.markAllAsTouched();
      this.error.set('Enter either repetitions or a duration, but not both.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const values = this.form.getRawValue();
    const input = {
      programmeId: null,
      programmeSessionId: null,
      performedAt: new Date(values.performedAt).toISOString(),
      notes: values.notes.trim() || null,
      sets: [{
        exerciseId: values.exerciseId,
        repetitions: reps,
        durationSeconds: duration,
        loadKg: values.loadKg,
        notes: null
      }]
    };
    const current = this.editing();
    const request = current ? this.api.updateWorkoutLog(current, input) : this.api.createWorkout(input);
    request.subscribe({
      next: () => {
        this.notice.set(current ? 'Workout log updated.' : 'Workout saved.');
        this.saving.set(false);
        this.cancelEdit();
        this.refresh();
      },
      error: (error: unknown) => { this.error.set(this.message(error)); this.saving.set(false); }
    });
  }

  edit(log: WorkoutLog): void {
    const set = log.sets[0];
    if (!set) return;
    this.editing.set(log);
    this.form.patchValue({
      exerciseId: set.exerciseId,
      performedAt: this.localDateTime(new Date(log.performedAt)),
      repetitions: set.repetitions,
      durationSeconds: set.durationSeconds,
      loadKg: set.loadKg,
      notes: log.notes ?? ''
    });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.form.reset({
      exerciseId: '',
      performedAt: this.localDateTime(),
      repetitions: 10,
      durationSeconds: null,
      loadKg: null,
      notes: ''
    });
  }

  remove(log: WorkoutLog): void {
    if (!window.confirm('Delete this workout log?')) return;
    this.api.deleteWorkoutLog(log.id).subscribe({
      next: () => { this.notice.set('Workout log deleted.'); this.refresh(); },
      error: (error: unknown) => this.error.set(this.message(error))
    });
  }

  private localDateTime(value = new Date()): string {
    const local = new Date(value.getTime() - value.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }

  private message(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) return 'This workout changed elsewhere. Reload and try again.';
    if (error instanceof HttpErrorResponse && error.status === 403) return 'The server denied access to this workout resource.';
    if (error instanceof HttpErrorResponse && error.status === 400) return 'The workout data is invalid. Check your entries.';
    return 'The workout request failed. Try again after checking the service.';
  }
}
