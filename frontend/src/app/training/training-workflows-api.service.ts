import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Exercise, ExercisePage } from './training-api.service';

export type ProgrammeStatus =
  | 'DRAFT' | 'PENDING_REVIEW' | 'CHANGES_REQUESTED' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED';
export type ExperienceLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

export interface ProgrammeItemInput {
  exerciseId: string;
  sets: number;
  repetitions: number | null;
  durationSeconds: number | null;
  restSeconds: number;
  suggestedLoadKg: number | null;
  notes: string | null;
}

export interface ProgrammeSessionInput {
  title: string;
  notes: string | null;
  items: ProgrammeItemInput[];
}

export interface ProgrammeInput {
  memberId: string;
  title: string;
  goal: string;
  experienceLevel: ExperienceLevel;
  durationWeeks: number;
  sessions: ProgrammeSessionInput[];
  version: number | null;
}

export interface ProgrammeItem extends ProgrammeItemInput { id: string; orderIndex: number; }
export interface ProgrammeSession extends Omit<ProgrammeSessionInput, 'items'> {
  id: string;
  dayOrder: number;
  items: ProgrammeItem[];
}
export interface CoachReview {
  reviewedRevision: number;
  coachId: string;
  decision: 'APPROVED' | 'CHANGES_REQUESTED';
  comment: string | null;
  reviewedAt: string;
}
export interface Programme {
  id: string;
  memberId: string;
  coachId: string;
  title: string;
  goal: string;
  experienceLevel: ExperienceLevel;
  durationWeeks: number;
  status: ProgrammeStatus;
  origin: 'MANUAL' | 'AI';
  revision: number;
  approvedRevision: number | null;
  supersedesProgrammeId: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  sessions: ProgrammeSession[];
  reviewHistory: CoachReview[];
}

export interface WorkoutSetInput {
  exerciseId: string;
  repetitions: number | null;
  durationSeconds: number | null;
  loadKg: number | null;
  notes: string | null;
}
export interface WorkoutLogInput {
  programmeId: string | null;
  programmeSessionId: string | null;
  performedAt: string;
  notes: string | null;
  sets: WorkoutSetInput[];
}
export interface WorkoutLog extends Omit<WorkoutLogInput, 'sets'> {
  id: string;
  memberId: string;
  createdAt: string;
  updatedAt: string;
  version: number;
  sets: Array<WorkoutSetInput & { setNumber: number }>;
}
export interface WorkoutLogPage {
  content: WorkoutLog[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Progress {
  memberId: string;
  completedWorkouts: number;
  totalRepetitions: number;
  trainingVolumeKgRepetitions: number;
  maximumRecordedLoadKgByExercise: Record<string, number>;
  completedWorkoutsByIsoWeek: Record<string, number>;
}

@Injectable({ providedIn: 'root' })
export class TrainingWorkflowsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/training';

  getExercises(): import('rxjs').Observable<ExercisePage> {
    const params = new HttpParams().set('page', 0).set('size', 50);
    return this.http.get<ExercisePage>(`${this.base}/exercises`, { params });
  }

  getProgrammes(status?: ProgrammeStatus) {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<Programme[]>(`${this.base}/programmes`, { params });
  }

  createProgramme(input: ProgrammeInput) {
    return this.http.post<Programme>(`${this.base}/programmes`, input);
  }

  updateProgramme(programme: Programme, input: ProgrammeInput) {
    return this.http.patch<Programme>(`${this.base}/programmes/${programme.id}`, {
      ...input,
      version: programme.version
    });
  }

  submitProgramme(id: string) {
    return this.http.post<Programme>(`${this.base}/programmes/${id}/submit`, {});
  }

  reviewProgramme(id: string, decision: CoachReview['decision'], comment: string) {
    return this.http.post<Programme>(`${this.base}/programmes/${id}/reviews`, { decision, comment });
  }

  publishProgramme(id: string) {
    return this.http.post<Programme>(`${this.base}/programmes/${id}/publish`, {});
  }

  archiveProgramme(id: string) {
    return this.http.post<Programme>(`${this.base}/programmes/${id}/archive`, {});
  }

  createRevision(id: string) {
    return this.http.post<Programme>(`${this.base}/programmes/${id}/revisions`, {});
  }

  createWorkout(input: WorkoutLogInput) {
    return this.http.post<WorkoutLog>(`${this.base}/workout-logs`, input);
  }

  getWorkoutLogs(page = 0) {
    return this.http.get<WorkoutLogPage>(`${this.base}/workout-logs`, {
      params: new HttpParams().set('page', page).set('size', 20)
    });
  }

  updateWorkoutLog(log: WorkoutLog, input: WorkoutLogInput) {
    return this.http.patch<WorkoutLog>(`${this.base}/workout-logs/${log.id}`, {
      performedAt: input.performedAt,
      notes: input.notes,
      sets: input.sets,
      version: log.version
    });
  }

  deleteWorkoutLog(id: string) {
    return this.http.delete<void>(`${this.base}/workout-logs/${id}`);
  }

  getProgress() {
    return this.http.get<Progress>(`${this.base}/me/progress`);
  }
}
