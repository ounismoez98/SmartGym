import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export type MuscleGroup =
  | 'CHEST' | 'BACK' | 'SHOULDERS' | 'BICEPS' | 'TRICEPS' | 'CORE'
  | 'QUADRICEPS' | 'HAMSTRINGS' | 'GLUTES' | 'CALVES' | 'FULL_BODY' | 'OTHER';
export type ExerciseDifficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type EquipmentCategory =
  | 'BODYWEIGHT' | 'DUMBBELL' | 'BARBELL' | 'KETTLEBELL'
  | 'CABLE' | 'MACHINE' | 'RESISTANCE_BAND' | 'OTHER';

export interface Exercise {
  id: string;
  name: string;
  description: string | null;
  instructions: string;
  primaryMuscleGroup: MuscleGroup;
  difficulty: ExerciseDifficulty;
  equipmentCategory: EquipmentCategory;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export type ExerciseInput = Pick<
  Exercise,
  'name' | 'description' | 'instructions' | 'primaryMuscleGroup' | 'difficulty' | 'equipmentCategory'
>;

export interface ExercisePage {
  content: Exercise[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ExerciseFilters {
  name: string;
  muscleGroup: MuscleGroup | '';
  difficulty: ExerciseDifficulty | '';
  equipmentCategory: EquipmentCategory | '';
  page: number;
}

@Injectable({ providedIn: 'root' })
export class TrainingApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/training/exercises';

  getExercises(filters: ExerciseFilters) {
    let params = new HttpParams().set('page', filters.page).set('size', 12);
    if (filters.name.trim()) params = params.set('name', filters.name.trim());
    if (filters.muscleGroup) params = params.set('muscleGroup', filters.muscleGroup);
    if (filters.difficulty) params = params.set('difficulty', filters.difficulty);
    if (filters.equipmentCategory) params = params.set('equipmentCategory', filters.equipmentCategory);
    return this.http.get<ExercisePage>(this.endpoint, { params });
  }

  createExercise(input: ExerciseInput) {
    return this.http.post<Exercise>(this.endpoint, input);
  }

  updateExercise(id: string, input: ExerciseInput, version: number) {
    return this.http.patch<Exercise>(`${this.endpoint}/${id}`, { ...input, version });
  }

  archiveExercise(id: string) {
    return this.http.post<void>(`${this.endpoint}/${id}/archive`, {});
  }
}
