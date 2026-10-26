import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, switchMap, takeWhile, timer } from 'rxjs';
import { MembershipApiService, AssignedMember } from '../membership-api.service';
import { AuthService } from '../auth.service';
import { Exercise } from './training-api.service';
import {
  Programme,
  AiJob,
  EquipmentCategory,
  ProgrammeGenerationInput,
  ProgrammeInput,
  ProgrammeItemInput,
  ProgrammeSessionInput,
  ProgrammeStatus,
  TrainingWorkflowsApiService
} from './training-workflows-api.service';

type PrescriptionType = 'REPETITIONS' | 'DURATION';
type ItemForm = FormGroup<{
  exerciseId: FormControl<string>;
  prescriptionType: FormControl<PrescriptionType>;
  sets: FormControl<number>;
  repetitions: FormControl<number | null>;
  durationSeconds: FormControl<number | null>;
  restSeconds: FormControl<number>;
  suggestedLoadKg: FormControl<number | null>;
  notes: FormControl<string>;
}>;
type SessionForm = FormGroup<{
  title: FormControl<string>;
  notes: FormControl<string>;
  items: FormArray<ItemForm>;
}>;

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <main class="content">
      <a class="back-link" routerLink="/training">← Exercise catalogue</a>
      <p class="eyebrow">M3 · PROGRAMMES</p>
      <h1>Training programmes</h1>
      <p class="lead">Build ordered sessions and exercise prescriptions. Coach-created plans remain drafts until reviewed and explicitly published.</p>
      @if (error()) { <div class="notice error" role="alert">{{ error() }}</div> }
      @if (notice()) { <div class="notice" role="status">{{ notice() }}</div> }

      @if (isCoach) {
        <section class="account-card">
          <h2>{{ editing() ? 'Edit programme draft' : 'Create a programme draft' }}</h2>
          @if (membersLoading()) { <p>Loading your assigned members…</p> }
          <form [formGroup]="form" (ngSubmit)="save()">
            <div class="form-grid">
              <label>Assigned member
                <select formControlName="memberId">
                  <option value="">Select a member</option>
                  @for (member of members(); track member.memberProfileId) {
                    <option [value]="member.memberProfileId">{{ member.displayName }}</option>
                  }
                </select>
              </label>
              <label>Programme title <input formControlName="title" maxlength="120"></label>
              <label>Goal <input formControlName="goal" maxlength="500"></label>
              <label>Experience level
                <select formControlName="experienceLevel">
                  <option value="BEGINNER">Beginner</option>
                  <option value="INTERMEDIATE">Intermediate</option>
                  <option value="ADVANCED">Advanced</option>
                </select>
              </label>
              <label>Duration (weeks) <input type="number" min="1" max="52" formControlName="durationWeeks"></label>
            </div>

            <div formArrayName="sessions">
              @for (session of sessions.controls; track session; let sessionIndex = $index) {
                <section class="session-editor" [formGroupName]="sessionIndex">
                  <div class="editor-heading">
                    <h3>Session {{ sessionIndex + 1 }}</h3>
                    <div class="actions">
                      <button class="button quiet" type="button" [disabled]="sessionIndex === 0" (click)="moveSession(sessionIndex, -1)">Move up</button>
                      <button class="button quiet" type="button" [disabled]="sessionIndex === sessions.length - 1" (click)="moveSession(sessionIndex, 1)">Move down</button>
                      <button class="button quiet" type="button" [disabled]="sessions.length === 1" (click)="removeSession(sessionIndex)">Remove session</button>
                    </div>
                  </div>
                  <div class="form-grid">
                    <label>Session title <input formControlName="title" maxlength="120"></label>
                    <label>Session notes <input formControlName="notes" maxlength="1000"></label>
                  </div>
                  <div formArrayName="items">
                    @for (item of session.controls.items.controls; track item; let itemIndex = $index) {
                      <div class="item-editor" [formGroupName]="itemIndex">
                        <div class="editor-heading">
                          <h4>Exercise {{ itemIndex + 1 }}</h4>
                          <div class="actions">
                            <button class="button quiet" type="button" [disabled]="itemIndex === 0" (click)="moveItem(session, itemIndex, -1)">Move up</button>
                            <button class="button quiet" type="button" [disabled]="itemIndex === session.controls.items.length - 1" (click)="moveItem(session, itemIndex, 1)">Move down</button>
                            <button class="button quiet" type="button" [disabled]="session.controls.items.length === 1" (click)="removeItem(session, itemIndex)">Remove</button>
                          </div>
                        </div>
                        <div class="form-grid">
                          <label>Exercise
                            <select formControlName="exerciseId">
                              <option value="">Select an active exercise</option>
                              @for (exercise of exercises(); track exercise.id) {
                                <option [value]="exercise.id">{{ exercise.name }} · {{ exercise.primaryMuscleGroup }}</option>
                              }
                            </select>
                          </label>
                          <label>Sets <input type="number" min="1" max="20" formControlName="sets"></label>
                          <label>Prescription
                            <select formControlName="prescriptionType" (change)="setPrescriptionType(item, $event)">
                              <option value="REPETITIONS">Repetitions</option>
                              <option value="DURATION">Duration</option>
                            </select>
                          </label>
                          @if (item.controls.prescriptionType.value === 'REPETITIONS') {
                            <label>Repetitions <input type="number" min="1" max="100" formControlName="repetitions"></label>
                          } @else {
                            <label>Duration (seconds) <input type="number" min="1" max="7200" formControlName="durationSeconds"></label>
                          }
                          <label>Rest (seconds) <input type="number" min="0" max="3600" formControlName="restSeconds"></label>
                          <label>Suggested load (kg, optional) <input type="number" min="0.01" step="0.01" formControlName="suggestedLoadKg"></label>
                          <label>Exercise notes <input formControlName="notes" maxlength="500"></label>
                        </div>
                      </div>
                    }
                  </div>
                  <button class="button quiet" type="button" [disabled]="session.controls.items.length >= 20" (click)="addItem(session)">Add exercise</button>
                </section>

                <section class="account-card ai-card">
                  <p class="eyebrow">LOCAL AI · DRAFT ONLY</p>
                  <h2>Generate a programme draft</h2>
                  <p class="muted">The local model uses only your inputs and a bounded list of active exercises. Its output is unreviewed, not medically verified, and must be reviewed and explicitly published by a coach.</p>
                  <form [formGroup]="aiForm" (ngSubmit)="generateWithAi()">
                    <div class="form-grid">
                      <label>Assigned member
                        <select formControlName="memberId">
                          <option value="">Select a member</option>
                          @for (member of members(); track member.memberProfileId) {
                            <option [value]="member.memberProfileId">{{ member.displayName }}</option>
                          }
                        </select>
                      </label>
                      <label>Training goal <input formControlName="goal" maxlength="500" placeholder="Build general strength"></label>
                      <label>Experience level
                        <select formControlName="experienceLevel">
                          <option value="BEGINNER">Beginner</option>
                          <option value="INTERMEDIATE">Intermediate</option>
                          <option value="ADVANCED">Advanced</option>
                        </select>
                      </label>
                      <label>Training days per week <input type="number" min="1" max="7" formControlName="trainingDaysPerWeek"></label>
                      <label>Maximum session duration (minutes) <input type="number" min="15" max="240" formControlName="maxSessionDurationMinutes"></label>
                      <label>Exercise preferences (optional) <input formControlName="exercisePreferences" maxlength="500"></label>
                      <label>Non-medical constraints (optional) <input formControlName="nonMedicalConstraints" maxlength="500"></label>
                    </div>
                    <fieldset>
                      <legend>Available training equipment</legend>
                      <div class="equipment-options">
                        @for (category of equipmentCategories; track category) {
                          <label class="checkbox-label">
                            <input type="checkbox" [checked]="aiForm.controls.availableEquipment.value.includes(category)"
                              (change)="toggleEquipment($event, category)">
                            {{ category.replace('_', ' ') }}
                          </label>
                        }
                      </div>
                    </fieldset>
                    <button class="button primary" type="submit"
                      [disabled]="aiRequesting() || isPolling() || members().length === 0">
                      {{ aiRequesting() ? 'Submitting…' : isPolling() ? 'Generation in progress…' : 'Request AI draft' }}
                    </button>
                  </form>
                  @if (activeAiJob(); as job) {
                    <div class="job-status" role="status" aria-live="polite">
                      <strong>Generation job: {{ job.status }}</strong>
                      @if (job.status === 'QUEUED') { <p>Waiting for the local generation worker. This page will check status automatically.</p> }
                      @if (job.status === 'FAILED') { <p class="notice error">{{ job.failureReason || 'Generation failed. Review the request and retry.' }}</p> }
                      @if (job.status === 'COMPLETED' && job.programmeId) {
                        <p>Draft created. It has not been approved or published.</p>
                        <a class="button quiet" [routerLink]="['/training/programmes']">View draft in programme list</a>
                      }
                    </div>
                  }
                </section>
              }
            </div>
            <button class="button quiet" type="button" [disabled]="sessions.length >= 14" (click)="addSession()">Add session</button>
            <div class="form-actions">
              <button class="button primary" type="submit" [disabled]="saving() || exercises().length === 0">
                {{ saving() ? 'Saving…' : editing() ? 'Save draft changes' : 'Create draft' }}
              </button>
              @if (editing()) { <button class="button quiet" type="button" (click)="cancelEdit()">Cancel</button> }
            </div>
          </form>
          @if (exercises().length === 0) { <p class="muted">Add an active exercise before creating a programme.</p> }
        </section>
      }

      <section class="programme-list">
        <div class="section-heading"><h2>{{ isCoach ? 'Assigned-member programmes' : 'Your published programmes' }}</h2>
          @if (isCoach) {
            <label>Status
              <select [value]="statusFilter()" (change)="setStatusFilter($event)">
                <option value="">All statuses</option>
                <option value="DRAFT">Draft</option>
                <option value="PENDING_REVIEW">Pending review</option>
                <option value="CHANGES_REQUESTED">Changes requested</option>
                <option value="APPROVED">Approved</option>
                <option value="PUBLISHED">Published</option>
                <option value="ARCHIVED">Archived</option>
              </select>
            </label>
          }
          <button class="button quiet" type="button" (click)="load()">Refresh</button></div>
        @if (loading()) { <div class="account-card">Loading programmes…</div> }
        @else if (programmes().length === 0) { <div class="account-card muted">No programmes to show yet.</div> }
        @else {
          @for (programme of programmes(); track programme.id) {
            <article class="account-card programme-card">
              <div class="programme-header">
                <div><p class="eyebrow">REVISION {{ programme.revision }} · {{ programme.status }}</p>
                  <h3>{{ programme.title }}</h3><p>{{ programme.goal }}</p></div>
                <span class="tag">{{ programme.experienceLevel }}</span>
              </div>
              <p>{{ programme.durationWeeks }} weeks · {{ programme.sessions.length }} sessions</p>
              @for (session of programme.sessions; track session.id) {
                <details>
                  <summary>{{ session.dayOrder }}. {{ session.title }}</summary>
                  @if (session.notes) { <p>{{ session.notes }}</p> }
                  <ol>@for (item of session.items; track item.id) {
                    <li>{{ exerciseName(item.exerciseId) }} — {{ item.sets }} ×
                      {{ item.repetitions ?? (item.durationSeconds + ' sec') }}
                      @if (item.suggestedLoadKg !== null) { · {{ item.suggestedLoadKg }} kg }
                      @if (item.notes) { · {{ item.notes }} }
                    </li>
                  }</ol>
                  @if (!isCoach && programme.status === 'PUBLISHED') {
                    <a class="button primary" [routerLink]="['/training/workouts']"
                      [queryParams]="{ programmeId: programme.id, programmeSessionId: session.id }">Log this session</a>
                  }
                </details>
              }
              @if (isCoach) {
                <div class="actions">
                  @if (programme.status === 'DRAFT' || programme.status === 'CHANGES_REQUESTED') {
                    <button class="button quiet" type="button" (click)="edit(programme)">Edit draft</button>
                    <button class="button primary" type="button" (click)="submit(programme)">Submit for review</button>
                  }
                  @if (programme.status === 'PENDING_REVIEW') {
                    <button class="button quiet" type="button" (click)="review(programme, 'CHANGES_REQUESTED')">Request changes</button>
                    <button class="button primary" type="button" (click)="review(programme, 'APPROVED')">Approve revision</button>
                  }
                  @if (programme.status === 'APPROVED' && programme.approvedRevision === programme.revision) {
                    <button class="button primary" type="button" (click)="publish(programme)">Publish</button>
                  }
                  @if (programme.status === 'PUBLISHED') {
                    <button class="button quiet" type="button" (click)="newRevision(programme)">Create new revision</button>
                  }
                  @if (programme.status !== 'ARCHIVED') {
                    <button class="button quiet" type="button" (click)="archive(programme)">Archive</button>
                  }
                </div>
                @if (programme.reviewHistory.length) {
                  <details><summary>Review history</summary>
                    <ul>@for (review of programme.reviewHistory; track review.reviewedAt) {
                      <li>Revision {{ review.reviewedRevision }}: {{ review.decision }} — {{ review.comment || 'No comment' }}</li>
                    }</ul>
                  </details>
                }
              }
            </article>
          }
        }
      </section>
    </main>
  `,
  styles: [`
    .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px; margin: 18px 0; }
    label { display: grid; gap: 7px; color: var(--muted); font-size: .9rem; }
    input, select { min-height: 42px; padding: 9px 11px; border: 1px solid var(--border); border-radius: 8px; color: #eef4ee; background: #101a15; font: inherit; }
    .session-editor { margin: 22px 0; padding: 18px; border: 1px solid var(--border); border-radius: 12px; }
    .item-editor { margin: 16px 0; padding: 14px; border: 1px solid var(--border); border-radius: 9px; }
    .editor-heading, .programme-header, .section-heading, .actions, .form-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .programme-list { margin-top: 36px; }
    .programme-card { margin: 14px 0; }
    .programme-header h3 { margin-bottom: 6px; }
    .programme-header p:last-child { color: var(--muted); }
    .tag { padding: 5px 10px; border-radius: 999px; color: var(--accent); background: rgb(163 230 53 / 10%); }
    details { margin-top: 12px; }
    summary { cursor: pointer; color: var(--accent); }
    .actions { flex-wrap: wrap; justify-content: flex-start; }
    .form-actions { justify-content: flex-start; margin-top: 18px; }
    .primary { margin: 0; }
    .muted { color: var(--muted); }
    .ai-card { margin-top: 24px; }
    fieldset { margin: 16px 0; padding: 14px; border: 1px solid var(--border); border-radius: 9px; }
    legend { padding: 0 6px; color: var(--muted); }
    .equipment-options { display: flex; flex-wrap: wrap; gap: 10px 18px; }
    .checkbox-label { display: flex; align-items: center; gap: 7px; }
    .checkbox-label input { min-height: auto; }
    .job-status { margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--border); }
    @media (max-width: 600px) {
      .programme-header, .editor-heading { align-items: flex-start; flex-direction: column; }
      .actions .button { flex: 1 1 100%; }
    }
  `]
})
export class ProgrammesComponent implements OnInit, OnDestroy {
  private readonly api = inject(TrainingWorkflowsApiService);
  private readonly auth = inject(AuthService);
  private readonly membership = inject(MembershipApiService);
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly isCoach = this.auth.hasAnyRole(['COACH']);
  readonly programmes = signal<Programme[]>([]);
  readonly exercises = signal<Exercise[]>([]);
  readonly members = signal<AssignedMember[]>([]);
  readonly loading = signal(false);
  readonly membersLoading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editing = signal<Programme | null>(null);
  readonly statusFilter = signal<ProgrammeStatus | ''>('');
  readonly activeAiJob = signal<AiJob | null>(null);
  readonly aiRequesting = signal(false);
  readonly isPolling = signal(false);
  readonly equipmentCategories: EquipmentCategory[] = [
    'BODYWEIGHT', 'DUMBBELL', 'BARBELL', 'KETTLEBELL',
    'CABLE', 'MACHINE', 'RESISTANCE_BAND', 'OTHER'
  ];
  private jobPolling?: Subscription;
  readonly form = this.fb.group({
    memberId: this.fb.control('', Validators.required),
    title: this.fb.control('', [Validators.required, Validators.maxLength(120)]),
    goal: this.fb.control('', [Validators.required, Validators.maxLength(500)]),
    experienceLevel: this.fb.control<'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'>('BEGINNER'),
    durationWeeks: this.fb.control(4, [Validators.required, Validators.min(1), Validators.max(52)]),
    sessions: this.fb.array<SessionForm>([this.createSessionForm()])
  });
  readonly aiForm = this.fb.group({
    memberId: this.fb.control('', Validators.required),
    goal: this.fb.control('', [Validators.required, Validators.maxLength(500)]),
    experienceLevel: this.fb.control<'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'>('BEGINNER'),
    trainingDaysPerWeek: this.fb.control(3, [Validators.required, Validators.min(1), Validators.max(7)]),
    maxSessionDurationMinutes: this.fb.control(60, [Validators.required, Validators.min(15), Validators.max(240)]),
    availableEquipment: this.fb.control<EquipmentCategory[]>(['BODYWEIGHT']),
    exercisePreferences: this.fb.control('', Validators.maxLength(500)),
    nonMedicalConstraints: this.fb.control('', Validators.maxLength(500))
  });

  get sessions(): FormArray<SessionForm> {
    return this.form.controls.sessions;
  }

  ngOnInit(): void {
    this.load();
    this.api.getExercises().subscribe({
      next: (page) => this.exercises.set(page.content),
      error: () => this.error.set('Could not load active exercises for programme editing.')
    });
    if (this.isCoach) {
      this.membersLoading.set(true);
      this.membership.getAssignedMembers().subscribe({
        next: (members) => { this.members.set(members); this.membersLoading.set(false); },
        error: () => { this.error.set('Could not load assigned members from Membership.'); this.membersLoading.set(false); }
      });
    }
  }

  ngOnDestroy(): void {
    this.jobPolling?.unsubscribe();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.getProgrammes(this.statusFilter() || undefined).subscribe({
      next: (programmes) => { this.programmes.set(programmes); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(this.message(error)); this.loading.set(false); }
    });
  }

  setStatusFilter(event: Event): void {
    const selected = (event.target as HTMLSelectElement).value;
    const statuses: ProgrammeStatus[] = [
      'DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'ARCHIVED'
    ];
    this.statusFilter.set(statuses.find((status) => status === selected) ?? '');
    this.load();
  }

  toggleEquipment(event: Event, category: EquipmentCategory): void {
    const selected = new Set(this.aiForm.controls.availableEquipment.value);
    if ((event.target as HTMLInputElement).checked) selected.add(category);
    else selected.delete(category);
    this.aiForm.controls.availableEquipment.setValue([...selected]);
  }

  generateWithAi(): void {
    if (this.aiForm.invalid) {
      this.aiForm.markAllAsTouched();
      this.error.set('Complete the required generation fields and use the permitted day/session limits.');
      return;
    }
    const values = this.aiForm.getRawValue();
    const request: ProgrammeGenerationInput = {
      memberId: values.memberId,
      goal: values.goal.trim(),
      experienceLevel: values.experienceLevel,
      trainingDaysPerWeek: Number(values.trainingDaysPerWeek),
      maxSessionDurationMinutes: Number(values.maxSessionDurationMinutes),
      availableEquipment: values.availableEquipment,
      exercisePreferences: values.exercisePreferences.trim() || null,
      nonMedicalConstraints: values.nonMedicalConstraints.trim() || null
    };
    this.aiRequesting.set(true);
    this.error.set('');
    this.api.requestProgrammeGeneration(request).subscribe({
      next: (accepted) => {
        this.aiRequesting.set(false);
        this.activeAiJob.set({
          id: accepted.jobId,
          status: accepted.status,
          programmeId: null,
          failureReason: null,
          createdAt: accepted.createdAt,
          updatedAt: accepted.createdAt
        });
        this.notice.set('Generation queued. The result will be a draft requiring coach review.');
        this.pollAiJob(accepted.jobId);
      },
      error: (error: unknown) => {
        this.aiRequesting.set(false);
        this.error.set(this.message(error));
      }
    });
  }

  exerciseName(id: string): string {
    return this.exercises().find((exercise) => exercise.id === id)?.name ?? 'Exercise';
  }

  addSession(): void {
    if (this.sessions.length < 14) this.sessions.push(this.createSessionForm());
  }

  removeSession(index: number): void {
    if (this.sessions.length > 1) this.sessions.removeAt(index);
  }

  moveSession(index: number, offset: number): void {
    this.moveControl(this.sessions, index, offset);
  }

  addItem(session: SessionForm): void {
    if (session.controls.items.length < 20) session.controls.items.push(this.createItemForm());
  }

  removeItem(session: SessionForm, index: number): void {
    if (session.controls.items.length > 1) session.controls.items.removeAt(index);
  }

  moveItem(session: SessionForm, index: number, offset: number): void {
    this.moveControl(session.controls.items, index, offset);
  }

  setPrescriptionType(item: ItemForm, event: Event): void {
    const durationBased = (event.target as HTMLSelectElement).value === 'DURATION';
    if (!durationBased) {
      item.controls.durationSeconds.setValue(null);
      if (item.controls.repetitions.value === null) item.controls.repetitions.setValue(8);
    } else {
      item.controls.repetitions.setValue(null);
      if (item.controls.durationSeconds.value === null) item.controls.durationSeconds.setValue(30);
    }
  }

  edit(programme: Programme): void {
    this.editing.set(programme);
    this.form.patchValue({
      memberId: programme.memberId,
      title: programme.title,
      goal: programme.goal,
      experienceLevel: programme.experienceLevel,
      durationWeeks: programme.durationWeeks
    });
    this.sessions.clear();
    programme.sessions.forEach((session) => this.sessions.push(this.createSessionForm({
      title: session.title,
      notes: session.notes,
      items: session.items.map((item) => ({
        exerciseId: item.exerciseId,
        sets: item.sets,
        repetitions: item.repetitions,
        durationSeconds: item.durationSeconds,
        restSeconds: item.restSeconds,
        suggestedLoadKg: item.suggestedLoadKg,
        notes: item.notes
      }))
    })));
    if (this.sessions.length === 0) this.addSession();
    this.error.set('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.resetForm();
    this.error.set('');
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Complete all required programme and exercise fields.');
      return;
    }
    const raw = this.form.getRawValue();
    if (raw.sessions.length === 0 || raw.sessions.some((session) => session.items.length === 0)) {
      this.error.set('A programme needs at least one session, and every session needs an exercise.');
      return;
    }
    const invalidPrescription = raw.sessions.some((session) => session.items.some((item) =>
      item.prescriptionType === 'REPETITIONS'
        ? item.repetitions === null || item.durationSeconds !== null
        : item.durationSeconds === null || item.repetitions !== null));
    if (invalidPrescription) {
      this.error.set('Each exercise must have either repetitions or a duration, not both.');
      return;
    }
    const invalidLoad = raw.sessions.some((session) => session.items.some((item) =>
      item.suggestedLoadKg !== null
      && this.exercises().find((exercise) => exercise.id === item.exerciseId)?.equipmentCategory === 'BODYWEIGHT'));
    if (invalidLoad) {
      this.error.set('Bodyweight exercises cannot have a suggested external load.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const input: ProgrammeInput = {
      memberId: raw.memberId,
      title: raw.title.trim(),
      goal: raw.goal.trim(),
      experienceLevel: raw.experienceLevel,
      durationWeeks: Number(raw.durationWeeks),
      version: null,
      sessions: raw.sessions.map((session): ProgrammeSessionInput => ({
        title: session.title.trim(),
        notes: session.notes.trim() || null,
        items: session.items.map((item): ProgrammeItemInput => ({
          exerciseId: item.exerciseId,
          sets: Number(item.sets),
          repetitions: item.prescriptionType === 'REPETITIONS' ? Number(item.repetitions) : null,
          durationSeconds: item.prescriptionType === 'DURATION' ? Number(item.durationSeconds) : null,
          restSeconds: Number(item.restSeconds),
          suggestedLoadKg: item.suggestedLoadKg,
          notes: item.notes.trim() || null
        }))
      }))
    };
    const current = this.editing();
    const request = current ? this.api.updateProgramme(current, input) : this.api.createProgramme(input);
    request.subscribe({
      next: () => {
        this.notice.set(current ? 'Programme draft updated.' : 'Draft created; it is not published.');
        this.saving.set(false);
        this.editing.set(null);
        this.resetForm();
        this.load();
      },
      error: (error: unknown) => { this.error.set(this.message(error)); this.saving.set(false); }
    });
  }

  submit(programme: Programme): void { this.action(this.api.submitProgramme(programme.id), 'Submitted for review.'); }
  publish(programme: Programme): void { this.action(this.api.publishProgramme(programme.id), 'Programme published.'); }
  archive(programme: Programme): void { this.action(this.api.archiveProgramme(programme.id), 'Programme archived.'); }
  newRevision(programme: Programme): void { this.action(this.api.createRevision(programme.id), 'New revision created as a draft.'); }

  review(programme: Programme, decision: 'APPROVED' | 'CHANGES_REQUESTED'): void {
    const comment = window.prompt(decision === 'APPROVED' ? 'Optional review note:' : 'What needs to change?');
    if (comment === null) return;
    this.action(this.api.reviewProgramme(programme.id, decision, comment), 'Review recorded.');
  }

  private pollAiJob(jobId: string): void {
    this.jobPolling?.unsubscribe();
    this.isPolling.set(true);
    this.jobPolling = timer(0, 2500).pipe(
      switchMap(() => this.api.getAiJob(jobId)),
      takeWhile((job) => job.status === 'QUEUED', true)
    ).subscribe({
      next: (job) => {
        this.activeAiJob.set(job);
        if (job.status === 'COMPLETED') {
          this.notice.set('AI-generated programme draft is ready for coach review.');
          this.load();
        } else if (job.status === 'FAILED') {
          this.error.set(job.failureReason || 'Programme generation failed. Please adjust the request and try again.');
        }
      },
      error: (error: unknown) => {
        this.error.set(this.message(error));
        this.isPolling.set(false);
      },
      complete: () => this.isPolling.set(false)
    });
  }

  private createSessionForm(value?: ProgrammeSessionInput): SessionForm {
    const items = value ? value.items.map((item) => this.createItemForm(item)) : [this.createItemForm()];
    return this.fb.group({
      title: this.fb.control(value?.title ?? 'Workout A', [Validators.required, Validators.maxLength(120)]),
      notes: this.fb.control(value?.notes ?? '', Validators.maxLength(1000)),
      items: this.fb.array<ItemForm>(items)
    });
  }

  private createItemForm(value?: ProgrammeItemInput): ItemForm {
    const durationBased = value?.durationSeconds !== null && value?.durationSeconds !== undefined;
    return this.fb.group({
      exerciseId: this.fb.control(value?.exerciseId ?? '', Validators.required),
      prescriptionType: this.fb.control<PrescriptionType>(durationBased ? 'DURATION' : 'REPETITIONS'),
      sets: this.fb.control(value?.sets ?? 3, [Validators.required, Validators.min(1), Validators.max(20)]),
      repetitions: this.fb.control<number | null>(value?.repetitions ?? 8, [Validators.min(1), Validators.max(100)]),
      durationSeconds: this.fb.control<number | null>(value?.durationSeconds ?? null, [Validators.min(1), Validators.max(7200)]),
      restSeconds: this.fb.control(value?.restSeconds ?? 60, [Validators.required, Validators.min(0), Validators.max(3600)]),
      suggestedLoadKg: this.fb.control<number | null>(value?.suggestedLoadKg ?? null, [Validators.min(0.01), Validators.max(99999.99)]),
      notes: this.fb.control(value?.notes ?? '', Validators.maxLength(500))
    });
  }

  private moveControl<T extends FormGroup>(array: FormArray<T>, index: number, offset: number): void {
    const target = index + offset;
    if (target < 0 || target >= array.length) return;
    const control = array.controls[index];
    if (!control) return;
    array.controls.splice(index, 1);
    array.controls.splice(target, 0, control);
    array.updateValueAndValidity();
  }

  private resetForm(): void {
    this.form.reset({
      memberId: '',
      title: '',
      goal: '',
      experienceLevel: 'BEGINNER',
      durationWeeks: 4
    });
    this.sessions.clear();
    this.sessions.push(this.createSessionForm());
  }

  private action(request: import('rxjs').Observable<Programme>, message: string): void {
    this.error.set('');
    request.subscribe({
      next: () => { this.notice.set(message); this.load(); },
      error: (error: unknown) => this.error.set(this.message(error))
    });
  }

  private message(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) return 'Programme state or revision changed. Refresh and try again.';
    if (error instanceof HttpErrorResponse && error.status === 403) return 'Membership denied this operation for the selected member.';
    if (error instanceof HttpErrorResponse && error.status === 400) {
      const detail = error.error && typeof error.error.detail === 'string' ? error.error.detail : '';
      return detail || 'The programme request is invalid. Check its fields and exercise prescriptions.';
    }
    if (error instanceof HttpErrorResponse && error.status === 503) return 'Membership verification is temporarily unavailable. Please retry shortly.';
    return 'The programme request failed. Try again after checking the service.';
  }
}
