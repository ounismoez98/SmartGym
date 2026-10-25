import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { apiAuthInterceptor } from './api-auth.interceptor';
import { AuthService } from './auth.service';
import { appRoutes } from './app.routes';
import { authGuard, roleGuard } from './route.guards';

describe('nutrition route authorization', () => {
  let isAuthenticated: boolean;
  let roles: string[];

  beforeEach(() => {
    isAuthenticated = true;
    roles = ['MEMBER'];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            authenticated: () => isAuthenticated,
            hasAnyRole: (allowed: readonly string[]) => roles.some((role) => allowed.includes(role))
          }
        }
      ]
    });
  });

  it('requires authentication and retains the requested route for the login flow', () => {
    isAuthenticated = false;
    const result = TestBed.runInInjectionContext(() => authGuard(
      {} as ActivatedRouteSnapshot,
      { url: '/nutrition' } as RouterStateSnapshot
    ));

    expect(result).toEqual(jasmine.anything());
    expect((result as ReturnType<Router['createUrlTree']>).toString()).toContain('returnUrl=%2Fnutrition');
  });

  it('allows only the nutrition route roles and redirects unsupported roles', () => {
    const nutritionRoute = appRoutes.find((route) => route.path === 'nutrition');
    expect(nutritionRoute?.data?.['roles']).toEqual(['ADMIN', 'MEMBER']);

    roles = ['COACH'];
    const denied = TestBed.runInInjectionContext(() => roleGuard({
      data: nutritionRoute?.data ?? {}
    } as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    expect((denied as ReturnType<Router['createUrlTree']>).toString()).toBe('/forbidden');

    roles = ['ADMIN'];
    expect(TestBed.runInInjectionContext(() => roleGuard({
      data: nutritionRoute?.data ?? {}
    } as ActivatedRouteSnapshot, {} as RouterStateSnapshot))).toBeTrue();
  });
});

describe('apiAuthInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let token: string | null;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    token = 'test-access-token';
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['accessToken', 'expireSession']);
    auth.accessToken.and.callFake(async () => token);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiAuthInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router }
      ]
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('attaches the access token to same-origin nutrition API requests only', async () => {
    const response = firstValueFrom(http.get('/api/nutrition/foods'));
    await Promise.resolve();
    const request = controller.expectOne('/api/nutrition/foods');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-access-token');
    request.flush([]);
    await response;

    const externalResponse = firstValueFrom(http.get('https://api.groq.com/openai/v1/chat/completions'));
    await Promise.resolve();
    const external = controller.expectOne('https://api.groq.com/openai/v1/chat/completions');
    expect(external.request.headers.has('Authorization')).toBeFalse();
    expect(auth.accessToken).toHaveBeenCalledTimes(1);
    external.flush({});
    await externalResponse;
  });

  it('does not add an authorization header when there is no token', async () => {
    token = null;
    const response = firstValueFrom(http.get('/api/nutrition/foods'));
    await Promise.resolve();
    const request = controller.expectOne('/api/nutrition/foods');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush([]);
    await response;
  });

  it('expires the session on 401 and redirects 403 without hiding the HTTP error', async () => {
    const unauthorized = firstValueFrom(http.get('/api/nutrition/profiles')).catch((error: unknown) => error);
    await Promise.resolve();
    controller.expectOne('/api/nutrition/profiles').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(await unauthorized).toEqual(jasmine.any(HttpErrorResponse));
    expect(auth.expireSession).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/'], { queryParams: { session: 'expired' } });

    const forbidden = firstValueFrom(http.get('/api/nutrition/foods')).catch((error: unknown) => error);
    await Promise.resolve();
    controller.expectOne('/api/nutrition/foods').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(await forbidden).toEqual(jasmine.any(HttpErrorResponse));
    expect(router.navigate).toHaveBeenCalledWith(['/forbidden']);
  });
});
