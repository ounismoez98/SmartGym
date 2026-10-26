import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface TimetableRecommendation {
  courseId: string;
  courseName: string;
  date: string;
  startTime: string;
  endTime: string;
  roomName: string;
  coachSubject: string;
  coachUsername: string;
  score: number;
  reason: string;
}

export interface TimetableAssistantResponse {
  recommendations: TimetableRecommendation[];
  summary: string;
}

export interface Course {
  courseId: string;
  name: string;
  description: string;
  difficultyLevel: string;
  durationMinutes: number;
  requiredEquipment: string;
  active: boolean;
}

export interface CourseSession {
  sessionId: string;
  course: Course;
  sessionDate: string;
  startTime: string;
  endTime: string;
  coachSubject: string;
  coachUsername: string;
  roomName: string;
  maxCapacity: number;
  status: string;
  remainingPlaces: number;
  bookingOpen: boolean;
}

export interface Reservation {
  reservationId: string;
  memberSubject: string;
  memberUsername: string;
  session: CourseSession;
  status: string;
}

export interface PageResponse<T> {
  content: T[];
}

@Injectable({ providedIn: 'root' })
export class CoursesApiService {
  private readonly http = inject(HttpClient);

  getCourses() {
    return this.http.get<PageResponse<Course>>('/api/courses', { params: { page: 0, size: 100 } });
  }

  createCourse(course: Partial<Course>) {
    return this.http.post<Course>('/api/courses', course);
  }

  createCourseAndSession(request: {
    courseName: string;
    courseDescription?: string;
    difficultyLevel: string;
    durationMinutes: number;
    requiredEquipment?: string;
    active: boolean;
    sessionDate: string;
    startTime: string;
    endTime: string;
    coachSubject: string;
    roomName: string;
    maxCapacity: number;
  }) {
    return this.http.post<CourseSession>('/api/courses/schedule', request);
  }

  updateCourse(courseId: string, course: Partial<Course>) {
    return this.http.put<Course>(`/api/courses/${courseId}`, course);
  }

  deleteCourse(courseId: string) {
    return this.http.delete<void>(`/api/courses/${courseId}`);
  }

  getSessions(date?: string) {
    const params: Record<string, string | number> = { size: 100 };
    if (date) params['date'] = date;
    return this.http.get<PageResponse<CourseSession>>('/api/courses/sessions', { params });
  }

  createSession(courseId: string, session: Partial<CourseSession>) {
    return this.http.post<CourseSession>(`/api/courses/${courseId}/sessions`, session);
  }

  updateSession(sessionId: string, session: Partial<CourseSession>) {
    return this.http.put<CourseSession>(`/api/courses/sessions/${sessionId}`, session);
  }

  cancelSession(sessionId: string) {
    return this.http.put<CourseSession>(`/api/courses/sessions/${sessionId}/cancel`, {});
  }

  getReservations(isStaff: boolean) {
    const endpoint = isStaff ? '/api/courses/reservations' : '/api/courses/reservations/me';
    return this.http.get<PageResponse<Reservation>>(endpoint, { params: { size: 100 } });
  }

  createReservation(sessionId: string) {
    return this.http.post<Reservation>(`/api/courses/sessions/${sessionId}/reservations`, {});
  }

  updateReservation(reservationId: string, sessionId: string) {
    return this.http.put<Reservation>(`/api/courses/reservations/${reservationId}`, { sessionId });
  }

  cancelReservation(reservationId: string) {
    return this.http.put<Reservation>(`/api/courses/reservations/${reservationId}/cancel`, {});
  }

  getTimetableSuggestions(fromDate?: string, toDate?: string, limit = 3, coachSubject?: string) {
    let params: Record<string, string | number> = { limit };
    if (fromDate) params = { ...params, fromDate };
    if (toDate) params = { ...params, toDate };
    if (coachSubject) params = { ...params, coachSubject };
    return this.http.get<TimetableAssistantResponse>('/api/courses/assistant/timetable', { params });
  }
}
