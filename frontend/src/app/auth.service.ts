import { Injectable, signal } from '@angular/core';
import Keycloak from 'keycloak-js';

const applicationRoles = new Set(['ADMIN', 'COACH', 'MEMBER']);
const knownRoutes = new Set([
  '/', '/dashboard', '/membership', '/courses', '/training', '/nutrition',
  '/forbidden', '/not-found'
]);

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = new Keycloak({
    url: 'http://localhost:8080',
    realm: 'gym',
    clientId: 'gym-frontend'
  });
  private refreshAttempted = false;
  private refreshInFlight: Promise<boolean> | null = null;
  private readonly authenticatedState = signal(false);
  private readonly roleState = signal<string[]>([]);
  private readonly userNameState = signal('');
  private readonly initializationErrorState = signal('');

  readonly authenticated = this.authenticatedState.asReadonly();
  readonly roles = this.roleState.asReadonly();
  readonly userName = this.userNameState.asReadonly();
  readonly initializationError = this.initializationErrorState.asReadonly();

  async initialize(): Promise<void> {
    try {
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        pkceMethod: 'S256',
        checkLoginIframe: false,
        silentCheckSsoFallback: false
      });
      this.syncIdentity(authenticated);
      this.keycloak.onAuthSuccess = () => this.syncIdentity(true);
      this.keycloak.onAuthLogout = () => this.clearIdentity();
      this.keycloak.onTokenExpired = () => {
        void this.refreshAccessToken().then((valid) => {
          if (!valid) this.clearIdentity();
        });
      };
    } catch (error: unknown) {
      this.initializationErrorState.set(
        error instanceof Error
          ? `Identity service is unavailable: ${error.message}`
          : 'Identity service is unavailable. Check that Keycloak is running.'
      );
      this.clearIdentity();
    }
  }

  async login(returnUrl: string): Promise<void> {
    const safeReturnUrl = this.safeReturnUrl(returnUrl);
    await this.keycloak.login({ redirectUri: `${window.location.origin}${safeReturnUrl}` });
  }

  async logout(): Promise<void> {
    this.clearIdentity();
    await this.keycloak.logout({ redirectUri: `${window.location.origin}/` });
  }

  async accessToken(): Promise<string | null> {
    if (!this.keycloak.authenticated || this.refreshAttempted) return null;
    const valid = await this.refreshAccessToken();
    return valid ? this.keycloak.token ?? null : null;
  }

  expireSession(): void {
    this.keycloak.clearToken();
    this.clearIdentity();
  }

  hasAnyRole(allowedRoles: readonly string[]): boolean {
    return this.roleState().some((role) => allowedRoles.includes(role));
  }

  private async refreshAccessToken(): Promise<boolean> {
    if (this.refreshAttempted) return false;
    if (this.refreshInFlight) return this.refreshInFlight;

    this.refreshInFlight = this.keycloak.updateToken(30)
      .then(() => {
        this.syncIdentity(true);
        return true;
      })
      .catch(() => {
        this.refreshAttempted = true;
        this.keycloak.clearToken();
        this.clearIdentity();
        return false;
      })
      .finally(() => {
        this.refreshInFlight = null;
      });
    return this.refreshInFlight;
  }

  private syncIdentity(authenticated: boolean): void {
    this.authenticatedState.set(authenticated);
    if (!authenticated) {
      this.roleState.set([]);
      this.userNameState.set('');
      return;
    }
    const token = this.keycloak.tokenParsed;
    const realmAccess = token?.['realm_access'];
    const realmRoles = typeof realmAccess === 'object' && realmAccess !== null
      ? (realmAccess as { roles?: unknown }).roles
      : undefined;
    this.roleState.set(
      Array.isArray(realmRoles)
        ? realmRoles.filter((role): role is string => typeof role === 'string' && applicationRoles.has(role))
        : []
    );
    const name = token?.['name'];
    const preferredUsername = token?.['preferred_username'];
    this.userNameState.set(
      typeof name === 'string' ? name
        : typeof preferredUsername === 'string' ? preferredUsername
          : 'Gym user'
    );
    this.refreshAttempted = false;
  }

  private clearIdentity(): void {
    this.authenticatedState.set(false);
    this.roleState.set([]);
    this.userNameState.set('');
  }

  private safeReturnUrl(value: string): string {
    if (!value.startsWith('/') || value.startsWith('//')) return '/dashboard';
    try {
      const destination = new URL(value, window.location.origin);
      return destination.origin === window.location.origin && knownRoutes.has(destination.pathname)
        ? `${destination.pathname}${destination.search}${destination.hash}`
        : '/dashboard';
    } catch {
      return '/dashboard';
    }
  }
}
