import { HttpClient } from '@angular/common/http';
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
}
