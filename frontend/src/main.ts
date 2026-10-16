import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAppInitializer, inject } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { AppComponent } from './app/app.component';
import { appRoutes } from './app/app.routes';
import { AuthService } from './app/auth.service';
import { apiAuthInterceptor } from './app/api-auth.interceptor';

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(appRoutes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(withInterceptors([apiAuthInterceptor])),
    provideAppInitializer(() => inject(AuthService).initialize())
  ]
}).catch((error: unknown) => {
  console.error('Smart Gym could not start.', error);
});
