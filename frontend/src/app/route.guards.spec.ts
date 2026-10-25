import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';
import { authGuard, roleGuard } from './route.guards';

describe('membership route guards', () => {
  it('allows authenticated users with an allowed module role', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { authenticated: () => true, hasAnyRole: () => true } }
      ]
    });
    const injector = TestBed.inject(EnvironmentInjector);
    const result = runInInjectionContext(injector, () => roleGuard(
      { data: { roles: ['ADMIN', 'MEMBER'] } } as unknown as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot
    ));

    expect(result).toBe(true);
  });

  it('redirects users without an allowed role to the forbidden page', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { authenticated: () => true, hasAnyRole: () => false } }
      ]
    });
    const injector = TestBed.inject(EnvironmentInjector);
    const router = TestBed.inject(Router);
    const result = runInInjectionContext(injector, () => roleGuard(
      { data: { roles: ['ADMIN'] } } as unknown as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot
    ));

    expect(router.serializeUrl(result as import('@angular/router').UrlTree)).toBe('/forbidden');
  });

  it('redirects unauthenticated users to sign in with their return URL', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { authenticated: () => false } }
      ]
    });
    const injector = TestBed.inject(EnvironmentInjector);
    const router = TestBed.inject(Router);
    const result = runInInjectionContext(injector, () => authGuard(
      {} as ActivatedRouteSnapshot,
      { url: '/membership' } as RouterStateSnapshot
    ));

    expect(router.serializeUrl(result as import('@angular/router').UrlTree))
      .toBe('/?returnUrl=%2Fmembership');
  });
});
