import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import {
  AiExecutiveReport,
  CategoryStat,
  FeedbackCategory,
  FeedbackDashboardStats,
  FeedbackResponse,
  FeedbackSentiment,
  FeedbackService,
  FeedbackSubmitRequest,
  FeedbackUrgency,
  ThemeStat
} from './feedback.service';

@Component({
  selector: 'app-feedback',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="app-shell">
      <header class="topbar">
        <a class="brand" routerLink="/dashboard">SMART<span>GYM</span></a>
        <div class="topbar-user">
          <a class="button quiet back-btn" routerLink="/dashboard">← Dashboard</a>
          <span class="avatar">{{ initials }}</span>
          <span>{{ auth.userName() }}</span>
        </div>
      </header>

      <main class="content">
        <div class="header-section">
          <div>
            <p class="eyebrow">{{ isAdmin ? 'EXECUTIVE INTELLIGENCE &amp; DSS' : 'MEMBER SATISFACTION' }}</p>
            <h1>{{ isAdmin ? 'AI Customer Intelligence & Satisfaction Analytics' : 'Your Feedback & Experience' }}</h1>
            <p class="lead">
              @if (isAdmin) {
                Enterprise Decision Support System (DSS) powered by Groq LLM inference. Automated root-cause detection,
                urgency triage, managerial action recommendations, and executive health scoring.
              } @else {
                Share your gym experience, rate our equipment, classes, and facilities, and help us continuously improve.
              }
            </p>
          </div>

          @if (isAdmin) {
            <div class="admin-badge-container">
              <span class="role-badge">ADMIN DSS VIEW</span>
            </div>
          }
        </div>

        @if (notificationMessage) {
          <div class="notice" [class.error]="isNotificationError" role="alert">
            {{ notificationMessage }}
            <button type="button" class="close-notice-btn" (click)="notificationMessage = ''">&times;</button>
          </div>
        }

        <!-- ============================================== -->
        <!-- ADMIN DASHBOARD: EXECUTIVE AI BRIEFING & STATS -->
        <!-- ============================================== -->
        @if (isAdmin) {
          <section class="admin-analytics-section" aria-labelledby="analytics-heading">
            <div class="section-title-row">
              <h2 id="analytics-heading">Executive Intelligence Dashboard</h2>
              <div class="action-buttons-group">
                <button
                  class="button quiet"
                  (click)="reanalyzeAllFeedbacks()"
                  [disabled]="isReanalyzingAll"
                  title="Re-analyze all reviews through Groq LLM"
                >
                  {{ isReanalyzingAll ? '⚡ Batch Processing...' : '⚡ Batch Re-Analyze All' }}
                </button>
                <button
                  class="button quiet refresh-btn"
                  (click)="loadAdminStats(true)"
                  [disabled]="isLoadingStats || isRefreshingExecutiveReport"
                >
                  {{ isLoadingStats ? 'Updating...' : '⟳ Refresh Analytics' }}
                </button>
              </div>
            </div>

            <!-- EXECUTIVE AI BRIEFING HERO CARD -->
            @if (executiveReport) {
              <div class="ai-executive-hero">
                <div class="hero-top">
                  <div class="hero-title-group">
                    <span class="ai-chip">✨ GROQ LLM DECISION SUPPORT SYSTEM</span>
                    <h3>Executive Operational Health Briefing</h3>
                  </div>

                  <div class="health-gauge" [class]="getHealthScoreClass(executiveReport.healthScore)">
                    <div class="gauge-val">{{ executiveReport.healthScore }}<span>/100</span></div>
                    <span class="gauge-label">Health Index</span>
                  </div>
                </div>

                <p class="executive-narrative">{{ executiveReport.overallAssessment }}</p>

                <div class="executive-columns">
                  <!-- Strengths -->
                  <div class="exec-col strengths-col">
                    <div class="col-header">
                      <span class="col-icon">🌟</span>
                      <h4>Top Operational Strengths</h4>
                    </div>
                    <ul class="exec-list">
                      @for (s of executiveReport.keyStrengths; track s) {
                        <li>{{ s }}</li>
                      }
                    </ul>
                  </div>

                  <!-- Critical Issues -->
                  <div class="exec-col issues-col">
                    <div class="col-header">
                      <span class="col-icon">⚠️</span>
                      <h4>Critical Churn Risks &amp; Bottlenecks</h4>
                    </div>
                    <ul class="exec-list">
                      @for (issue of executiveReport.criticalIssues; track issue) {
                        <li>{{ issue }}</li>
                      }
                    </ul>
                  </div>
                </div>

                <!-- Strategic Recommendations -->
                <div class="strategic-roadmap">
                  <div class="roadmap-header">
                    <span class="roadmap-icon">🎯</span>
                    <h4>AI Strategic Action Roadmap for Gym Management</h4>
                  </div>
                  <div class="recommendations-grid">
                    @for (rec of executiveReport.strategicRecommendations; track rec; let idx = $index) {
                      <div class="recommendation-card">
                        <span class="rec-num">0{{ idx + 1 }}</span>
                        <p class="rec-text">{{ rec }}</p>
                      </div>
                    }
                  </div>
                </div>
              </div>
            }

            @if (stats) {
              <!-- KPI Cards -->
              <div class="kpi-grid">
                <div class="kpi-card">
                  <span class="kpi-label">TOTAL REVIEWS</span>
                  <div class="kpi-value">{{ stats.totalFeedbacks }}</div>
                  <span class="kpi-sub">Total submissions</span>
                </div>

                <div class="kpi-card highlight">
                  <span class="kpi-label">AVERAGE RATING</span>
                  <div class="kpi-value rating-val">
                    {{ stats.averageRating }}<span class="max-star"> / 5.0</span>
                  </div>
                  <div class="stars-display">
                    @for (star of [1, 2, 3, 4, 5]; track star) {
                      <span class="star-icon" [class.filled]="star <= Math.round(stats.averageRating)">★</span>
                    }
                  </div>
                </div>

                <div class="kpi-card">
                  <span class="kpi-label">POSITIVE SENTIMENT</span>
                  <div class="kpi-value sentiment-pos-val">{{ stats.positivePercentage }}%</div>
                  <span class="kpi-sub">{{ stats.positiveCount }} positive reviews</span>
                </div>

                <div class="kpi-card" [class.kpi-critical]="stats.criticalCount > 0">
                  <span class="kpi-label">CRITICAL ATTENTION</span>
                  <div class="kpi-value urgency-val">{{ stats.criticalCount }}</div>
                  <span class="kpi-sub">{{ stats.criticalCount > 0 ? 'Urgent management action required' : 'No critical flags' }}</span>
                </div>
              </div>

              <!-- Sentiment & Rating Distribution Breakdown -->
              <div class="analytics-row">
                <!-- Sentiment Breakdown -->
                <div class="analytics-card">
                  <h3>Sentiment Distribution (Groq AI)</h3>
                  <div class="distribution-bars">
                    <div class="dist-row">
                      <div class="dist-header">
                        <span class="sentiment-pill positive">Positive</span>
                        <span>{{ stats.positiveCount }} ({{ stats.positivePercentage }}%)</span>
                      </div>
                      <div class="bar-container">
                        <div class="bar-fill positive-bar" [style.width.%]="stats.positivePercentage"></div>
                      </div>
                    </div>

                    <div class="dist-row">
                      <div class="dist-header">
                        <span class="sentiment-pill neutral">Neutral</span>
                        <span>{{ stats.neutralCount }} ({{ stats.neutralPercentage }}%)</span>
                      </div>
                      <div class="bar-container">
                        <div class="bar-fill neutral-bar" [style.width.%]="stats.neutralPercentage"></div>
                      </div>
                    </div>

                    <div class="dist-row">
                      <div class="dist-header">
                        <span class="sentiment-pill negative">Negative</span>
                        <span>{{ stats.negativeCount }} ({{ stats.negativePercentage }}%)</span>
                      </div>
                      <div class="bar-container">
                        <div class="bar-fill negative-bar" [style.width.%]="stats.negativePercentage"></div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Star Rating Distribution -->
                <div class="analytics-card">
                  <h3>Rating Distribution</h3>
                  <div class="distribution-bars">
                    @for (star of [5, 4, 3, 2, 1]; track star) {
                      <div class="dist-row">
                        <div class="dist-header">
                          <span class="star-label">{{ star }} Stars ★</span>
                          <span>{{ getRatingCount(star) }} ({{ getRatingPercentage(star) }}%)</span>
                        </div>
                        <div class="bar-container">
                          <div class="bar-fill star-bar" [style.width.%]="getRatingPercentage(star)"></div>
                        </div>
                      </div>
                    }
                  </div>
                </div>
              </div>

              <!-- Common Themes & Category Performance -->
              <div class="analytics-row">
                <!-- Themes -->
                <div class="analytics-card">
                  <h3>AI-Extracted Root Themes</h3>
                  <p class="card-caption">Extracted automatically by Groq LLM. Click any theme tag to filter reviews.</p>
                  <div class="theme-cloud">
                    @for (theme of stats.commonThemes; track theme.theme) {
                      <button
                        type="button"
                        class="theme-chip"
                        [class.active]="filterSearch === theme.theme"
                        (click)="filterByTheme(theme.theme)"
                      >
                        {{ theme.theme }}
                        <span class="theme-count">{{ theme.count }}</span>
                      </button>
                    }
                    @if (stats.commonThemes.length === 0) {
                      <p class="empty-state">No themes detected yet.</p>
                    }
                  </div>
                </div>

                <!-- Category Breakdown -->
                <div class="analytics-card">
                  <h3>Category Operational Performance</h3>
                  <div class="category-grid">
                    @for (cat of stats.categoryBreakdown; track cat.category) {
                      <div class="cat-card" (click)="filterByCategory(cat.category)">
                        <div class="cat-title">{{ cat.displayName }}</div>
                        <div class="cat-score">{{ cat.averageRating }} ★</div>
                        <div class="cat-count">{{ cat.count }} feedback(s)</div>
                        <div class="cat-pill-row">
                          <span class="mini-pos">+{{ cat.positiveCount }}</span>
                          <span class="mini-neu">~{{ cat.neutralCount }}</span>
                          <span class="mini-neg">-{{ cat.negativeCount }}</span>
                        </div>
                      </div>
                    }
                  </div>
                </div>
              </div>
            } @else if (isLoadingStats) {
              <div class="loading-box">Synthesizing executive intelligence via Groq...</div>
            }
          </section>

          <!-- ============================================== -->
          <!-- ADMIN FEEDBACK TRIAGE & DEEP AI INSPECTION     -->
          <!-- ============================================== -->
          <section class="feedback-management-section">
            <div class="section-title-row">
              <h2>Member Reviews &amp; Operational Triage ({{ allFeedbacks.length }})</h2>
              <div class="action-buttons-group">
                <button
                  type="button"
                  class="button quiet"
                  (click)="showSubmitForm = !showSubmitForm"
                >
                  {{ showSubmitForm ? 'Hide Submit Form' : '+ Submit New Review' }}
                </button>
              </div>
            </div>

            <!-- Filters Bar -->
            <div class="filters-card">
              <div class="filter-item search-filter">
                <label for="search-input">Search comments / actions / themes</label>
                <input
                  id="search-input"
                  type="text"
                  placeholder="Filter by keyword, member, theme, action..."
                  [value]="filterSearch"
                  (input)="onSearchInput($event)"
                />
              </div>

              <div class="filter-item">
                <label for="urgency-select">Priority / Urgency</label>
                <select id="urgency-select" [value]="filterUrgency" (change)="onUrgencyChange($event)">
                  <option value="">All Priorities</option>
                  <option value="CRITICAL">🔴 Critical Priority</option>
                  <option value="MEDIUM">🟡 Moderate Priority</option>
                  <option value="LOW">🟢 Routine / Low</option>
                </select>
              </div>

              <div class="filter-item">
                <label for="category-select">Category</label>
                <select id="category-select" [value]="filterCategory" (change)="onCategoryChange($event)">
                  <option value="">All Categories</option>
                  <option value="CLEANLINESS">Cleanliness</option>
                  <option value="EQUIPMENT">Equipment</option>
                  <option value="CLASSES">Classes</option>
                  <option value="STAFF">Staff</option>
                  <option value="SERVICE">Service</option>
                </select>
              </div>

              <div class="filter-item">
                <label for="sentiment-select">Sentiment</label>
                <select id="sentiment-select" [value]="filterSentiment" (change)="onSentimentChange($event)">
                  <option value="">All Sentiments</option>
                  <option value="POSITIVE">Positive</option>
                  <option value="NEUTRAL">Neutral</option>
                  <option value="NEGATIVE">Negative</option>
                </select>
              </div>

              <div class="filter-item">
                <label for="rating-select">Rating</label>
                <select id="rating-select" [value]="filterRating" (change)="onRatingChange($event)">
                  <option value="0">All Ratings</option>
                  <option value="5">5 Stars</option>
                  <option value="4">4 Stars</option>
                  <option value="3">3 Stars</option>
                  <option value="2">2 Stars</option>
                  <option value="1">1 Star</option>
                </select>
              </div>

              @if (filterCategory || filterSentiment || filterRating || filterUrgency || filterSearch) {
                <div class="filter-item reset-item">
                  <button type="button" class="button quiet reset-btn" (click)="resetFilters()">Clear Filters</button>
                </div>
              }
            </div>

            <!-- All Feedback List -->
            @if (isLoadingList) {
              <div class="loading-box">Retrieving feedback records...</div>
            } @else if (allFeedbacks.length === 0) {
              <div class="empty-box">No feedback records match the current criteria.</div>
            } @else {
              <div class="feedback-grid">
                @for (feedback of allFeedbacks; track feedback.id) {
                  <article class="feedback-card" [class.critical-card]="feedback.urgency === 'CRITICAL'">
                    <header class="card-header">
                      <div class="member-info">
                        <strong class="member-name">{{ feedback.memberName }}</strong>
                        <span class="feedback-date">{{ feedback.createdAt | date: 'mediumDate' }}</span>
                      </div>
                      <div class="header-badges">
                        <span class="urgency-pill" [class]="feedback.urgency.toLowerCase()">
                          {{ feedback.urgency === 'CRITICAL' ? '⚠️ URGENT' : feedback.urgency }}
                        </span>
                        <span class="category-pill">{{ getCategoryDisplayName(feedback.category) }}</span>
                        <span class="sentiment-pill" [class]="feedback.sentiment.toLowerCase()">
                          {{ feedback.sentiment }}
                        </span>
                        @if (feedback.confidenceScore) {
                          <span class="confidence-tag" title="Model confidence score">
                            {{ Math.round(feedback.confidenceScore * 100) }}% AI
                          </span>
                        }
                      </div>
                    </header>

                    <div class="star-row">
                      @for (star of [1, 2, 3, 4, 5]; track star) {
                        <span class="star-icon" [class.filled]="star <= feedback.rating">★</span>
                      }
                      <span class="rating-num">({{ feedback.rating }}/5)</span>
                    </div>

                    <!-- AI EXECUTIVE SUMMARY -->
                    @if (feedback.summary) {
                      <div class="ai-summary-box">
                        <span class="ai-tag">✨ Groq AI Executive Summary:</span>
                        <p class="summary-text">{{ feedback.summary }}</p>
                      </div>
                    }

                    <!-- MEMBER VERBATIM QUOTE -->
                    <p class="comment-text">"{{ feedback.comment }}"</p>

                    <!-- AI ACTION RECOMMENDATION -->
                    @if (feedback.actionRecommendation) {
                      <div class="action-recommendation-box">
                        <span class="action-tag">💡 AI Recommended Management Action:</span>
                        <p class="action-text">{{ feedback.actionRecommendation }}</p>
                      </div>
                    }

                    <!-- AI THEMES -->
                    @if (feedback.themes && feedback.themes.length > 0) {
                      <div class="card-themes">
                        @for (theme of feedback.themes; track theme) {
                          <span class="theme-tag">#{{ theme }}</span>
                        }
                      </div>
                    }

                    <footer class="card-footer">
                      <button
                        type="button"
                        class="button quiet card-btn"
                        (click)="reanalyzeFeedback(feedback.id)"
                        [disabled]="reanalyzingId === feedback.id"
                      >
                        {{ reanalyzingId === feedback.id ? 'Analyzing...' : '⟳ Re-analyze AI' }}
                      </button>
                      <button
                        type="button"
                        class="button quiet delete-btn"
                        (click)="deleteFeedback(feedback.id)"
                      >
                        Delete
                      </button>
                    </footer>
                  </article>
                }
              </div>
            }
          </section>
        }

        <!-- ============================================== -->
        <!-- SUBMIT FEEDBACK SECTION (CLEAN MEMBER UX)      -->
        <!-- ============================================== -->
        @if (!isAdmin || showSubmitForm) {
          <section class="submit-feedback-section" aria-labelledby="submit-heading">
            <div class="section-title-row">
              <h2 id="submit-heading">Submit Your Experience</h2>
            </div>

            <form class="feedback-form" (submit)="$event.preventDefault(); submitFeedback()">
              <!-- Category Picker -->
              <div class="form-group">
                <label class="form-label">Category</label>
                <div class="category-picker">
                  @for (cat of categories; track cat.id) {
                    <button
                      type="button"
                      class="cat-picker-btn"
                      [class.selected]="newCategory === cat.id"
                      (click)="newCategory = cat.id"
                    >
                      {{ cat.label }}
                    </button>
                  }
                </div>
              </div>

              <!-- Interactive Star Rating -->
              <div class="form-group">
                <label class="form-label">Rating: <strong>{{ getRatingLabel(newRating) }}</strong></label>
                <div class="star-rating-selector" (mouseleave)="hoverRating = 0">
                  @for (star of [1, 2, 3, 4, 5]; track star) {
                    <button
                      type="button"
                      class="star-btn"
                      [class.active]="star <= (hoverRating || newRating)"
                      (mouseenter)="hoverRating = star"
                      (click)="newRating = star"
                      [attr.aria-label]="star + ' stars'"
                    >
                      ★
                    </button>
                  }
                </div>
              </div>

              <!-- Comment -->
              <div class="form-group">
                <div class="label-with-counter">
                  <label for="feedback-comment" class="form-label">Detailed Comments</label>
                  <span class="char-count">{{ newComment.length }} / 4000</span>
                </div>
                <textarea
                  id="feedback-comment"
                  class="comment-textarea"
                  rows="4"
                  placeholder="Tell us what you enjoyed or how we can improve our gym facilities..."
                  [value]="newComment"
                  (input)="onCommentInput($event)"
                  maxlength="4000"
                  required
                ></textarea>
              </div>

              <div class="form-actions">
                <button
                  type="button"
                  class="button primary submit-btn"
                  (click)="submitFeedback()"
                  [disabled]="isSubmitting || !newComment.trim() || newRating === 0"
                >
                  {{ isSubmitting ? 'Submitting feedback...' : 'Submit Feedback' }}
                </button>
              </div>
            </form>
          </section>
        }

        <!-- ============================================== -->
        <!-- MEMBER PERSONAL FEEDBACK HISTORY (NO AI LEAKS) -->
        <!-- ============================================== -->
        @if (!isAdmin) {
          <section class="my-feedback-section" aria-labelledby="history-heading">
            <div class="section-title-row">
              <h2 id="history-heading">My Feedback History ({{ myFeedbacks.length }})</h2>
              <button class="button quiet refresh-btn" (click)="loadMyFeedback()">⟳ Refresh</button>
            </div>

            @if (isLoadingMyList) {
              <div class="loading-box">Loading your feedback history...</div>
            } @else if (myFeedbacks.length === 0) {
              <div class="empty-box">
                <p>You haven't submitted any feedback yet.</p>
                <p class="empty-sub">Your ratings help us optimize gym equipment, classes, and cleanliness!</p>
              </div>
            } @else {
              <div class="feedback-grid">
                @for (feedback of myFeedbacks; track feedback.id) {
                  <article class="feedback-card">
                    <header class="card-header">
                      <div>
                        <span class="category-pill">{{ getCategoryDisplayName(feedback.category) }}</span>
                        <span class="feedback-date">{{ feedback.createdAt | date: 'mediumDate' }}</span>
                      </div>
                    </header>

                    <div class="star-row">
                      @for (star of [1, 2, 3, 4, 5]; track star) {
                        <span class="star-icon" [class.filled]="star <= feedback.rating">★</span>
                      }
                      <span class="rating-num">({{ feedback.rating }}/5)</span>
                    </div>

                    <p class="comment-text">"{{ feedback.comment }}"</p>
                  </article>
                }
              </div>
            }
          </section>
        }
      </main>
    </div>
  `
})
export class FeedbackComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly feedbackService = inject(FeedbackService);
  readonly Math = Math;

  isAdmin = false;

  // Notification state
  notificationMessage = '';
  isNotificationError = false;

  // Form State
  newRating = 5;
  hoverRating = 0;
  newCategory: FeedbackCategory = 'EQUIPMENT';
  newComment = '';
  isSubmitting = false;
  showSubmitForm = false;

  readonly categories: { id: FeedbackCategory; label: string }[] = [
    { id: 'EQUIPMENT', label: 'Equipment' },
    { id: 'CLEANLINESS', label: 'Cleanliness' },
    { id: 'CLASSES', label: 'Classes' },
    { id: 'STAFF', label: 'Staff' },
    { id: 'SERVICE', label: 'Service' }
  ];

  // Admin Data
  stats: FeedbackDashboardStats | null = null;
  executiveReport: AiExecutiveReport | null = null;
  isLoadingStats = false;
  isRefreshingExecutiveReport = false;
  isReanalyzingAll = false;
  allFeedbacks: FeedbackResponse[] = [];
  isLoadingList = false;
  reanalyzingId: string | null = null;

  // Filters
  filterCategory = '';
  filterSentiment = '';
  filterUrgency = '';
  filterRating = 0;
  filterSearch = '';

  // Member Data
  myFeedbacks: FeedbackResponse[] = [];
  isLoadingMyList = false;

  ngOnInit(): void {
    this.isAdmin = this.auth.hasAnyRole(['ADMIN']);

    if (this.isAdmin) {
      this.loadAdminStats();
      this.applyFilters();
    } else {
      this.loadMyFeedback();
    }
  }

  get initials(): string {
    return this.auth.userName().split(/\s+/).map((part) => part.charAt(0)).join('').slice(0, 2).toUpperCase();
  }

  getRatingLabel(rating: number): string {
    switch (rating) {
      case 1: return '1 - Poor';
      case 2: return '2 - Fair';
      case 3: return '3 - Good';
      case 4: return '4 - Very Good';
      case 5: return '5 - Excellent';
      default: return 'Select rating';
    }
  }

  getCategoryDisplayName(category: FeedbackCategory): string {
    switch (category) {
      case 'CLEANLINESS': return 'Cleanliness';
      case 'EQUIPMENT': return 'Equipment';
      case 'CLASSES': return 'Classes';
      case 'STAFF': return 'Staff';
      case 'SERVICE': return 'Service';
      default: return category;
    }
  }

  getHealthScoreClass(score: number): string {
    if (score >= 80) return 'optimal';
    if (score >= 60) return 'stable';
    return 'critical';
  }

  getRatingCount(star: number): number {
    return this.stats?.ratingDistribution[star] || 0;
  }

  getRatingPercentage(star: number): number {
    if (!this.stats || this.stats.totalFeedbacks === 0) return 0;
    const count = this.getRatingCount(star);
    return Math.round((count / this.stats.totalFeedbacks) * 100);
  }

  loadAdminStats(notifyOnSuccess = false): void {
    this.isLoadingStats = true;
    this.feedbackService.getDashboardStats().subscribe({
      next: (stats) => {
        this.stats = stats;
        if (stats.executiveReport) {
          this.executiveReport = stats.executiveReport;
        }
        this.isLoadingStats = false;
        if (notifyOnSuccess) {
          this.showNotification('Executive analytics & intelligence updated successfully.', false);
        }
      },
      error: () => {
        this.showNotification('Could not load dashboard analytics.', true);
        this.isLoadingStats = false;
      }
    });
  }

  reanalyzeAllFeedbacks(): void {
    if (!confirm('Re-run Groq LLM inference across all stored feedback to generate updated summaries and action plans?')) {
      return;
    }

    this.isReanalyzingAll = true;
    this.feedbackService.reanalyzeAllFeedbacks().subscribe({
      next: (updatedList) => {
        this.isReanalyzingAll = false;
        this.allFeedbacks = updatedList;
        this.showNotification(`Successfully re-analyzed all ${updatedList.length} feedback records with Groq AI.`, false);
        this.loadAdminStats();
      },
      error: () => {
        this.isReanalyzingAll = false;
        this.showNotification('Failed to batch re-analyze feedback records.', true);
      }
    });
  }

  applyFilters(): void {
    this.isLoadingList = true;
    this.feedbackService.getAllFeedback({
      category: this.filterCategory || undefined,
      sentiment: this.filterSentiment || undefined,
      urgency: this.filterUrgency || undefined,
      rating: this.filterRating > 0 ? this.filterRating : undefined,
      search: this.filterSearch || undefined
    }).subscribe({
      next: (list) => {
        this.allFeedbacks = list;
        this.isLoadingList = false;
      },
      error: () => {
        this.showNotification('Could not load feedback records.', true);
        this.isLoadingList = false;
      }
    });
  }

  resetFilters(): void {
    this.filterCategory = '';
    this.filterSentiment = '';
    this.filterUrgency = '';
    this.filterRating = 0;
    this.filterSearch = '';
    this.applyFilters();
  }

  onSearchInput(event: Event): void {
    this.filterSearch = (event.target as HTMLInputElement).value;
    this.applyFilters();
  }

  onUrgencyChange(event: Event): void {
    this.filterUrgency = (event.target as HTMLSelectElement).value;
    this.applyFilters();
  }

  onCategoryChange(event: Event): void {
    this.filterCategory = (event.target as HTMLSelectElement).value;
    this.applyFilters();
  }

  onSentimentChange(event: Event): void {
    this.filterSentiment = (event.target as HTMLSelectElement).value;
    this.applyFilters();
  }

  onRatingChange(event: Event): void {
    this.filterRating = Number((event.target as HTMLSelectElement).value) || 0;
    this.applyFilters();
  }

  onCommentInput(event: Event): void {
    this.newComment = (event.target as HTMLTextAreaElement).value;
  }

  filterByTheme(theme: string): void {
    this.filterSearch = theme;
    this.applyFilters();
  }

  filterByCategory(category: string): void {
    this.filterCategory = category;
    this.applyFilters();
  }

  loadMyFeedback(): void {
    this.isLoadingMyList = true;
    this.feedbackService.getMyFeedback().subscribe({
      next: (feedbacks) => {
        this.myFeedbacks = feedbacks;
        this.isLoadingMyList = false;
      },
      error: () => {
        this.showNotification('Could not load your feedback history.', true);
        this.isLoadingMyList = false;
      }
    });
  }

  submitFeedback(): void {
    if (!this.newComment.trim() || this.newRating < 1) return;

    this.isSubmitting = true;
    const req: FeedbackSubmitRequest = {
      rating: this.newRating,
      category: this.newCategory,
      comment: this.newComment.trim()
    };

    this.feedbackService.submitFeedback(req).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.newComment = '';
        this.newRating = 5;
        this.showNotification('Thank you! Your feedback has been submitted successfully.', false);

        if (this.isAdmin) {
          this.loadAdminStats();
          this.applyFilters();
        } else {
          this.loadMyFeedback();
        }
      },
      error: () => {
        this.isSubmitting = false;
        this.showNotification('Failed to submit feedback. Please try again.', true);
      }
    });
  }

  reanalyzeFeedback(id: string): void {
    this.reanalyzingId = id;
    this.feedbackService.reanalyzeFeedback(id).subscribe({
      next: () => {
        this.reanalyzingId = null;
        this.showNotification('Feedback re-analyzed successfully by Groq AI.', false);
        this.applyFilters();
        this.loadAdminStats();
      },
      error: () => {
        this.reanalyzingId = null;
        this.showNotification('Could not re-analyze feedback.', true);
      }
    });
  }

  deleteFeedback(id: string): void {
    if (!confirm('Are you sure you want to permanently delete this feedback?')) return;

    this.feedbackService.deleteFeedback(id).subscribe({
      next: () => {
        this.showNotification('Feedback deleted successfully.', false);
        this.applyFilters();
        this.loadAdminStats();
      },
      error: () => {
        this.showNotification('Could not delete feedback.', true);
      }
    });
  }

  private showNotification(msg: string, isError: boolean): void {
    this.notificationMessage = msg;
    this.isNotificationError = isError;
    setTimeout(() => {
      if (this.notificationMessage === msg) {
        this.notificationMessage = '';
      }
    }, 6000);
  }
}
