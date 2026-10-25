import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Exercise } from './training-api.service';
import {
  Progress,
  Programme,
  ProgrammeSession,
  TrainingWorkflowsApiService,
  WorkoutLog,
  WorkoutSetInput
} from './training-workflows-api.service';

type WorkoutSetForm = FormGroup<{
  exerciseId: FormControl<string>;
  repetitions: FormControl<number | null>;
  durationSeconds: FormControl<number | null>;
  loadKg: FormControl<number | null>;
  notes: FormControl<string>;
}>;

@Component({
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink],
  template: `
    <main class="content">
      <a class="back-link" routerLink="/training">← Exercise catalogue</a>
      <p class="eyebrow">M3 · WORKOUTS</p>
      <h1>Workout history</h1>
      <p class="lead">Record actual sets, repetitions, durations and loads. Progress summaries are calculated from your saved logs.</p>
      @if (error()) { <div class="notice error" role="alert">{{ error() }}</div> }
      @if (notice()) { <div class="notice" role="status">{{ notice() }}</div> }

      @if (progress(); as summary) {
        <section class="metrics" aria-label="Progress summary">
          <article class="account-card"><span>Completed workouts</span><strong>{{ summary.completedWorkouts }}</strong></article>
          <article class="account-card"><span>Repetitions recorded</span><strong>{{ summary.totalRepetitions }}</strong></article>
          <article class="account-card"><span>Loaded volume (kg × reps)</span><strong>{{ summary.trainingVolumeKgRepetitions }}</strong></article>
        </section>
        <section class="account-card progress-card">
          <h2>Recent weekly activity</h2>
          <p class="muted">Workouts grouped by ISO calendar week (UTC).</p>
          @if (weeklyProgress().length === 0) {
            <p class="muted">Your weekly activity will appear after you log a workout.</p>
          } @else {
            <div class="week-chart" role="img" aria-label="Completed workouts by ISO week">
              @for (week of weeklyProgress(); track week.week) {
                <div class="week-column" [attr.aria-label]="week.week + ': ' + week.count + ' workouts'">
                  <span class="week-count">{{ week.count }}</span>
                  <span class="week-bar" [style.height.%]="week.height"></span>
                  <span class="week-label">{{ week.week }}</span>
                </div>
              }
            </div>
          }
          @if (maximumLoads().length > 0) {
            <h3>Maximum recorded external load</h3>
            <ul class="load-list">@for (entry of maximumLoads(); track entry.exerciseId) {
              <li>{{ exerciseName(entry.exerciseId) }}: {{ entry.load }} kg</li>
            }</ul>
          }
          <p class="metric-note">Loaded volume includes only repetition-based sets with an explicitly recorded load. Timed sets and bodyweight-only sets are excluded.</p>
        </section>
      }

      <section class="account-card">
        <h2>{{ editing() ? 'Edit workout log' : 'Log a workout' }}</h2>
        <p class="muted">Choose a published programme session or record a standalone workout. Add one row for each actual set.</p>
        <form [formGroup]="form" (ngSubmit)="save()">
          @if (!editing()) {
            <div class="form-grid">
              <label>Workout source
                <select formControlName="programmeId" (change)="programmeChanged()">
                  <option value="">Standalone workout</option>
                  @for (programme of programmes(); track programme.id) {
                    <option [value]="programme.id">{{ programme.title }} · revision {{ programme.revision }}</option>
                  }
                </select>
              </label>
              @if (form.controls.programmeId.value) {
                <label>Programme session
                  <select formControlName="programmeSessionId" (change)="programmeSessionChanged()">
                    <option value="">Select a session</option>
                    @for (session of selectedSessions(); track session.id) {
                      <option [value]="session.id">{{ session.dayOrder }}. {{ session.title }}</option>
                    }
                  </select>
                </label>
              }
            </div>
          } @else if (editing()?.programmeId) {
            <p class="muted">This log remains linked to its original published programme session.</p>
          }
          <div class="form-grid">
            <label>Performed at <input type="datetime-local" formControlName="performedAt"></label>
            <label>Workout notes <input formControlName="notes" maxlength="2000"></label>
          </div>
          <div formArrayName="sets">
            @for (set of sets.controls; track set; let setIndex = $index) {
              <section class="set-editor" [formGroupName]="setIndex">
                <div class="set-heading">
                  <h3>Set {{ setIndex + 1 }}</h3>
                  <button class="button quiet" type="button" [disabled]="sets.length === 1" (click)="removeSet(setIndex)">Remove set</button>
                </div>
                <div class="form-grid">
                  <label>Exercise
                    <select formControlName="exerciseId">
                      <option value="">Select exercise</option>
                      @for (exercise of exerciseOptions(set); track exercise.id) {
                        <option [value]="exercise.id">{{ exercise.name }}</option>
                      }
                    </select>
                  </label>
                  <label>Repetitions (leave blank for timed)
                    <input type="number" min="1" max="100" formControlName="repetitions">
                  </label>
                  <label>Duration in seconds (leave blank for reps)
                    <input type="number" min="1" max="7200" formControlName="durationSeconds">
                  </label>
                  <label>Actual external load (kg, optional)
                    <input type="number" min="0.01" step="0.01" formControlName="loadKg">
                  </label>
                  <label>Set notes <input formControlName="notes" maxlength="500"></label>
                </div>
              </section>
            }
          </div>
          <button class="button quiet" type="button" [disabled]="sets.length >= 100" (click)="addSet()">Add set</button>
          <div class="form-actions">
            <button class="button primary" type="submit" [disabled]="saving()">
              {{ saving() ? 'Saving…' : editing() ? 'Save log' : 'Save workout' }}
            </button>
            @if (editing()) { <button class="button quiet" type="button" (click)="cancelEdit()">Cancel</button> }
          </div>
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
                @if (log.programmeId) { <p>{{ programmeName(log.programmeId) }} · {{ sessionName(log) }}</p> }
                @if (log.notes) { <p>{{ log.notes }}</p> }
              </div>
              <ol>@for (set of log.sets; track set.setNumber) {
                <li>{{ exerciseName(set.exerciseId) }}: {{ set.repetitions ?? (set.durationSeconds + ' seconds') }}
                  @if (set.loadKg !== null) { · {{ set.loadKg }} kg }
                  @if (set.notes) { · {{ set.notes }} }
                </li>
              }</ol>
              <div class="actions">
                <button class="button quiet" type="button" (click)="edit(log)">Edit</button>
                <button class="button quiet" type="button" (click)="remove(log)">Delete</button>
              </div>
            </article>
          }
        }
        @if (pageCount() > 1) {
          <nav class="pagination" aria-label="Workout history pages">
            <button class="button quiet" type="button" [disabled]="page() === 0" (click)="changePage(page() - 1)">Previous</button>
            <span>Page {{ page() + 1 }} of {{ pageCount() }}</span>
            <button class="button quiet" type="button" [disabled]="page() + 1 >= pageCount()" (click)="changePage(page() + 1)">Next</button>
          </nav>
        }
      </section>
    </main>
  `,
  styles: [`
    .metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin-bottom: 24px; }
    .metrics article { display: grid; gap: 8px; margin: 0; color: var(--muted); }
    .metrics strong { color: var(--accent); font: 700 1.6rem 'Space Grotesk', sans-serif; }
    .progress-card { margin-bottom: 24px; }
    .week-chart { display: flex; align-items: end; gap: 12px; min-height: 160px; overflow-x: auto; padding: 16px 4px 0; }
    .week-column { display: grid; justify-items: center; align-content: end; gap: 6px; min-width: 54px; height: 140px; }
    .week-count, .week-label { color: var(--muted); font-size: .78rem; }
    .week-bar { display: block; width: 28px; min-height: 4px; border-radius: 6px 6px 0 0; background: var(--accent); }
    .load-list { display: flex; flex-wrap: wrap; gap: 12px 24px; padding-left: 20px; }
    .metric-note, .muted { color: var(--muted); }
    .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; margin: 18px 0; }
    label { display: grid; gap: 7px; color: var(--muted); font-size: .9rem; }
    input, select { min-height: 42px; padding: 9px 11px; border: 1px solid var(--border); border-radius: 8px; color: #eef4ee; background: #101a15; font: inherit; }
    .history { margin-top: 34px; }
    .log-card, .set-editor { margin: 14px 0; }
    .set-editor { padding: 16px; border: 1px solid var(--border); border-radius: 10px; }
    .set-heading, .actions, .form-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
    .form-actions { justify-content: flex-start; margin-top: 18px; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 16px; margin: 22px 0; }
    .primary { margin: 0; }
    @media (max-width: 600px) { .actions .button { flex: 1; } }
  `]
})
export class WorkoutsComponent implements OnInit {
  private readonly api = inject(TrainingWorkflowsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly exercises = signal<Exercise[]>([]);
  readonly programmes = signal<Programme[]>([]);
  readonly logs = signal<WorkoutLog[]>([]);
  readonly progress = signal<Progress | null>(null);
  readonly page = signal(0);
  readonly pageCount = signal(0);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editing = signal<WorkoutLog | null>(null);
  readonly form = this.fb.group({
    programmeId: this.fb.control(''),
    programmeSessionId: this.fb.control(''),
    performedAt: this.fb.control(this.localDateTime(), Validators.required),
    notes: this.fb.control('', Validators.maxLength(2000)),
    sets: this.fb.array<WorkoutSetForm>([this.createSetForm()])
  });

  get sets(): FormArray<WorkoutSetForm> {
    return this.form.controls.sets;
  }

  ngOnInit(): void {
    this.api.getExercises().subscribe({
      next: (page) => this.exercises.set(page.content),
      error: () => this.error.set('Could not load exercises for workout logging.')
    });
    const requestedProgrammeId = this.route.snapshot.queryParamMap.get('programmeId');
    const requestedSessionId = this.route.snapshot.queryParamMap.get('programmeSessionId');
    this.api.getProgrammes().subscribe({
      next: (programmes) => {
        this.programmes.set(programmes);
        if (requestedProgrammeId && programmes.some((programme) => programme.id === requestedProgrammeId)) {
          this.form.patchValue({
            programmeId: requestedProgrammeId,
            programmeSessionId: requestedSessionId ?? ''
          });
          if (requestedSessionId) this.programmeSessionChanged();
        }
      },
      error: (error: unknown) => this.error.set(this.message(error))
    });
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.api.getWorkoutLogs(this.page()).subscribe({
      next: (result) => {
        if (result.content.length === 0 && this.page() > 0 && this.page() >= result.totalPages) {
          this.page.set(Math.max(0, result.totalPages - 1));
          this.refresh();
          return;
        }
        this.logs.set(result.content);
        this.pageCount.set(result.totalPages);
        this.loading.set(false);
      },
      error: (error: unknown) => { this.error.set(this.message(error)); this.loading.set(false); }
    });
    this.api.getProgress().subscribe({
      next: (progress) => this.progress.set(progress),
      error: (error: unknown) => this.error.set(this.message(error))
    });
  }

  selectedSessions(): ProgrammeSession[] {
    const selected = this.form.controls.programmeId.value;
    return this.programmes().find((programme) => programme.id === selected)?.sessions ?? [];
  }

  programmeChanged(): void {
    this.form.controls.programmeSessionId.setValue('');
    this.resetSets();
  }

  programmeSessionChanged(): void {
    const selectedId = this.form.controls.programmeSessionId.value;
    const session = this.selectedSessions().find((entry) => entry.id === selectedId);
    this.resetSets();
    if (!session) return;
    this.sets.clear();
    let plannedSets = 0;
    for (const item of session.items) {
      for (let index = 0; index < item.sets && plannedSets < 100; index++, plannedSets++) {
        this.sets.push(this.createSetForm({
          exerciseId: item.exerciseId,
          repetitions: item.repetitions,
          durationSeconds: item.durationSeconds,
          loadKg: null,
          notes: null
        }));
      }
    }
    if (plannedSets === 0) this.sets.push(this.createSetForm());
    if (session.items.reduce((total, item) => total + item.sets, 0) > 100) {
      this.error.set('This programme session exceeds the 100-set log limit; only the first 100 set rows were prepared.');
    }
  }

  changePage(page: number): void {
    if (page < 0 || page >= this.pageCount()) return;
    this.page.set(page);
    this.refresh();
  }

  addSet(): void {
    if (this.sets.length < 100) this.sets.push(this.createSetForm());
  }

  removeSet(index: number): void {
    if (this.sets.length > 1) this.sets.removeAt(index);
  }

  exerciseName(id: string): string {
    return this.exercises().find((exercise) => exercise.id === id)?.name ?? 'Exercise';
  }

  exerciseOptions(set: WorkoutSetForm): Array<{ id: string; name: string }> {
    const selectedSession = this.selectedSessions().find(
      (session) => session.id === this.form.controls.programmeSessionId.value
    );
    const allowedIds = selectedSession
      ? new Set(selectedSession.items.map((item) => item.exerciseId))
      : null;
    const options = this.exercises()
      .filter((exercise) => !allowedIds || allowedIds.has(exercise.id))
      .map((exercise) => ({ id: exercise.id, name: exercise.name }));
    const currentId = set.controls.exerciseId.value;
    if (currentId && !options.some((exercise) => exercise.id === currentId)) {
      options.push({ id: currentId, name: 'Historical exercise' });
    }
    return options;
  }

  programmeName(id: string): string {
    return this.programmes().find((programme) => programme.id === id)?.title ?? 'Published programme';
  }

  sessionName(log: WorkoutLog): string {
    if (!log.programmeId || !log.programmeSessionId) return 'Programme workout';
    const programme = this.programmes().find((entry) => entry.id === log.programmeId);
    return programme?.sessions.find((session) => session.id === log.programmeSessionId)?.title ?? 'Programme session';
  }

  maximumLoads(): Array<{ exerciseId: string; load: number }> {
    const values = this.progress()?.maximumRecordedLoadKgByExercise ?? {};
    return Object.entries(values)
      .map(([exerciseId, load]) => ({ exerciseId, load }))
      .sort((left, right) => this.exerciseName(left.exerciseId).localeCompare(this.exerciseName(right.exerciseId)));
  }

  weeklyProgress(): Array<{ week: string; count: number; height: number }> {
    const entries = Object.entries(this.progress()?.completedWorkoutsByIsoWeek ?? {}).slice(0, 8).reverse();
    const maximum = Math.max(1, ...entries.map(([, count]) => count));
    return entries.map(([week, count]) => ({ week, count, height: Math.max(5, (count / maximum) * 100) }));
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Choose an exercise and complete the required workout fields.');
      return;
    }
    const values = this.form.getRawValue();
    if (values.sets.some((set) => (set.repetitions === null) === (set.durationSeconds === null))) {
      this.error.set('Each set must record either repetitions or a duration, but not both.');
      return;
    }
    if (values.programmeId && !values.programmeSessionId && !this.editing()) {
      this.error.set('Select the programme session you completed.');
      return;
    }
    if (values.sets.some((set) => {
      const exercise = this.exercises().find((entry) => entry.id === set.exerciseId);
      return set.loadKg !== null && (set.durationSeconds !== null || exercise?.equipmentCategory === 'BODYWEIGHT');
    })) {
      this.error.set('Loads are not recorded for timed or bodyweight exercise sets.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const current = this.editing();
    const input = {
      programmeId: current?.programmeId ?? (values.programmeId || null),
      programmeSessionId: current?.programmeSessionId ?? (values.programmeSessionId || null),
      performedAt: new Date(values.performedAt).toISOString(),
      notes: values.notes.trim() || null,
      sets: values.sets.map((set): WorkoutSetInput => ({
        exerciseId: set.exerciseId,
        repetitions: set.repetitions,
        durationSeconds: set.durationSeconds,
        loadKg: set.loadKg,
        notes: set.notes.trim() || null
      }))
    };
    const request = current ? this.api.updateWorkoutLog(current, input) : this.api.createWorkout(input);
    request.subscribe({
      next: () => {
        this.notice.set(current ? 'Workout log updated.' : 'Workout saved.');
        this.saving.set(false);
        this.editing.set(null);
        this.resetForm();
        this.refresh();
      },
      error: (error: unknown) => { this.error.set(this.message(error)); this.saving.set(false); }
    });
  }

  edit(log: WorkoutLog): void {
    this.editing.set(log);
    this.form.patchValue({
      programmeId: log.programmeId ?? '',
      programmeSessionId: log.programmeSessionId ?? '',
      performedAt: this.localDateTime(new Date(log.performedAt)),
      notes: log.notes ?? ''
    });
    this.sets.clear();
    log.sets.forEach((set) => this.sets.push(this.createSetForm(set)));
    if (this.sets.length === 0) this.addSet();
    this.error.set('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.resetForm();
    this.error.set('');
  }

  remove(log: WorkoutLog): void {
    if (!window.confirm('Delete this workout log?')) return;
    this.api.deleteWorkoutLog(log.id).subscribe({
      next: () => { this.notice.set('Workout log deleted.'); this.refresh(); },
      error: (error: unknown) => this.error.set(this.message(error))
    });
  }

  private createSetForm(value?: WorkoutSetInput): WorkoutSetForm {
    return this.fb.group({
      exerciseId: this.fb.control(value?.exerciseId ?? '', Validators.required),
      repetitions: this.fb.control<number | null>(value?.repetitions ?? 10, [Validators.min(1), Validators.max(100)]),
      durationSeconds: this.fb.control<number | null>(value?.durationSeconds ?? null, [Validators.min(1), Validators.max(7200)]),
      loadKg: this.fb.control<number | null>(value?.loadKg ?? null, [Validators.min(0.01), Validators.max(99999.99)]),
      notes: this.fb.control(value?.notes ?? '', Validators.maxLength(500))
    });
  }

  private resetForm(): void {
    this.form.reset({
      programmeId: '',
      programmeSessionId: '',
      performedAt: this.localDateTime(),
      notes: ''
    });
    this.resetSets();
  }

  private resetSets(): void {
    this.sets.clear();
    this.sets.push(this.createSetForm());
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
