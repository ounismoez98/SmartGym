import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.authenticated()
    ? true
    : router.createUrlTree(['/'], { queryParams: { returnUrl: state.url } });
};

export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const allowedRoles = route.data['roles'] as readonly string[] | undefined;
  return allowedRoles && auth.hasAnyRole(allowedRoles)
    ? true
    : router.createUrlTree(['/forbidden']);
};
