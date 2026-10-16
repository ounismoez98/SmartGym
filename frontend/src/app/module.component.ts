import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="content">
      <a class="back-link" routerLink="/dashboard">← Dashboard</a>
      <p class="eyebrow">{{ moduleCode }} MODULE</p>
      <h1>{{ title }}</h1>
      <p class="lead">{{ description }}</p>
      <section class="account-card">
        <h2>Secure module workspace</h2>
        <p>Your authenticated session and role are checked before this area opens. Business features for this module are the next delivery phase.</p>
      </section>
    </main>
  `
})
export class ModuleComponent {
  private readonly route = inject(ActivatedRoute);
  readonly title = String(this.route.snapshot.data['title']);
  readonly moduleCode = String(this.route.snapshot.data['module']);
  readonly description = String(this.route.snapshot.data['description']);
}
