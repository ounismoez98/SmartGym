import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from './auth.service';
import {
  EmployeeProfile,
  MemberProfile,
  MemberSubscription,
  MembershipApiService,
  MembershipIdentity,
  Payment,
  ReminderDraft,
  SubscriptionPlan
} from './membership-api.service';

type MembershipTab = 'members' | 'subscriptions' | 'payments' | 'employees' | 'assistant' | 'profile';

@Component({
  selector: 'sg-membership',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './membership.component.html'
})
export class MembershipComponent implements OnInit {
  private readonly api = inject(MembershipApiService);
  private readonly auth = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);

  readonly memberSearchForm = this.formBuilder.nonNullable.group({
    query: '',
    status: ''
  });
  readonly memberCreateForm = this.formBuilder.nonNullable.group({
    keycloakUserId: ['', [Validators.required, Validators.pattern(/^[0-9a-f-]{36}$/i)]],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: '',
    birthDate: ''
  });
  readonly memberEditForm = this.formBuilder.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: '',
    birthDate: ''
  });
  readonly planCreateForm = this.formBuilder.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    conditions: ['', Validators.required],
    durationDays: [30, [Validators.required, Validators.min(1)]],
    priceCents: [0, [Validators.required, Validators.min(0)]],
    currency: ['TND', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]]
  });
  readonly planEditForm = this.formBuilder.nonNullable.group({
    name: ['', Validators.required],
    conditions: ['', Validators.required],
    durationDays: [30, [Validators.required, Validators.min(1)]],
    priceCents: [0, [Validators.required, Validators.min(0)]],
    currency: ['TND', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    active: [true]
  });
  readonly subscriptionCreateForm = this.formBuilder.nonNullable.group({
    memberId: ['', Validators.required],
    planId: ['', Validators.required]
  });
  readonly subscriptionPlanForm = this.formBuilder.nonNullable.group({ planId: ['', Validators.required] });
  readonly cancelForm = this.formBuilder.nonNullable.group({ reason: ['', Validators.required] });
  readonly paymentCreateForm = this.formBuilder.nonNullable.group({
    memberId: ['', Validators.required],
    subscriptionId: '',
    amountCents: [1, [Validators.required, Validators.min(1)]],
    currency: ['TND', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    method: ['CASH' as Payment['method'], Validators.required],
    paidAt: ['']
  });
  readonly paymentCorrectionForm = this.formBuilder.nonNullable.group({
    amountCents: [1, [Validators.required, Validators.min(1)]],
    reason: ['', Validators.required]
  });
  readonly employeeCreateForm = this.formBuilder.nonNullable.group({
    keycloakUserId: ['', [Validators.required, Validators.pattern(/^[0-9a-f-]{36}$/i)]],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    jobTitle: ['', Validators.required],
    hireDate: ''
  });
  readonly employeeEditForm = this.formBuilder.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    jobTitle: ['', Validators.required],
    hireDate: ''
  });
  readonly assignmentForm = this.formBuilder.nonNullable.group({
    coachId: ['', Validators.required],
    memberId: ['', Validators.required]
  });
  readonly explainForm = this.formBuilder.nonNullable.group({
    question: ['', Validators.required],
    planId: ''
  });
  readonly renewalForm = this.formBuilder.nonNullable.group({
    days: [7, [Validators.required, Validators.min(1), Validators.max(365)]]
  });
  readonly reminderCreateForm = this.formBuilder.nonNullable.group({
    subscriptionId: ['', Validators.required]
  });
  readonly reminderEditForm = this.formBuilder.nonNullable.group({
    subject: ['', Validators.required],
    body: ['', Validators.required]
  });

  identity: MembershipIdentity | null = null;
  members: MemberProfile[] = [];
  plans: SubscriptionPlan[] = [];
  subscriptions: MemberSubscription[] = [];
  payments: Payment[] = [];
  employees: EmployeeProfile[] = [];
  assignedMembers: MemberProfile[] = [];
  selectedMember: MemberProfile | null = null;
  selectedPlan: SubscriptionPlan | null = null;
  selectedSubscription: MemberSubscription | null = null;
  selectedPayment: Payment | null = null;
  selectedEmployee: EmployeeProfile | null = null;
  reminder: ReminderDraft | null = null;
  assistantAnswer = '';
  renewalsAnswer = '';
  totalPaidCents: number | null = null;
  activeTab: MembershipTab = 'members';
  loading = false;
  errorMessage = '';
  errorDetails = '';
  successMessage = '';
  memberPage = 0;
  memberTotalPages = 0;

  get isAdmin(): boolean {
    return this.auth.hasAnyRole(['ADMIN']);
  }

  get isMember(): boolean {
    return this.auth.hasAnyRole(['MEMBER']);
  }

  get isCoach(): boolean {
    return this.auth.hasAnyRole(['COACH']);
  }

  ngOnInit(): void {
    this.loading = true;
    this.api.getIdentity().subscribe({
      next: (identity) => {
        this.identity = identity;
        this.loadInitialData();
      },
      error: (error: unknown) => this.fail(error)
    });
  }

  selectTab(tab: MembershipTab): void {
    this.activeTab = tab;
    this.errorMessage = '';
    this.successMessage = '';
  }

  searchMembers(pageNumber = 0): void {
    if (!this.isAdmin && !this.isCoach) return;
    const { query, status } = this.memberSearchForm.getRawValue();
    this.memberPage = pageNumber;
    this.loading = true;
    this.api.listMembers({ query, status: status || undefined, page: pageNumber, size: 20 }).subscribe({
      next: (page) => this.loaded(() => {
        this.members = page.content;
        this.memberPage = page.page;
        this.memberTotalPages = page.totalPages;
      }),
      error: (error: unknown) => this.fail(error)
    });
  }

  createMember(): void {
    if (!this.isAdmin || this.memberCreateForm.invalid) return;
    const value = this.memberCreateForm.getRawValue();
    this.mutate(this.api.createMember({
      ...value,
      phone: value.phone || undefined,
      birthDate: value.birthDate || undefined
    }), 'Member created.', () => {
      this.memberCreateForm.reset({ keycloakUserId: '', firstName: '', lastName: '', email: '', phone: '', birthDate: '' });
      this.searchMembers();
    });
  }

  selectMember(member: MemberProfile): void {
    this.selectedMember = member;
    this.memberEditForm.reset({
      firstName: member.firstName,
      lastName: member.lastName,
      email: member.email,
      phone: member.phone ?? '',
      birthDate: member.birthDate ?? ''
    });
  }

  saveMember(): void {
    if (!this.selectedMember || this.memberEditForm.invalid) return;
    const value = this.memberEditForm.getRawValue();
    const request = this.isAdmin
      ? { ...value, phone: value.phone || undefined, birthDate: value.birthDate || undefined }
      : { email: value.email, phone: value.phone || undefined };
    this.mutate(this.api.updateMember(this.selectedMember.id, request), 'Member profile updated.', () => {
      this.selectedMember = null;
      if (this.isAdmin || this.isCoach) this.searchMembers();
      else this.loadMyProfile();
    });
  }

  deactivateMember(member: MemberProfile): void {
    if (!this.isAdmin || !window.confirm(`Deactivate ${member.firstName} ${member.lastName}?`)) return;
    this.mutate(this.api.deactivateMember(member.id), 'Member deactivated.', () => this.searchMembers());
  }

  createPlan(): void {
    if (!this.isAdmin || this.planCreateForm.invalid) return;
    this.mutate(this.api.createPlan(this.planCreateForm.getRawValue()), 'Plan created.', () => {
      this.planCreateForm.reset({ code: '', name: '', conditions: '', durationDays: 30, priceCents: 0, currency: 'TND' });
      this.loadPlans();
    });
  }

  selectPlan(plan: SubscriptionPlan): void {
    this.selectedPlan = plan;
    this.planEditForm.reset({
      name: plan.name,
      conditions: plan.conditions,
      durationDays: plan.durationDays,
      priceCents: plan.priceCents,
      currency: plan.currency,
      active: plan.active
    });
  }

  savePlan(): void {
    if (!this.isAdmin || !this.selectedPlan || this.planEditForm.invalid) return;
    this.mutate(this.api.updatePlan(this.selectedPlan.id, this.planEditForm.getRawValue()), 'Plan updated.', () => {
      this.selectedPlan = null;
      this.loadPlans();
    });
  }

  createSubscription(): void {
    if (!this.isAdmin || this.subscriptionCreateForm.invalid) return;
    this.mutate(this.api.createSubscription(this.subscriptionCreateForm.getRawValue()), 'Subscription created.', () => {
      this.subscriptionCreateForm.reset({ memberId: '', planId: '' });
      this.loadSubscriptions();
    });
  }

  chooseSubscriptionPlan(subscription: MemberSubscription): void {
    this.selectedSubscription = subscription;
    this.subscriptionPlanForm.reset({ planId: subscription.planId });
  }

  changeSubscriptionPlan(): void {
    if (!this.selectedSubscription || this.subscriptionPlanForm.invalid) return;
    const { planId } = this.subscriptionPlanForm.getRawValue();
    this.mutate(this.api.changeSubscriptionPlan(this.selectedSubscription.id, planId), 'Subscription plan changed.', () => {
      this.selectedSubscription = null;
      this.loadSubscriptions();
    });
  }

  cancelSubscription(subscription: MemberSubscription): void {
    if (this.cancelForm.invalid) return;
    const { reason } = this.cancelForm.getRawValue();
    this.mutate(this.api.cancelSubscription(subscription.id, reason), 'Subscription cancelled.', () => {
      this.cancelForm.reset({ reason: '' });
      this.loadSubscriptions();
    });
  }

  createPayment(): void {
    if (!this.isAdmin || this.paymentCreateForm.invalid) return;
    const value = this.paymentCreateForm.getRawValue();
    this.mutate(this.api.createPayment({
      ...value,
      subscriptionId: value.subscriptionId || undefined,
      paidAt: value.paidAt ? new Date(value.paidAt).toISOString() : undefined
    }), 'Payment recorded.', () => {
      this.paymentCreateForm.reset({
        memberId: '', subscriptionId: '', amountCents: 1, currency: 'TND', method: 'CASH', paidAt: ''
      });
      this.loadPayments();
    });
  }

  openPayment(payment: Payment, forCorrection = false): void {
    this.loading = true;
    this.errorMessage = '';
    this.api.getPayment(payment.id).subscribe({
      next: (details) => this.loaded(() => {
        this.selectedPayment = details;
        if (forCorrection) {
          this.paymentCorrectionForm.reset({ amountCents: details.amountCents, reason: '' });
        }
      }),
      error: (error: unknown) => this.fail(error)
    });
  }

  choosePayment(payment: Payment): void {
    this.openPayment(payment, true);
  }

  correctPayment(): void {
    if (!this.isAdmin || !this.selectedPayment || this.paymentCorrectionForm.invalid) return;
    const { amountCents, reason } = this.paymentCorrectionForm.getRawValue();
    this.mutate(this.api.correctPayment(this.selectedPayment.id, amountCents, reason), 'Payment correction recorded.', () => {
      const correctedPayment = this.selectedPayment;
      this.loadPayments();
      if (correctedPayment) this.openPayment(correctedPayment);
    });
  }

  createEmployee(): void {
    if (!this.isAdmin || this.employeeCreateForm.invalid) return;
    const value = this.employeeCreateForm.getRawValue();
    this.mutate(this.api.createEmployee({ ...value, hireDate: value.hireDate || undefined }), 'Employee created.', () => {
      this.employeeCreateForm.reset({
        keycloakUserId: '', firstName: '', lastName: '', email: '', jobTitle: '', hireDate: ''
      });
      this.loadEmployees();
    });
  }

  selectEmployee(employee: EmployeeProfile): void {
    this.selectedEmployee = employee;
    this.employeeEditForm.reset({
      firstName: employee.firstName,
      lastName: employee.lastName,
      email: employee.email,
      jobTitle: employee.jobTitle,
      hireDate: employee.hireDate ?? ''
    });
    this.assignmentForm.patchValue({ coachId: employee.id });
    this.loadAssignedMembers(employee.id);
  }

  saveEmployee(): void {
    if (!this.isAdmin || !this.selectedEmployee || this.employeeEditForm.invalid) return;
    const value = this.employeeEditForm.getRawValue();
    this.mutate(this.api.updateEmployee(this.selectedEmployee.id, {
      ...value,
      hireDate: value.hireDate || undefined
    }), 'Employee updated.', () => {
      this.selectedEmployee = null;
      this.loadEmployees();
    });
  }

  deactivateEmployee(employee: EmployeeProfile): void {
    if (!this.isAdmin || !window.confirm(`Deactivate ${employee.firstName} ${employee.lastName}?`)) return;
    this.mutate(this.api.deactivateEmployee(employee.id), 'Employee deactivated.', () => this.loadEmployees());
  }

  assignMember(): void {
    if (!this.isAdmin || this.assignmentForm.invalid) return;
    const { coachId, memberId } = this.assignmentForm.getRawValue();
    this.mutate(this.api.assignCoach(coachId, memberId), 'Coach assigned to member.', () => {
      this.assignmentForm.patchValue({ memberId: '' });
      this.loadAssignedMembers(coachId);
    });
  }

  unassignMember(member: MemberProfile): void {
    const coachId = this.selectedEmployee?.id;
    if (!this.isAdmin || !coachId) return;
    this.mutate(this.api.unassignCoach(coachId, member.id), 'Coach assignment ended.', () => {
      this.loadAssignedMembers(coachId);
    });
  }

  askAssistant(): void {
    if (!this.isAdmin || this.explainForm.invalid) return;
    const { question, planId } = this.explainForm.getRawValue();
    this.mutate(this.api.explain(question, planId), 'Answer received.', (result) => {
      this.assistantAnswer = result.answer;
    });
  }

  summarizeRenewals(): void {
    if (!this.isAdmin || this.renewalForm.invalid) return;
    this.mutate(this.api.renewalSummary(this.renewalForm.getRawValue().days), 'Renewal summary received.', (result) => {
      this.renewalsAnswer = result.answer;
    });
  }

  createReminder(): void {
    if (!this.isAdmin || this.reminderCreateForm.invalid) return;
    const { subscriptionId } = this.reminderCreateForm.getRawValue();
    this.mutate(this.api.createReminder(subscriptionId), 'Reminder draft created.', (draft) => {
      this.reminder = draft;
      this.reminderEditForm.reset({ subject: draft.subject, body: draft.body });
    });
  }

  saveReminder(): void {
    if (!this.reminder || this.reminder.status !== 'DRAFT' || this.reminderEditForm.invalid) return;
    const { subject, body } = this.reminderEditForm.getRawValue();
    this.mutate(this.api.updateReminder(this.reminder.id, subject, body), 'Draft saved.', (draft) => {
      this.reminder = draft;
    });
  }

  confirmReminder(): void {
    if (!this.reminder || this.reminder.status !== 'DRAFT') return;
    if (!window.confirm('Send this reminder to the member email on file?')) return;
    this.mutate(this.api.confirmReminder(this.reminder.id), 'Reminder sent.', (draft) => this.reminder = draft);
  }

  rejectReminder(): void {
    if (!this.reminder || this.reminder.status !== 'DRAFT') return;
    this.mutate(this.api.rejectReminder(this.reminder.id), 'Reminder rejected.', (draft) => this.reminder = draft);
  }

  formatMoney(cents: number, currency: string): string {
    return `${(cents / 100).toFixed(2)} ${currency}`;
  }

  planName(planId: string): string {
    return this.plans.find((plan) => plan.id === planId)?.name ?? 'Unknown plan';
  }

  memberName(memberId: string): string {
    const member = this.members.find((entry) => entry.id === memberId);
    return member ? `${member.firstName} ${member.lastName}` : memberId;
  }

  private loadInitialData(): void {
    if (this.isAdmin) {
      forkJoin({
        members: this.api.listMembers({ page: 0, size: 20 }),
        plans: this.api.listPlans({ page: 0, size: 50 }),
        subscriptions: this.api.listSubscriptions({ page: 0, size: 20 }),
        payments: this.api.listPayments({ page: 0, size: 20 }),
        employees: this.api.listEmployees({ page: 0, size: 20 })
      }).subscribe({
        next: (data) => this.loaded(() => {
          this.members = data.members.content;
          this.plans = data.plans.content;
          this.subscriptions = data.subscriptions.content;
          this.payments = data.payments.content;
          this.employees = data.employees.content;
        }),
        error: (error: unknown) => this.fail(error)
      });
      return;
    }
    const memberId = this.identity?.memberProfileId;
    const plans = this.api.listPlans({ active: true, page: 0, size: 50 });
    if (this.isMember && memberId) {
      forkJoin({
        plans,
        profile: this.api.getMember(memberId),
        subscriptions: this.api.listSubscriptions({ memberId, page: 0, size: 20 }),
        payments: this.api.listPayments({ memberId, page: 0, size: 20 }),
        total: this.api.paymentTotal(memberId)
      }).subscribe({
        next: (data) => this.loaded(() => {
          this.plans = data.plans.content;
          this.members = [data.profile];
          this.subscriptions = data.subscriptions.content;
          this.payments = data.payments.content;
          this.totalPaidCents = data.total.totalPaidCents;
          this.memberTotalPages = 0;
          this.activeTab = 'profile';
        }),
        error: (error: unknown) => this.fail(error)
      });
      return;
    }
    if (this.isMember) {
      plans.subscribe({
        next: (page) => this.loaded(() => this.plans = page.content),
        error: (error: unknown) => this.fail(error)
      });
      return;
    }
    forkJoin({
      plans,
      members: this.api.listMembers({ page: 0, size: 20 })
    }).subscribe({
      next: (data) => this.loaded(() => {
        this.plans = data.plans.content;
        this.members = data.members.content;
        this.memberPage = data.members.page;
        this.memberTotalPages = data.members.totalPages;
      }),
      error: (error: unknown) => this.fail(error)
    });
  }

  private loadMyProfile(): void {
    const memberId = this.identity?.memberProfileId;
    if (!memberId) return;
    this.api.getMember(memberId).subscribe({
      next: (member) => this.loaded(() => this.members = [member]),
      error: (error: unknown) => this.fail(error)
    });
  }

  private loadPlans(): void {
    this.api.listPlans({ page: 0, size: 50 }).subscribe({
      next: (page) => this.loaded(() => this.plans = page.content),
      error: (error: unknown) => this.fail(error)
    });
  }

  private loadSubscriptions(): void {
    const memberId = this.isMember ? this.identity?.memberProfileId ?? undefined : undefined;
    this.api.listSubscriptions({ memberId, page: 0, size: 20 }).subscribe({
      next: (page) => this.loaded(() => this.subscriptions = page.content),
      error: (error: unknown) => this.fail(error)
    });
  }

  private loadPayments(): void {
    const memberId = this.isMember ? this.identity?.memberProfileId ?? undefined : undefined;
    this.api.listPayments({ memberId, page: 0, size: 20 }).subscribe({
      next: (page) => this.loaded(() => this.payments = page.content),
      error: (error: unknown) => this.fail(error)
    });
  }

  private loadEmployees(): void {
    this.api.listEmployees({ page: 0, size: 20 }).subscribe({
      next: (page) => this.loaded(() => this.employees = page.content),
      error: (error: unknown) => this.fail(error)
    });
  }

  private loadAssignedMembers(coachId: string): void {
    this.api.assignedMembers(coachId).subscribe({
      next: (members) => this.loaded(() => this.assignedMembers = members),
      error: (error: unknown) => this.fail(error)
    });
  }

  private mutate<T>(request: import('rxjs').Observable<T>, success: string, after?: (value: T) => void): void {
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    request.subscribe({
      next: (value) => {
        after?.(value);
        this.successMessage = success;
        this.loading = false;
      },
      error: (error: unknown) => this.fail(error)
    });
  }

  private loaded(update: () => void): void {
    update();
    this.loading = false;
  }

  private fail(error: unknown): void {
    this.loading = false;
    if (error instanceof HttpErrorResponse) {
      const response = error.error as { code?: unknown; message?: unknown; details?: unknown } | null;
      const code = typeof response?.code === 'string' ? `${response.code}: ` : '';
      const message = typeof response?.message === 'string'
        ? response.message
        : `Request failed (${error.status}).`;
      this.errorMessage = `${code}${message}`;
      this.errorDetails = response?.details && typeof response.details === 'object'
        ? JSON.stringify(response.details)
        : '';
      return;
    }
    this.errorMessage = 'An unexpected error occurred. Please try again.';
    this.errorDetails = '';
  }
}
