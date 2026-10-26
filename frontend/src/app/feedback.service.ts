import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type FeedbackCategory = 'CLEANLINESS' | 'EQUIPMENT' | 'CLASSES' | 'STAFF' | 'SERVICE';
export type FeedbackSentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';
export type FeedbackUrgency = 'CRITICAL' | 'MEDIUM' | 'LOW';

export interface FeedbackSubmitRequest {
  rating: number;
  category: FeedbackCategory;
  comment: string;
}

export interface FeedbackResponse {
  id: string;
  subject: string;
  memberName: string;
  rating: number;
  category: FeedbackCategory;
  comment: string;
  sentiment: FeedbackSentiment;
  summary: string;
  themes: string[];
  urgency: FeedbackUrgency;
  actionRecommendation: string;
  confidenceScore: number;
  createdAt: string;
}

export interface CategoryStat {
  category: string;
  displayName: string;
  count: number;
  averageRating: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
}

export interface ThemeStat {
  theme: string;
  count: number;
}

export interface AiExecutiveReport {
  healthScore: number;
  overallAssessment: string;
  keyStrengths: string[];
  criticalIssues: string[];
  strategicRecommendations: string[];
  generatedAt: string;
}

export interface FeedbackDashboardStats {
  totalFeedbacks: number;
  averageRating: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  positivePercentage: number;
  neutralPercentage: number;
  negativePercentage: number;
  criticalCount: number;
  mediumCount: number;
  lowCount: number;
  ratingDistribution: Record<number, number>;
  categoryBreakdown: CategoryStat[];
  commonThemes: ThemeStat[];
  executiveReport?: AiExecutiveReport;
}

@Injectable({ providedIn: 'root' })
export class FeedbackService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/feedback';

  submitFeedback(request: FeedbackSubmitRequest): Observable<FeedbackResponse> {
    return this.http.post<FeedbackResponse>(this.baseUrl, request);
  }

  getMyFeedback(): Observable<FeedbackResponse[]> {
    return this.http.get<FeedbackResponse[]>(`${this.baseUrl}/my`);
  }

  getAllFeedback(filters?: {
    category?: string;
    sentiment?: string;
    rating?: number;
    urgency?: string;
    search?: string;
  }): Observable<FeedbackResponse[]> {
    let params = new HttpParams();
    if (filters?.category) params = params.set('category', filters.category);
    if (filters?.sentiment) params = params.set('sentiment', filters.sentiment);
    if (filters?.urgency) params = params.set('urgency', filters.urgency);
    if (filters?.rating !== undefined && filters.rating !== null && filters.rating > 0) {
      params = params.set('rating', filters.rating.toString());
    }
    if (filters?.search && filters.search.trim()) {
      params = params.set('search', filters.search.trim());
    }
    return this.http.get<FeedbackResponse[]>(`${this.baseUrl}/admin/all`, { params });
  }

  getDashboardStats(): Observable<FeedbackDashboardStats> {
    return this.http.get<FeedbackDashboardStats>(`${this.baseUrl}/admin/stats`);
  }

  getExecutiveReport(): Observable<AiExecutiveReport> {
    return this.http.get<AiExecutiveReport>(`${this.baseUrl}/admin/executive-report`);
  }

  reanalyzeAllFeedbacks(): Observable<FeedbackResponse[]> {
    return this.http.post<FeedbackResponse[]>(`${this.baseUrl}/admin/reanalyze-all`, {});
  }

  deleteFeedback(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/${id}`);
  }

  reanalyzeFeedback(id: string): Observable<FeedbackResponse> {
    return this.http.post<FeedbackResponse>(`${this.baseUrl}/admin/${id}/reanalyze`, {});
  }
}
