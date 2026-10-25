import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const apiPath = /^\/api\/(membership|courses|training|nutrition|feedback)(\/|$)/;

function isApprovedBusinessApi(url: string): boolean {
  try {
    const parsed = new URL(url, window.location.origin);
    return parsed.origin === window.location.origin && apiPath.test(parsed.pathname);
  } catch {
    return false;
  }
}

export const apiAuthInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApprovedBusinessApi(request.url)) return next(request);

  const auth = inject(AuthService);
  const router = inject(Router);
  return from(auth.accessToken()).pipe(
    switchMap((token) => next(token
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request)),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        auth.expireSession();
        void router.navigate(['/'], { queryParams: { session: 'expired' } });
      } else if (error instanceof HttpErrorResponse && error.status === 403) {
        void router.navigate(['/forbidden']);
      }
      return throwError(() => error);
    })
  );
};
