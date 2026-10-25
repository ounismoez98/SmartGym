import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface MembershipIdentity {
  subject: string;
  displayName: string;
  roles: string[];
  memberProfileId: string | null;
  employeeProfileId: string | null;
  profileStatus: 'COMPLETE' | 'ONBOARDING_REQUIRED' | 'NO_APPLICATION_ROLE';
  onboardingRequired: boolean;
}

export interface AssignedMember {
  memberProfileId: string;
  displayName: string;
}

export interface ApiPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MemberProfile {
  id: string;
  keycloakUserId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  birthDate: string | null;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface SubscriptionPlan {
  id: string;
  code: string;
  name: string;
  conditions: string;
  durationDays: number;
  priceCents: number;
  currency: string;
  active: boolean;
}

export interface MemberSubscription {
  id: string;
  memberId: string;
  planId: string;
  startsAt: string;
  endsAt: string;
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED';
  cancelReason: string | null;
}

export interface Payment {
  id: string;
  memberId: string;
  subscriptionId: string | null;
  amountCents: number;
  currency: string;
  method: 'CASH' | 'CARD' | 'TRANSFER';
  status: 'RECORDED' | 'CORRECTED';
  correctsPaymentId: string | null;
  reason: string | null;
  paidAt: string;
  corrections: Payment[];
}

export interface EmployeeProfile {
  id: string;
  keycloakUserId: string;
  firstName: string;
  lastName: string;
  email: string;
  jobTitle: string;
  hireDate: string | null;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface ReminderDraft {
  id: string;
  memberId: string;
  subscriptionId: string;
  subject: string;
  body: string;
  status: 'DRAFT' | 'SENT' | 'REJECTED';
  createdAt: string;
  sentAt: string | null;
}

export interface ApiMessage {
  answer: string;
}

@Injectable({ providedIn: 'root' })
export class MembershipApiService {
  private readonly http = inject(HttpClient);

  getIdentity() {
    return this.http.get<MembershipIdentity>('/api/membership/me');
  }

  getAdminOperations() {
    return this.http.get<{ status: string; enabledModuleAreas: string[] }>(
      '/api/membership/admin/operations'
    );
  }

  getAssignedMembers() {
    return this.http.get<AssignedMember[]>('/api/membership/coach/assigned-members');
  }

  getMember(id: string) {
    return this.http.get<MemberProfile>(`/api/membership/members/${id}`);
  }

  listMembers(query: { query?: string; status?: string; page?: number; size?: number } = {}) {
    return this.http.get<ApiPage<MemberProfile>>('/api/membership/members', {
      params: this.params(query)
    });
  }

  createMember(request: {
    keycloakUserId: string;
    firstName: string;
    lastName: string;
    email: string;
    phone?: string;
    birthDate?: string;
  }) {
    return this.http.post<MemberProfile>('/api/membership/members', request);
  }

  updateMember(id: string, request: Partial<Pick<MemberProfile, 'firstName' | 'lastName' | 'email' | 'phone' | 'birthDate'>>) {
    return this.http.put<MemberProfile>(`/api/membership/members/${id}`, request);
  }

  deactivateMember(id: string) {
    return this.http.post<MemberProfile>(`/api/membership/members/${id}/deactivate`, {});
  }

  listPlans(query: { query?: string; active?: boolean; page?: number; size?: number } = {}) {
    return this.http.get<ApiPage<SubscriptionPlan>>('/api/membership/plans', {
      params: this.params(query)
    });
  }

  createPlan(request: {
    code: string;
    name: string;
    conditions: string;
    durationDays: number;
    priceCents: number;
    currency: string;
  }) {
    return this.http.post<SubscriptionPlan>('/api/membership/plans', request);
  }

  updatePlan(id: string, request: Pick<SubscriptionPlan, 'name' | 'conditions' | 'durationDays' | 'priceCents' | 'currency' | 'active'>) {
    return this.http.put<SubscriptionPlan>(`/api/membership/plans/${id}`, request);
  }

