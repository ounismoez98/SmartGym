import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MembershipApiService, AssignedMember } from '../membership-api.service';
import { AuthService } from '../auth.service';
import { Exercise } from './training-api.service';
import { Programme, ProgrammeInput, TrainingWorkflowsApiService } from './training-workflows-api.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <main class="content">
      <a class="back-link" routerLink="/training">← Exercise catalogue</a>
      <p class="eyebrow">M3 · PROGRAMMES</p>
      <h1>Training programmes</h1>
      <p class="lead">Coach-created plans remain drafts until reviewed and explicitly published.</p>
      @if (error()) { <div class="notice error" role="alert">{{ error() }}</div> }
      @if (notice()) { <div class="notice" role="status">{{ notice() }}</div> }

      @if (isCoach) {
        <section class="account-card">
          <h2>{{ editing() ? 'Edit draft' : 'Create a programme draft' }}</h2>
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
            <h3>First session</h3>
            <div class="form-grid">
              <label>Session title <input formControlName="sessionTitle" maxlength="120"></label>
              <label>Exercise
                <select formControlName="exerciseId">
                  <option value="">Select an active exercise</option>
                  @for (exercise of exercises(); track exercise.id) {
                    <option [value]="exercise.id">{{ exercise.name }} · {{ exercise.primaryMuscleGroup }}</option>
                  }
                </select>
              </label>
              <label>Sets <input type="number" min="1" max="20" formControlName="sets"></label>
              <label>Repetitions <input type="number" min="1" max="100" formControlName="repetitions"></label>
              <label>Rest (seconds) <input type="number" min="0" max="3600" formControlName="restSeconds"></label>
            </div>
            <button class="button primary" type="submit" [disabled]="saving()">
              {{ saving() ? 'Saving…' : editing() ? 'Save draft changes' : 'Create draft' }}
            </button>
            @if (editing()) { <button class="button quiet" type="button" (click)="cancelEdit()">Cancel</button> }
          </form>
          @if (exercises().length === 0) { <p class="muted">Add an active exercise before creating a programme.</p> }
        </section>
      }

      <section class="programme-list">
        <div class="section-heading"><h2>{{ isCoach ? 'Assigned-member programmes' : 'Your published programmes' }}</h2>
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
                  <ul>@for (item of session.items; track item.id) {
                    <li>{{ exerciseName(item.exerciseId) }} — {{ item.sets }} ×
                      {{ item.repetitions ?? (item.durationSeconds + ' sec') }}</li>
                  }</ul>
                </details>
              }
              @if (isCoach) {
                <div class="actions">
                  @if (programme.status === 'DRAFT' || programme.status === 'CHANGES_REQUESTED') {
                    @if (programme.sessions.length === 1 && programme.sessions[0].items.length === 1) {
                      <button class="button quiet" type="button" (click)="edit(programme)">Edit draft</button>
                    }
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
    .programme-list { margin-top: 36px; }
    .programme-card { margin: 14px 0; }
    .programme-header, .section-heading, .actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .programme-header h3 { margin-bottom: 6px; }
    .programme-header p:last-child { color: var(--muted); }
    .tag { padding: 5px 10px; border-radius: 999px; color: var(--accent); background: rgb(163 230 53 / 10%); }
    details { margin-top: 12px; }
    summary { cursor: pointer; color: var(--accent); }
    .actions { flex-wrap: wrap; justify-content: flex-start; margin-top: 18px; }
    .primary { margin: 0; }
    .muted { color: var(--muted); }
    @media (max-width: 600px) { .programme-header { align-items: flex-start; } .actions .button { flex: 1 1 100%; } }
  `]
})
export class ProgrammesComponent implements OnInit {
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
  readonly form = this.fb.group({
    memberId: this.fb.control('', Validators.required),
    title: this.fb.control('', [Validators.required, Validators.maxLength(120)]),
    goal: this.fb.control('', [Validators.required, Validators.maxLength(500)]),
    experienceLevel: this.fb.control<'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'>('BEGINNER'),
    durationWeeks: this.fb.control(4, [Validators.required, Validators.min(1), Validators.max(52)]),
    sessionTitle: this.fb.control('Workout A', Validators.required),
    exerciseId: this.fb.control('', Validators.required),
    sets: this.fb.control(3, [Validators.required, Validators.min(1), Validators.max(20)]),
    repetitions: this.fb.control(8, [Validators.required, Validators.min(1), Validators.max(100)]),
    restSeconds: this.fb.control(60, [Validators.required, Validators.min(0), Validators.max(3600)])
  });

  ngOnInit(): void {
    this.load();
    this.api.getExercises().subscribe({
      next: (page) => this.exercises.set(page.content),
      error: () => this.error.set('Could not load active exercises for programme viewing.')
    });
    if (this.isCoach) {
      this.membersLoading.set(true);
      this.membership.getAssignedMembers().subscribe({
        next: (members) => { this.members.set(members); this.membersLoading.set(false); },
        error: () => { this.error.set('Could not load assigned members from Membership.'); this.membersLoading.set(false); }
      });
    }
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.getProgrammes().subscribe({
      next: (programmes) => { this.programmes.set(programmes); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(this.message(error)); this.loading.set(false); }
    });
  }

  exerciseName(id: string): string {
    return this.exercises().find((exercise) => exercise.id === id)?.name ?? 'Exercise';
  }

  edit(programme: Programme): void {
    const session = programme.sessions[0];
    const item = session?.items[0];
    if (!session || !item) return;
    this.editing.set(programme);
    this.form.patchValue({
      memberId: programme.memberId,
      title: programme.title,
      goal: programme.goal,
      experienceLevel: programme.experienceLevel,
      durationWeeks: programme.durationWeeks,
      sessionTitle: session.title,
      exerciseId: item.exerciseId,
      sets: item.sets,
      repetitions: item.repetitions ?? 8,
      restSeconds: item.restSeconds
    });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.form.reset({
      memberId: '', title: '', goal: '', experienceLevel: 'BEGINNER', durationWeeks: 4,
      sessionTitle: 'Workout A', exerciseId: '', sets: 3, repetitions: 8, restSeconds: 60
    });
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    this.error.set('');
    const values = this.form.getRawValue();
    const input: ProgrammeInput = {
      memberId: values.memberId,
      title: values.title.trim(),
      goal: values.goal.trim(),
      experienceLevel: values.experienceLevel,
      durationWeeks: Number(values.durationWeeks),
      version: null,
      sessions: [{
        title: values.sessionTitle.trim(),
        notes: null,
        items: [{
          exerciseId: values.exerciseId,
          sets: Number(values.sets),
          repetitions: Number(values.repetitions),
          durationSeconds: null,
          restSeconds: Number(values.restSeconds),
          suggestedLoadKg: null,
          notes: null
        }]
      }]
    };
    const current = this.editing();
    const request = current ? this.api.updateProgramme(current, input) : this.api.createProgramme(input);
    request.subscribe({
      next: () => {
        this.notice.set(current ? 'Programme draft updated.' : 'Draft created; it is not published.');
        this.saving.set(false);
        this.cancelEdit();
        this.load();
      },
      error: (error: unknown) => { this.error.set(this.message(error)); this.saving.set(false); }
    });
  }

  submit(programme: Programme): void { this.action(programme, this.api.submitProgramme(programme.id), 'Submitted for review.'); }
  publish(programme: Programme): void { this.action(programme, this.api.publishProgramme(programme.id), 'Programme published.'); }
  archive(programme: Programme): void { this.action(programme, this.api.archiveProgramme(programme.id), 'Programme archived.'); }
  newRevision(programme: Programme): void { this.action(programme, this.api.createRevision(programme.id), 'New revision created as a draft.'); }

  review(programme: Programme, decision: 'APPROVED' | 'CHANGES_REQUESTED'): void {
    const comment = window.prompt(decision === 'APPROVED' ? 'Optional review note:' : 'What needs to change?');
    if (comment === null) return;
    this.action(programme, this.api.reviewProgramme(programme.id, decision, comment), 'Review recorded.');
  }

  private action(programme: Programme, request: import('rxjs').Observable<Programme>, message: string): void {
    this.error.set('');
    request.subscribe({
      next: () => { this.notice.set(message); this.load(); },
      error: (error: unknown) => this.error.set(this.message(error))
    });
  }

  private message(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) return 'Programme state or revision changed. Refresh and try again.';
    if (error instanceof HttpErrorResponse && error.status === 403) return 'Membership denied this operation for the selected member.';
    if (error instanceof HttpErrorResponse && error.status === 400) return 'The programme is invalid. Check its sessions and exercise prescriptions.';
    return 'The programme request failed. Try again after checking the service.';
  }
}
