import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MembershipApiService } from './membership-api.service';

describe('MembershipApiService', () => {
  let service: MembershipApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(MembershipApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the current identity from the membership API', () => {
    service.getIdentity().subscribe((identity) => {
      expect(identity.memberProfileId).toBe('member-1');
    });

    const request = http.expectOne('/api/membership/me');
    expect(request.request.method).toBe('GET');
    request.flush({
      subject: 'member-sub',
      displayName: 'Gym member',
      roles: ['MEMBER'],
      memberProfileId: 'member-1',
      employeeProfileId: null,
      profileStatus: 'COMPLETE',
      onboardingRequired: false
    });
  });

  it('posts payment corrections to the append-only correction endpoint', () => {
    service.correctPayment('payment-1', 2500, 'Corrected amount').subscribe((payment) => {
      expect(payment.correctsPaymentId).toBe('payment-1');
    });

    const request = http.expectOne('/api/membership/payments/payment-1/corrections');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ amountCents: 2500, reason: 'Corrected amount' });
    request.flush({
      id: 'payment-2',
      memberId: 'member-1',
      subscriptionId: 'subscription-1',
      amountCents: 2500,
      currency: 'TND',
      method: 'CARD',
      status: 'RECORDED',
      correctsPaymentId: 'payment-1',
      reason: 'Corrected amount',
      paidAt: '2026-01-01T00:00:00Z',
      corrections: []
    });
  });
});