  listSubscriptions(query: { memberId?: string; status?: string; planId?: string; page?: number; size?: number } = {}) {
    return this.http.get<ApiPage<MemberSubscription>>('/api/membership/subscriptions', {
      params: this.params(query)
    });
  }

  createSubscription(request: { memberId: string; planId: string }) {
    return this.http.post<MemberSubscription>('/api/membership/subscriptions', request);
  }

  changeSubscriptionPlan(id: string, planId: string) {
    return this.http.put<MemberSubscription>(`/api/membership/subscriptions/${id}`, { planId });
  }

  cancelSubscription(id: string, reason: string) {
    return this.http.post<MemberSubscription>(`/api/membership/subscriptions/${id}/cancel`, { reason });
  }

  listPayments(query: {
    memberId?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  } = {}) {
    return this.http.get<ApiPage<Payment>>('/api/membership/payments', {
      params: this.params(query)
    });
  }

  createPayment(request: {
    memberId: string;
    subscriptionId?: string;
    amountCents: number;
    currency?: string;
    method: Payment['method'];
    paidAt?: string;
  }) {
    return this.http.post<Payment>('/api/membership/payments', request);
  }

  correctPayment(id: string, amountCents: number, reason: string) {
    return this.http.post<Payment>(`/api/membership/payments/${id}/corrections`, { amountCents, reason });
  }

  paymentTotal(memberId: string) {
    return this.http.get<{ totalPaidCents: number }>(`/api/membership/members/${memberId}/payments/total`);
  }

  listEmployees(query: { query?: string; status?: string; jobTitle?: string; page?: number; size?: number } = {}) {
    return this.http.get<ApiPage<EmployeeProfile>>('/api/membership/employees', {
      params: this.params(query)
    });
  }

  createEmployee(request: {
    keycloakUserId: string;
    firstName: string;
    lastName: string;
    email: string;
    jobTitle: string;
    hireDate?: string;
  }) {
    return this.http.post<EmployeeProfile>('/api/membership/employees', request);
  }

  updateEmployee(id: string, request: {
    firstName: string;
    lastName: string;
    email: string;
    jobTitle: string;
    hireDate?: string;
  }) {
    return this.http.put<EmployeeProfile>(`/api/membership/employees/${id}`, request);
  }

  deactivateEmployee(id: string) {
    return this.http.post<EmployeeProfile>(`/api/membership/employees/${id}/deactivate`, {});
  }

  assignCoach(coachId: string, memberId: string) {
    return this.http.post<void>(`/api/membership/employees/${coachId}/members/${memberId}`, {});
  }

  unassignCoach(coachId: string, memberId: string) {
    return this.http.post<void>(`/api/membership/employees/${coachId}/members/${memberId}/unassign`, {});
  }

  assignedMembers(coachId: string) {
    return this.http.get<MemberProfile[]>(`/api/membership/employees/${coachId}/members`);
  }

  explain(question: string, planId?: string) {
    return this.http.post<ApiMessage>('/api/membership/assistant/explain', { question, planId: planId || null });
  }

  renewalSummary(days: number) {
    return this.http.get<ApiMessage>('/api/membership/assistant/renewals', {
      params: this.params({ days })
    });
  }

  createReminder(subscriptionId: string) {
    return this.http.post<ReminderDraft>('/api/membership/assistant/reminders', { subscriptionId });
  }

  updateReminder(id: string, subject: string, body: string) {
    return this.http.put<ReminderDraft>(`/api/membership/assistant/reminders/${id}`, { subject, body });
  }

  confirmReminder(id: string) {
    return this.http.post<ReminderDraft>(`/api/membership/assistant/reminders/${id}/confirm`, {});
  }

  rejectReminder(id: string) {
    return this.http.post<ReminderDraft>(`/api/membership/assistant/reminders/${id}/reject`, {});
  }

  private params(query: Record<string, string | number | boolean | undefined>) {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== '') params = params.set(key, String(value));
    }
    return params;
  }
}
