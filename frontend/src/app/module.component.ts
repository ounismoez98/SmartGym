import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';
import {
  Course,
  CourseSession,
  CoursesApiService,
  Reservation,
  TimetableRecommendation
} from './courses-api.service';
import { AuthService } from './auth.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <main class="content">
      <a class="back-link" routerLink="/dashboard">← Dashboard</a>
      <p class="eyebrow">{{ moduleCode }} MODULE</p>
      <h1>{{ title }}</h1>
      <p class="lead">{{ description }}</p>

      <section class="account-card" *ngIf="!isCoursesModule">
        <h2>Secure module workspace</h2>
        <p>Your authenticated session and role are checked before this area opens. Business features for this module are the next delivery phase.</p>
      </section>

      <ng-container *ngIf="isCoursesModule">
        <p *ngIf="notice" class="notice" role="status">{{ notice }}</p>
        <p *ngIf="error" class="error-box" role="alert">{{ error }}</p>
        <p *ngIf="loading" class="muted">Loading courses and module data...</p>

        <section class="account-card summary-grid" *ngIf="isAdmin">
          <article><strong>{{ courses.length }}</strong><span>Course offerings</span></article>
          <article><strong>{{ scheduledSessionCount }}</strong><span>Scheduled sessions</span></article>
          <article><strong>{{ confirmedReservationCount }}</strong><span>Confirmed reservations</span></article>
        </section>

        <section class="account-card">
          <h2>Courses</h2>
          <p *ngIf="courses.length === 0" class="muted">No courses yet. An admin can create the first course below.</p>
          <div class="item-list">
            <article class="item" *ngFor="let course of courses">
              <div>
                <img class="course-picture" [src]="courseImage(course.name).src" [alt]="courseImage(course.name).alt"
                  loading="lazy" (error)="onCourseImageError($event)">
                <strong>{{ course.name }}</strong>
                <p>{{ course.difficultyLevel }} · {{ course.durationMinutes }} min · {{ course.active ? 'Active' : 'Inactive' }}</p>
                <small>{{ course.description || 'No description' }}</small>
                <small><strong>Required equipment:</strong> {{ course.requiredEquipment || courseEquipment(course.name) }}</small>
                <small *ngIf="courseImage(course.name).creditUrl">Photo by <a [href]="courseImage(course.name).creditUrl" target="_blank" rel="noopener noreferrer">LocalFitness</a> · <a href="https://creativecommons.org/licenses/by-sa/3.0/" target="_blank" rel="noopener noreferrer">CC BY-SA 3.0</a> · <a href="https://commons.wikimedia.org/wiki/File:Spin_Cycle_Indoor_Cycling_Class_at_a_Gym.JPG" target="_blank" rel="noopener noreferrer">image source</a></small>
              </div>
              <div class="actions" *ngIf="isAdmin">
                <button class="button quiet" type="button" (click)="editCourse(course)">Edit</button>
                <button class="button quiet danger" type="button" (click)="deleteCourse(course)">Delete</button>
              </div>
            </article>
          </div>
          <form *ngIf="isAdmin" class="form-grid" (ngSubmit)="saveCourse()">
            <h3>{{ editingCourseId ? 'Update course offering' : 'Create course and schedule a session' }}</h3>
            <label>Course type
              <select name="courseName" [(ngModel)]="courseDraft.name" required>
                <option value="">Choose a course type</option>
                <option *ngFor="let type of courseTypes" [value]="type">{{ type }}</option>
              </select>
            </label>
            <label>Description<input name="courseDescription" [(ngModel)]="courseDraft.description"></label>
            <label>Difficulty
              <select name="courseDifficulty" [(ngModel)]="courseDraft.difficultyLevel">
                <option>BEGINNER</option><option>INTERMEDIATE</option><option>ADVANCED</option>
              </select>
            </label>
            <label>Duration (minutes)<input name="courseDuration" type="number" min="1" [(ngModel)]="courseDraft.durationMinutes" required></label>
            <label *ngIf="editingCourseId">Required equipment recommendations
              <input name="courseEquipment" [(ngModel)]="courseDraft.requiredEquipment" [placeholder]="courseEquipment(courseDraft.name || '')">
            </label>
            <p *ngIf="!editingCourseId && courseDraft.name" class="form-hint">
              Required equipment: {{ courseEquipment(courseDraft.name) }}
            </p>
            <label class="check"><input name="courseActive" type="checkbox" [(ngModel)]="courseDraft.active"> Active</label>
            <ng-container *ngIf="!editingCourseId">
              <p class="form-hint">If this course type already exists, the new session will use that offering; no duplicate course is created.</p>
              <label>Session date<input name="courseSessionDate" type="date" [(ngModel)]="sessionDraft.sessionDate" required></label>
              <label>Start time<input name="courseSessionStart" type="time" [(ngModel)]="sessionDraft.startTime" required></label>
              <label>End time<input name="courseSessionEnd" type="time" [(ngModel)]="sessionDraft.endTime" required></label>
              <label>Assigned Coach (Keycloak subject)<input name="courseSessionCoach" [(ngModel)]="sessionDraft.coachSubject" required>
                <small>Enter the Coach account's exact Keycloak subject, not their display name or username.</small>
              </label>
              <label>Room<input name="courseSessionRoom" [(ngModel)]="sessionDraft.roomName" required></label>
              <label>Maximum participants<input name="courseSessionCapacity" type="number" min="1" max="200" [(ngModel)]="sessionDraft.maxCapacity" required></label>
            </ng-container>
            <div class="actions"><button class="button" type="submit">{{ editingCourseId ? 'Save course' : 'Create course and schedule session' }}</button>
              <button *ngIf="editingCourseId" class="button quiet" type="button" (click)="resetCourseForm()">Cancel</button></div>
          </form>
        </section>

        <section class="account-card">
          <h2>{{ isAdmin ? 'Scheduled sessions' : isCoach ? 'Assigned sessions' : scheduleDate ? 'Daily schedule' : 'Upcoming classes' }}</h2>
          <label *ngIf="isMember || isAdmin" class="schedule-date">Schedule date
            <input name="scheduleDate" type="date" [(ngModel)]="scheduleDate" (ngModelChange)="loadWorkspace()">
            <small>Leave blank to show upcoming sessions.</small>
          </label>
          <p *ngIf="sessions.length === 0" class="muted">
            {{ isCoach
              ? 'No sessions are assigned to this Coach account. Ask an Admin to check that your Keycloak subject, not your display name, is assigned.'
              : scheduleDate
                ? 'No sessions are scheduled for ' + scheduleDate + '. Clear the date to show upcoming sessions.'
                : 'No upcoming sessions are scheduled.' }}
          </p>
          <div class="item-list">
            <article class="item" *ngFor="let session of sessions">
              <div>
                <img class="course-picture schedule-picture" [src]="courseImage(session.course.name).src"
                  [alt]="courseImage(session.course.name).alt" loading="lazy" (error)="onCourseImageError($event)">
                <strong>{{ session.course.name }} · {{ session.sessionDate }} · {{ session.startTime.slice(0, 5) }}–{{ session.endTime.slice(0, 5) }}</strong>
                <p>{{ session.roomName }} · Coach {{ session.coachUsername }} · {{ session.status }}</p>
                <small>{{ session.remainingPlaces }} places remaining · {{ session.maxCapacity }} maximum</small>
                <small *ngIf="isCoach"><strong>Required Equipment:</strong> {{ session.course.requiredEquipment || courseEquipment(session.course.name) }}</small>
                <small *ngIf="courseImage(session.course.name).creditUrl">Photo by <a [href]="courseImage(session.course.name).creditUrl" target="_blank" rel="noopener noreferrer">LocalFitness</a> · <a href="https://creativecommons.org/licenses/by-sa/3.0/" target="_blank" rel="noopener noreferrer">CC BY-SA 3.0</a> · <a href="https://commons.wikimedia.org/wiki/File:Spin_Cycle_Indoor_Cycling_Class_at_a_Gym.JPG" target="_blank" rel="noopener noreferrer">image source</a></small>
                <small *ngIf="isMember && session.status === 'SCHEDULED' && !session.bookingOpen && session.remainingPlaces === 0">This session is full.</small>
                <small *ngIf="isMember && session.status === 'SCHEDULED' && !session.bookingOpen && session.remainingPlaces > 0">Reservations close 24 hours before the session.</small>
              </div>
              <div class="actions">
                <button class="button quiet" type="button" *ngIf="isAdmin && session.status === 'SCHEDULED'" (click)="editSession(session)">Reschedule</button>
                <button class="button quiet danger" type="button" *ngIf="isAdmin && session.status === 'SCHEDULED'" (click)="cancelSession(session)">Cancel</button>
                <button class="button quiet" type="button" *ngIf="isMember && canReserve(session)" (click)="reserve(session)">Reserve</button>
                <span *ngIf="isMember && hasReservation(session.sessionId)">Reserved</span>
              </div>
            </article>
          </div>
          <form *ngIf="isAdmin && editingSessionId" class="form-grid" (ngSubmit)="saveSession()">
            <h3>Reschedule session</h3>
            <label>Date<input name="sessionDate" type="date" [(ngModel)]="sessionDraft.sessionDate" required></label>
            <label>Start<input name="sessionStart" type="time" [(ngModel)]="sessionDraft.startTime" required></label>
            <label>End<input name="sessionEnd" type="time" [(ngModel)]="sessionDraft.endTime" required></label>
            <label>Coach subject (Keycloak ID)<input name="sessionCoach" [(ngModel)]="sessionDraft.coachSubject" required>
              <small>Enter the Coach account's exact Keycloak subject, not their display name or username.</small>
            </label>
            <label>Room<input name="sessionRoom" [(ngModel)]="sessionDraft.roomName" required></label>
            <label>Capacity<input name="sessionCapacity" type="number" min="1" max="200" [(ngModel)]="sessionDraft.maxCapacity" required></label>
            <div class="actions"><button class="button" type="submit">Save schedule</button>
              <button class="button quiet" type="button" (click)="resetSessionForm()">Cancel</button></div>
          </form>
        </section>

        <section class="account-card">
          <h2>{{ isStaff ? 'Reservations' : 'My reservations' }}</h2>
          <p *ngIf="reservations.length === 0" class="muted">No reservations yet.</p>
          <div class="item-list">
            <article class="item" *ngFor="let reservation of reservations">
              <div>
                <strong>{{ reservation.session.course.name }} · {{ reservation.session.sessionDate }} · {{ reservation.session.startTime.slice(0, 5) }}–{{ reservation.session.endTime.slice(0, 5) }}</strong>
                <p>Coach {{ reservation.session.coachUsername }} · {{ reservation.session.roomName }}</p>
                <p *ngIf="isStaff">Member: {{ reservation.memberUsername }}</p>
                <small>Status: {{ reservation.status }}</small>
              </div>
              <div class="actions" *ngIf="(isAdmin || isMember) && reservation.status === 'CONFIRMED'">
                <select [name]="'move-' + reservation.reservationId" [(ngModel)]="reservationTargets[reservation.reservationId]" aria-label="Move reservation to session">
                  <option value="">Move to session…</option>
                  <option *ngFor="let session of reservableSessions(reservation.session.sessionId)" [value]="session.sessionId">{{ session.course.name }} · {{ session.sessionDate }} · {{ session.startTime.slice(0, 5) }}</option>
                </select>
                <button class="button quiet" type="button" *ngIf="reservationTargets[reservation.reservationId]" (click)="moveReservation(reservation)">Move</button>
                <button class="button quiet danger" type="button" (click)="cancelReservation(reservation)">Cancel</button>
              </div>
            </article>
          </div>
        </section>

        <section class="account-card" *ngIf="isAdmin">
          <h2>Intelligent timetable assistant</h2>
          <p>Generate rule-checked draft slots. They are not saved as sessions until you approve one below.</p>
          <div class="form-grid">
            <label>From<input name="assistantFrom" type="date" [(ngModel)]="assistantFrom"></label>
            <label>To<input name="assistantTo" type="date" [(ngModel)]="assistantTo"></label>
            <label>Coach subject<input name="assistantCoach" [(ngModel)]="assistantCoach" required></label>
            <label>Capacity<input name="assistantCapacity" type="number" min="1" max="200" [(ngModel)]="assistantCapacity"></label>
            <button class="button" type="button" (click)="generateRecommendations()">Generate drafts</button>
          </div>
          <p>{{ assistantSummary }}</p>
          <div class="item-list" *ngIf="recommendations.length">
            <article class="item" *ngFor="let suggestion of recommendations">
              <div><strong>{{ suggestion.courseName }} · {{ suggestion.date }} · {{ suggestion.startTime.slice(0, 5) }}–{{ suggestion.endTime.slice(0, 5) }}</strong>
                <p>{{ suggestion.roomName }} · Coach {{ suggestion.coachUsername }} · score {{ suggestion.score }}</p><small>{{ suggestion.reason }}</small></div>
              <button class="button" type="button" (click)="approveRecommendation(suggestion)">Approve and create session</button>
            </article>
          </div>
        </section>
      </ng-container>
    </main>
  `,
  styles: [
    '.account-card { margin-top: 20px; }',
    '.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }',
    '.summary-grid article { display: grid; gap: 5px; padding: 14px; border: 1px solid var(--border, #dfe7df); border-radius: 12px; }',
    '.summary-grid strong { font-size: 1.5rem; }',
    '.summary-grid span { color: var(--muted, #aab7b0); }',
    '.item-list { display: grid; gap: 10px; margin: 14px 0; }',
    '.item { display: flex; justify-content: space-between; align-items: center; gap: 14px; border: 1px solid var(--border, #dfe7df); border-radius: 12px; padding: 14px 16px; }',
    '.item p { margin: 6px 0; }',
    '.course-picture { display: block; width: min(320px, 100%); aspect-ratio: 16 / 9; object-fit: cover; border-radius: 10px; margin-bottom: 12px; background: #1d3027; }',
    '.schedule-picture { width: min(240px, 100%); }',
    '.schedule-date { max-width: 260px; margin: 16px 0; }',
    '.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }',
    '.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; border-top: 1px solid var(--border, #dfe7df); margin-top: 18px; padding-top: 16px; }',
    '.form-grid h3, .form-hint { grid-column: 1 / -1; margin: 0; }',
    'label { display: grid; gap: 6px; color: var(--muted, #aab7b0); }',
    'input, select { min-width: 0; border: 1px solid var(--border, #dfe7df); border-radius: 8px; padding: 9px; color: inherit; background: var(--surface, #17221c); }',
    '.check { display: flex; align-items: center; gap: 8px; }',
    '.check input { min-width: auto; }',
    '.muted { color: var(--muted, #aab7b0); margin-top: 12px; }',
    '.error-box { margin-top: 12px; color: #f7d7d7; }',
    '.danger { color: #f7d7d7; }',
    '@media (max-width: 650px) { .item { align-items: flex-start; flex-direction: column; } }'
  ]
})
export class ModuleComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly coursesApi = inject(CoursesApiService);
  readonly auth = inject(AuthService);

  readonly title = String(this.route.snapshot.data['title']);
  readonly moduleCode = String(this.route.snapshot.data['module']);
  readonly description = String(this.route.snapshot.data['description']);

  courses: Course[] = [];
  sessions: CourseSession[] = [];
  reservations: Reservation[] = [];
  recommendations: TimetableRecommendation[] = [];
  reservationTargets: Record<string, string> = {};
  assistantSummary = 'No timetable drafts generated yet.';
  loading = false;
  notice = '';
  error = '';
  editingCourseId = '';
  editingSessionId = '';
  scheduleDate = '';
  readonly courseTypes = [
    'Strength Training',
    'HIIT (High-Intensity Interval Training)',
    'Indoor Cycling',
    'Yoga',
    'Pilates',
    'Boxing Fitness',
    'Zumba / Dance Fitness',
    'Functional Training'
  ];
  private readonly fallbackCourseImage = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 640 360'%3E%3Crect width='640' height='360' fill='%231d3027'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%23a8c7b1' font-family='sans-serif' font-size='24'%3EFitness course%3C/text%3E%3C/svg%3E";
  private readonly coursePhotos: Record<string, { src: string; alt: string; creditUrl?: string }> = {
    'Strength Training': {
      src: 'https://images.unsplash.com/photo-1517836357463-d25dfeac3438?auto=format&fit=crop&w=900&q=80',
      alt: 'Athlete lifting a barbell during strength training'
    },
    'HIIT (High-Intensity Interval Training)': {
      src: 'https://images.unsplash.com/photo-1534258936925-c58bed479fcb?auto=format&fit=crop&w=900&q=80',
      alt: 'Athletes training with battle ropes in a fitness studio'
    },
    'Indoor Cycling': {
      src: 'https://upload.wikimedia.org/wikipedia/commons/f/fa/Spin_Cycle_Indoor_Cycling_Class_at_a_Gym.JPG',
      alt: 'A group taking an indoor cycling class on stationary bikes',
      creditUrl: 'https://www.localfitness.com.au'
    },
    Yoga: {
      src: 'https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=900&q=80',
      alt: 'Person practising yoga in an open, calm setting'
    },
    Pilates: {
      src: 'https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=900&q=80',
      alt: 'People practising controlled Pilates movements on mats'
    },
    'Boxing Fitness': {
      src: 'https://images.unsplash.com/photo-1549719386-74dfcbf7dbed?auto=format&fit=crop&w=900&q=80',
      alt: 'Boxing gloves ready for a fitness workout'
    },
    'Zumba / Dance Fitness': {
      src: 'https://images.unsplash.com/photo-1504609813442-a8924e83f76e?auto=format&fit=crop&w=900&q=80',
      alt: 'Group dancing together during a dance fitness class'
    },
    'Functional Training': {
      src: 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=900&q=80',
      alt: 'Functional training equipment in a modern gym'
    }
  };
  assistantFrom = localDateOffset(1);
  assistantTo = localDateOffset(7);
  assistantCoach = this.auth.subject();
  assistantCapacity = 20;

  courseDraft = this.newCourseDraft();
  sessionDraft = this.newSessionDraft();

  get isCoursesModule(): boolean {
    return this.moduleCode === 'M2';
  }

  courseEquipment(courseName: string): string {
    const recommendations: Record<string, string> = {
      'Strength Training': 'Barbells, weight plates, dumbbells, benches, squat racks',
      'HIIT (High-Intensity Interval Training)': 'Exercise mats, dumbbells, kettlebells, resistance bands, timers',
      'Indoor Cycling': 'Stationary bikes, water bottles, towels',
      Yoga: 'Yoga mats, yoga blocks, straps',
      Pilates: 'Exercise mats, Pilates balls, resistance bands, rings',
      'Boxing Fitness': 'Boxing gloves, punching bags, hand wraps, skipping ropes',
      'Zumba / Dance Fitness': 'Open workout space, sound system, optional exercise mats',
      'Functional Training': 'Kettlebells, medicine balls, resistance bands, dumbbells, exercise mats'
    };
    return recommendations[courseName] ?? 'Requirements to be confirmed by the Admin.';
  }

  get isStaff(): boolean {
    return this.auth.hasAnyRole(['ADMIN', 'COACH']);
  }

  get isAdmin(): boolean {
    return this.auth.hasAnyRole(['ADMIN']);
  }

  get isCoach(): boolean {
    return this.auth.hasAnyRole(['COACH']) && !this.isAdmin;
  }

  get isMember(): boolean {
    return this.auth.hasAnyRole(['MEMBER']) && !this.isStaff;
  }

  get scheduledSessionCount(): number {
    return this.sessions.filter((session) => session.status === 'SCHEDULED').length;
  }

  get confirmedReservationCount(): number {
    return this.reservations.filter((reservation) => reservation.status === 'CONFIRMED').length;
  }

  ngOnInit(): void {
    if (this.isCoursesModule) this.loadWorkspace();
  }

  loadWorkspace(): void {
    this.loading = true;
    this.error = '';
    forkJoin({
      courses: this.coursesApi.getCourses(),
      sessions: this.coursesApi.getSessions(this.isMember || this.isAdmin ? this.scheduleDate : undefined),
      reservations: this.coursesApi.getReservations(this.isStaff)
    }).subscribe({
      next: (result) => {
        this.courses = result.courses.content;
        this.sessions = result.sessions.content;
        this.reservations = result.reservations.content;
        this.reservationTargets = Object.fromEntries(this.reservations.map((reservation) => [reservation.reservationId, '']));
        this.loading = false;
        if (!this.courses.some((course) => course.active)) {
          this.assistantSummary = 'No active courses are available. Staff can add or activate a course to generate timetable drafts.';
        }
      },
      error: (error: unknown) => {
        this.loading = false;
        this.error = this.describeError(error, 'Could not load the Courses workspace.');
      }
    });
  }

  saveCourse(): void {
    const request = {
      name: this.courseDraft.name,
      description: this.courseDraft.description,
      difficultyLevel: this.courseDraft.difficultyLevel,
      durationMinutes: Number(this.courseDraft.durationMinutes),
      requiredEquipment: this.courseDraft.requiredEquipment,
      active: this.courseDraft.active
    };
    if (!this.editingCourseId) {
      this.scheduleDate = this.sessionDraft.sessionDate;
      this.run(this.coursesApi.createCourseAndSession({
        courseName: String(request.name ?? ''),
        courseDescription: request.description,
        difficultyLevel: String(request.difficultyLevel ?? 'BEGINNER'),
        durationMinutes: Number(request.durationMinutes),
        requiredEquipment: request.requiredEquipment,
        active: Boolean(request.active),
        sessionDate: this.sessionDraft.sessionDate,
        startTime: this.sessionDraft.startTime,
        endTime: this.sessionDraft.endTime,
        coachSubject: this.sessionDraft.coachSubject,
        roomName: this.sessionDraft.roomName,
        maxCapacity: Number(this.sessionDraft.maxCapacity)
      }), 'Course offering created or reused, and session scheduled.', () => {
        this.resetCourseForm();
        this.sessionDraft = this.newSessionDraft();
      });
      return;
    }
    this.run(this.coursesApi.updateCourse(this.editingCourseId, request), 'Course updated.');
    this.resetCourseForm();
  }

  editCourse(course: Course): void {
    this.editingCourseId = course.courseId;
    this.courseDraft = { ...course };
  }

  deleteCourse(course: Course): void {
    this.run(this.coursesApi.deleteCourse(course.courseId), 'Course deleted.');
  }

  resetCourseForm(): void {
    this.editingCourseId = '';
    this.courseDraft = this.newCourseDraft();
  }

  saveSession(): void {
    if (!this.editingSessionId) return;
    this.scheduleDate = this.sessionDraft.sessionDate;
    this.run(this.coursesApi.updateSession(this.editingSessionId, this.sessionDraft), 'Session rescheduled.');
    this.resetSessionForm();
  }

  editSession(session: CourseSession): void {
    this.editingSessionId = session.sessionId;
    this.sessionDraft = {
      sessionDate: session.sessionDate,
      startTime: session.startTime.slice(0, 5),
      endTime: session.endTime.slice(0, 5),
      coachSubject: session.coachSubject,
      roomName: session.roomName,
      maxCapacity: session.maxCapacity
    };
  }

  cancelSession(session: CourseSession): void {
    this.run(this.coursesApi.cancelSession(session.sessionId), 'Session cancelled.');
  }

  resetSessionForm(): void {
    this.editingSessionId = '';
    this.sessionDraft = this.newSessionDraft();
  }

  reserve(session: CourseSession): void {
    this.run(this.coursesApi.createReservation(session.sessionId), 'Reservation created.');
  }

  moveReservation(reservation: Reservation): void {
    const targetSessionId = this.reservationTargets[reservation.reservationId];
    if (!targetSessionId) return;
    this.run(this.coursesApi.updateReservation(reservation.reservationId, targetSessionId), 'Reservation moved.');
  }

  cancelReservation(reservation: Reservation): void {
    this.run(this.coursesApi.cancelReservation(reservation.reservationId), 'Reservation cancelled.');
  }

  confirmedCount(sessionId: string): number {
    return this.reservations.filter((reservation) =>
      reservation.session.sessionId === sessionId && reservation.status === 'CONFIRMED').length;
  }

  hasReservation(sessionId: string): boolean {
    return this.reservations.some((reservation) =>
      reservation.session.sessionId === sessionId && reservation.status === 'CONFIRMED');
  }

  canReserve(session: CourseSession): boolean {
    return session.status === 'SCHEDULED'
      && session.bookingOpen
      && session.remainingPlaces > 0
      && !this.hasReservation(session.sessionId);
  }

  reservableSessions(excludingSessionId: string): CourseSession[] {
    return this.sessions.filter((session) =>
      session.status === 'SCHEDULED'
      && session.bookingOpen
      && session.remainingPlaces > 0
      && session.sessionId !== excludingSessionId
      && !this.hasReservation(session.sessionId));
  }

  courseImage(courseName: string): { src: string; alt: string; creditUrl?: string } {
    return this.coursePhotos[courseName] ?? {
      src: this.fallbackCourseImage,
      alt: `${courseName} course image unavailable`
    };
  }

  onCourseImageError(event: Event): void {
    const image = event.target;
    if (image instanceof HTMLImageElement && image.src !== this.fallbackCourseImage) {
      image.src = this.fallbackCourseImage;
    }
  }

  generateRecommendations(): void {
    this.error = '';
    this.notice = '';
    this.coursesApi.getTimetableSuggestions(this.assistantFrom, this.assistantTo, 10, this.assistantCoach).subscribe({
      next: (response) => {
        this.recommendations = response.recommendations;
        this.assistantSummary = response.summary;
      },
      error: (error: unknown) => {
        this.error = this.describeError(error, 'The timetable assistant could not generate drafts.');
      }
    });
  }

  approveRecommendation(recommendation: TimetableRecommendation): void {
    if (!this.assistantCoach.trim()) {
      this.error = 'Enter an available coach subject before approving a timetable draft.';
      return;
    }
    this.run(this.coursesApi.createSession(recommendation.courseId, {
      sessionDate: recommendation.date,
      startTime: recommendation.startTime,
      endTime: recommendation.endTime,
      coachSubject: this.assistantCoach.trim(),
      roomName: recommendation.roomName,
      maxCapacity: Number(this.assistantCapacity)
    }), 'Draft approved and session created.');
  }

  private run(action: Observable<unknown>, successMessage: string, onSuccess?: () => void): void {
    this.notice = '';
    this.error = '';
    action.subscribe({
      next: () => {
        this.notice = successMessage;
        this.loadWorkspace();
        onSuccess?.();
      },
      error: (error: unknown) => {
        this.error = this.describeError(error, 'The requested change could not be saved.');
      }
    });
  }

  private describeError(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) return fallback;
    const body = error.error as { detail?: unknown; message?: unknown; reason?: unknown } | string | null;
    if (typeof body === 'string' && body.trim()) return body;
    if (body && typeof body === 'object') {
      for (const message of [body.detail, body.message, body.reason]) {
        if (typeof message === 'string' && message.trim()) return message;
      }
    }
    return error.status
      ? `${fallback} (HTTP ${error.status})`
      : `${fallback} Check that the API is running.`;
  }

  private newCourseDraft(): Partial<Course> {
    return { name: '', description: '', difficultyLevel: 'BEGINNER', durationMinutes: 45, requiredEquipment: '', active: true };
  }

  private newSessionDraft(): {
    sessionDate: string;
    startTime: string;
    endTime: string;
    coachSubject: string;
    roomName: string;
    maxCapacity: number;
  } {
    return {
      sessionDate: localDateOffset(1),
      startTime: '09:00',
      endTime: '09:45',
      coachSubject: this.auth.subject(),
      roomName: 'Room A',
      maxCapacity: 20
    };
  }

}

function localDateOffset(days: number): string {
  const value = new Date();
  value.setDate(value.getDate() + days);
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
