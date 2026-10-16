import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="message-shell">
      <section class="account-card">
        <p class="eyebrow">SMART GYM</p>
        <h1>{{ title }}</h1>
        <p>{{ message }}</p>
        <a class="button primary" routerLink="/dashboard">Back to dashboard</a>
        <a class="back-link" routerLink="/">Return to home</a>
      </section>
    </main>
  `
})
export class MessagePageComponent {
  private readonly route = inject(ActivatedRoute);
  readonly title = String(this.route.snapshot.data['title']);
  readonly message = String(this.route.snapshot.data['message']);
}
