import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AuthService } from './auth.service';
import { AssignedMember, MembershipApiService, MembershipIdentity } from './membership-api.service';

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="app-shell">
      <header class="topbar">
        <a class="brand" routerLink="/">SMART<span>GYM</span></a>
        <div class="topbar-user">
          <span class="avatar">{{ initials }}</span>
          <span>{{ auth.userName() }}</span>
          <button class="button quiet" type="button" (click)="signOut()">Log out</button>
        </div>
      </header>
      <main class="content">
        <p class="eyebrow">YOUR TRAINING SPACE</p>
        <h1>Welcome, {{ auth.userName() }}</h1>
        <p class="lead">Your gym, your progress, all in one place.</p>

        <section class="account-card" aria-labelledby="account-title">
          <div>
            <p class="eyebrow">ACCOUNT &amp; SESSION</p>
            <h2 id="account-title">Signed in</h2>
            <p>Application roles: <strong>{{ auth.roles().join(', ') || 'None assigned' }}</strong></p>
          </div>
          @if (identityError) {
            <p class="notice error" role="alert">{{ identityError }}</p>
          } @else if (identity) {
            <div class="identity-details">
              <p>Membership API: <strong>Connected</strong></p>
              <p>Profile status: <strong>{{ identity.profileStatus.replaceAll('_', ' ') }}</strong></p>
              @if (identity.memberProfileId) { <p>Member profile: <code>{{ identity.memberProfileId }}</code></p> }
              @if (identity.employeeProfileId) { <p>Employee profile: <code>{{ identity.employeeProfileId }}</code></p> }
            </div>
          } @else {
            <p>Loading the authenticated membership profile…</p>
          }
        </section>

        <section class="module-section">
          <div class="section-heading">
            <div><p class="eyebrow">YOUR WORKSPACE</p><h2>Explore Smart Gym</h2></div>
          </div>
          <nav class="module-grid" aria-label="Gym modules">
            @for (module of visibleModules; track module.route) {
              <a class="module-card" [routerLink]="module.route">
                <span class="module-code">{{ module.code }}</span>
                <h3>{{ module.title }}</h3>
                <p>{{ module.description }}</p>
                <span class="card-link">Open area <span aria-hidden="true">→</span></span>
              </a>
            }
          </nav>
        </section>

        @if (auth.hasAnyRole(['ADMIN'])) {
          <section class="admin-note">
            <strong>Administration &amp; operations</strong>
            <p>Your ADMIN role enables protected administration and operations.</p>
            <button class="button quiet" type="button" (click)="checkAdminOperations()">Check operations access</button>
            @if (operationsStatus) {
              <p>Operations status: <strong>{{ operationsStatus.status }}</strong></p>
              <p>Enabled areas: {{ operationsStatus.enabledModuleAreas.join(', ') }}</p>
            }
            @if (operationsError) { <p class="notice error" role="alert">{{ operationsError }}</p> }
          </section>
        }
        @if (auth.hasAnyRole(['COACH'])) {
          <section class="admin-note">
            <strong>Coach workspace</strong>
            <p>Training and assigned-member records are available according to your server-side assignments.</p>
            <button class="button quiet" type="button" (click)="loadAssignedMembers()">View assigned members</button>
            @if (assignedMembers) {
              <ul>
                @for (member of assignedMembers; track member.memberProfileId) {
                  <li>{{ member.displayName }} — <code>{{ member.memberProfileId }}</code></li>
                }
                @if (assignedMembers.length === 0) { <li>No members are currently assigned.</li> }
              </ul>
            }
            @if (coachError) { <p class="notice error" role="alert">{{ coachError }}</p> }
          </section>
        }
        @if (auth.hasAnyRole(['MEMBER'])) {
          <section class="admin-note">
            <strong>Your member account</strong>
            <p>Membership reservations and your training and nutrition areas are linked to your own profile.</p>
          </section>
        }
      </main>
    </div>
  `
})
export class DashboardComponent {
  readonly auth = inject(AuthService);
  private readonly membershipApi = inject(MembershipApiService);
  identity: MembershipIdentity | null = null;
  identityError = '';
  operationsStatus: { status: string; enabledModuleAreas: string[] } | null = null;
  operationsError = '';
  assignedMembers: AssignedMember[] | null = null;
  coachError = '';

  readonly visibleModules = [
    { code: 'M1', title: 'Membership', description: 'Your membership, reservations, and assigned members.', route: '/membership', roles: ['ADMIN', 'COACH', 'MEMBER'] },
    { code: 'M2', title: 'Courses', description: 'Courses and class schedules for your gym.', route: '/courses', roles: ['ADMIN', 'COACH', 'MEMBER'] },
    { code: 'M3', title: 'Training', description: 'Training plans and coaching workspace.', route: '/training', roles: ['ADMIN', 'COACH', 'MEMBER'] },
    { code: 'M4', title: 'Nutrition', description: 'Nutrition plans and member resources.', route: '/nutrition', roles: ['ADMIN', 'MEMBER'] }
  ].filter((module) => this.auth.hasAnyRole(module.roles));

  constructor() {
    this.membershipApi.getIdentity().pipe(
      catchError((error: unknown) => {
        this.identityError = error instanceof HttpErrorResponse && error.status === 403
          ? 'Your account is signed in, but is not permitted to read its membership profile.'
          : 'Could not load your membership profile. Check that the API is running and sign in again if your session expired.';
        return of(null);
      })
    ).subscribe((identity) => {
      if (identity) this.identity = identity;
    });
  }

  get initials(): string {
    return this.auth.userName().split(/\s+/).map((part) => part.charAt(0)).join('').slice(0, 2).toUpperCase();
  }

  checkAdminOperations(): void {
    this.operationsError = '';
    this.membershipApi.getAdminOperations().pipe(
      catchError((error: unknown) => {
        this.operationsError = error instanceof HttpErrorResponse && error.status === 403
          ? 'The server denied access to administration operations.'
          : 'Could not load administration operations.';
        return of(null);
      })
    ).subscribe((status) => {
      if (status) this.operationsStatus = status;
    });
  }

  loadAssignedMembers(): void {
    this.coachError = '';
    this.membershipApi.getAssignedMembers().pipe(
      catchError((error: unknown) => {
        this.coachError = error instanceof HttpErrorResponse && error.status === 403
          ? 'The server denied access to assigned-member records.'
          : 'Could not load assigned members.';
        return of(null);
      })
    ).subscribe((members) => {
      if (members) this.assignedMembers = members;
    });
  }

  async signOut(): Promise<void> {
    await this.auth.logout();
  }
}
