import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  standalone: true,
  template: `
    <main class="landing-shell">
      <section class="landing-card">
        <p class="eyebrow">TRAIN SMART. LIVE STRONG.</p>
        <h1>Smart <span>Gym</span></h1>
        <p class="lead">Your membership, coaching, training, and nutrition in one place.</p>
        @if (expired) {
          <p class="notice" role="status">Your session expired. Sign in again to continue.</p>
        }
        @if (auth.initializationError()) {
          <p class="notice error" role="alert">{{ auth.initializationError() }}</p>
        }
        @if (loginError) {
          <p class="notice error" role="alert">{{ loginError }}</p>
        }
        <button class="button primary" type="button" (click)="signIn()">Sign in</button>
        <p class="secure-note">Secure sign-in powered by Smart Gym identity.</p>
      </section>
    </main>
  `
})
export class LandingComponent {
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  readonly expired = this.route.snapshot.queryParamMap.get('session') === 'expired';
  loginError = '';

  async signIn(): Promise<void> {
    try {
      await this.auth.login(this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard');
    } catch (error: unknown) {
      this.loginError = error instanceof Error ? error.message : 'Could not start secure sign-in.';
    }
  }
}
