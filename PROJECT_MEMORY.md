# Smart Gym — Project Memory

> Compact handoff for future work. Update this file when the project state,
> decisions, verification, or next priorities materially change. Do not store
> credentials, tokens, personal data, or copies of `.env` here.

## Current checkpoint — 2026-10-16

### Project state

- Repository started empty (no prior application/commits); current branch is
  `main`. Initial runnable authentication vertical slice has been created.
- Development stack was started and browser-smoke-tested at
  `http://localhost:8088`; Keycloak is at `http://localhost:8080`.
- Local secrets are in ignored `.env`; use `.env.example` as the template and
  never copy actual secret values into documentation or source control.

### Ownership and identity rules — preserve these

- M1 membership-service: Nour.
- M2 courses-service: Yacine.
- M3 training-service: **pending**; do not choose between Moez and Mehdi.
- M4 nutrition-service: **pending**; owner is the remaining member after M3
  is confirmed. Do not guess.
- M5 Keycloak/shared infrastructure: shared team responsibility.
- Project ownership is not gym-user identity. Do not grant developers roles
  based on their names. Application roles are `ADMIN`, `COACH`, `MEMBER`;
  roles can overlap.
- Seeded gym accounts are explicitly development-only and separate from
  developers.

### Implemented architecture

- `frontend/`: strict Angular SPA using official `keycloak-js`; Authorization
  Code + PKCE S256; in-memory adapter tokens; initialization before guards;
  silent SSO callback; same-origin allowlisted API-token interceptor; token
  refresh/expiry handling and `401`/`403` behavior.
- `keycloak/realm-import/`: imported `gym` realm, public `gym-frontend` SPA client
  without a secret, exact development redirect/logout/web-origin allowlists,
  explicit role/profile/subject/audience claims, and lightweight login theme.
  Password reset is disabled because SMTP is not configured.
- `backend/`: Spring Boot resource server backed by PostgreSQL; validates
  Keycloak signature via Docker-internal JWKS and exact browser-visible issuer,
  API audience, and subject. `GET /api/membership/me` returns JWT-derived
  subject, display name, application roles, optional business profile IDs,
  and onboarding status. Missing business profiles do not create employees or
  grant privileges.
- Backend access checks: members can read only their own private record;
  coaches only assigned members; admins can read any member. Admin operations
  and coach assigned-member listing have server-side role checks. Angular menu
  visibility is not treated as authorization.
- `frontend/nginx.conf`: SPA history fallback and same-prefix proxying for
  `/api/membership`, `/api/courses`, `/api/training`, `/api/nutrition`.
- `scripts/provision-dev.ps1`: idempotent provisioning of gym-admin,
  gym-coach, member-a, member-b via Keycloak Admin API, then business profiles
  and coach assignments via a dev-only, provisioning-key-protected API. No
  Keycloak database access.
- `docker-compose.yml`: local PostgreSQL, Keycloak, RabbitMQ, Ollama, API,
  and frontend services. RabbitMQ and Ollama data use named volumes.
- `README.md`: startup instructions, development-only HTTP and account setup,
  production HTTPS guidance, ownership and acceptance status.

### Verified

- `npm.cmd run build` from `frontend/`: passed.
- `mvn.cmd -B test` from `backend/` using Java 21: passed, 8 tests.
- `docker compose --env-file .env.example config --quiet`: passed.
- Compose stack started; Angular and Keycloak returned HTTP 200.
- Browser: landing → hosted Keycloak login → dashboard; invalid credentials
  display Keycloak feedback; signed-in dashboard successfully called
  `/api/membership/me` with a bearer token and displayed its member profile.
- Browser: `ADMIN` administration operation and `COACH` assigned-member
  action worked; coach saw assigned Member A/B; member navigation was
  role-aware; originally requested `/training` route was restored after login;
  protected route redirected to sign-in after logout.
- Direct unauthenticated API requests returned `401`. Automated tests cover
  role denials (`403`) and Member A/B record isolation.
- Session expiry was **not explicitly browser-simulated**. Browser showed a
  non-fatal Keycloak hidden-iframe sandbox warning; login and callback worked.

### Important paths

- Start stack: `docker compose up --build -d`
- Provision users: see commands in `README.md`; requires local environment
  variables. Never paste development passwords into chat or docs.
- Angular: `frontend/src/app/`
- JWT/security/API: `backend/src/main/java/com/smartgym/membership/`
- API access-control tests:
  `backend/src/test/java/com/smartgym/membership/api/MembershipControllerTest.java`
- Realm/client/claims: `keycloak/realm-import/smart-gym-realm.json`

## Next work — recommended order

1. Continue the authentication foundation before business features: explicitly
   test session expiry/failed refresh in the browser, verify redirect-loop
   protection, and address the Keycloak iframe warning if a compatible,
   secure configuration is available. Retain exact issuer/audience checks.
2. Add integration coverage using real signed Keycloak tokens for issuer,
   audience, subject, and role claims (current MockMvc JWT tests exercise
   authorization behavior but do not verify live signature validation).
3. Confirm M3 and M4 owners with the team; keep documentation marked pending
   until confirmation. Continue M1/M2 ownership as listed above.
4. Implement module CRUD only after the security acceptance foundation;
   enforce authorization and member/coach record isolation server-side.
5. Before production, switch to HTTPS and managed secrets, remove development
   ports/profile/provisioning, and configure SMTP before enabling recovery.

## Update convention

Keep this file compact: replace the current checkpoint date/status and record
material changes, decisions, verification results, blockers, and next tasks.
Do not duplicate full feature documentation from `README.md`.
