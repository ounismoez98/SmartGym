# Smart Gym

Smart Gym is a gym-management platform using Angular, Spring Boot, PostgreSQL,
and Keycloak. The repository includes the authenticated membership vertical
slice and a member-scoped Nutrition & Body Tracking module (M4). Courses and
training API prefixes are protected but their module services are not included.

## Development ownership

| Area | Owner |
| --- | --- |
| M1 membership-service | Nour |
| M2 courses-service | Yacine |
| M3 training-service | Pending confirmation: Moez or Mehdi |
| M4 nutrition-service | Pending confirmation: the remaining member |
| M5 Keycloak and shared infrastructure | Shared team responsibility |

These are project ownership assignments, not application roles. Application
roles are `ADMIN`, `COACH`, and `MEMBER`; users may have overlapping roles.
The development accounts provisioned by the script below are local test
identities and are not mapped to any developer.

## Development-only URLs and startup

The local HTTP configuration is for development only:

| Component | URL |
| --- | --- |
| Angular/Nginx | `http://localhost:8088` |
| Keycloak | `http://localhost:8080` |
| Realm | `smart-gym` |
| Business API | `http://localhost:8088/api/...` (proxied by Nginx) |

The Spring API and its PostgreSQL database are also exposed on host ports
`8081` and `5433` for local development and provisioning. Browser API calls
should use the Nginx `/api/...` URLs.

1. Copy `.env.example` to `.env` and set unique local-only passwords and a
   provisioning key. Do not use these development secrets outside your local
   machine.
2. Start the core platform:

   ```powershell
   docker compose up --build -d
   ```

3. Wait for the PostgreSQL and Keycloak health checks to pass, then confirm
   the API started with `docker compose ps` and `docker compose logs api`.
   The API and frontend do not define Compose health checks. Provision the
   four development accounts and their business profiles:

   ```powershell
   $env:KEYCLOAK_ADMIN_USERNAME = "admin"
   $env:KEYCLOAK_ADMIN_PASSWORD = "<the KEYCLOAK_ADMIN_PASSWORD from .env>"
   $env:DEV_ADMIN_PASSWORD = "<choose a local-only password>"
   $env:DEV_COACH_PASSWORD = "<choose a local-only password>"
   $env:DEV_MEMBER_A_PASSWORD = "<choose a local-only password>"
   $env:DEV_MEMBER_B_PASSWORD = "<choose a local-only password>"
   .\scripts\provision-dev.ps1
   ```

   Account usernames are `gym-admin`, `gym-coach`, `member-a`, and
   `member-b`. The script creates or updates these Keycloak users, applies
   their application roles, and idempotently provisions their business
   profiles and coach assignments through the Keycloak Admin API and
   development provisioning endpoint. It prints the usernames, never their
   passwords. Passwords are supplied only through environment variables and
   are not stored in source control. The Keycloak administrator account is
   infrastructure-only; it is not an application administrator.
4. Open `http://localhost:8088` and select **Sign in**.

## Database schema and migrations

The API uses Flyway migrations under
`backend/src/main/resources/db/migration`. `V1__baseline.sql` creates the
current PostgreSQL schema for both membership and nutrition entities. New
installations using a fresh, empty database apply this migration on first API
startup; Hibernate validates the mapped schema and does not modify it. If the
Compose PostgreSQL volume already contains a database, review the existing
database procedure below before starting the API.

For an existing database, first take a database backup and compare its schema
with `V1__baseline.sql`. Do not baseline a database with unrelated or
unreviewed schema drift. First inspect whether `flyway_schema_history` already
exists; do not baseline a database that already has migration history. Only
after confirming the existing schema represents V1 and there is no migration
history, stop the API and start it once with Flyway's baseline options:

```powershell
docker compose stop api
docker compose run --rm api `
  --spring.flyway.baseline-on-migrate=true `
  --spring.flyway.baseline-version=1
```

Wait for the API to report that it started successfully, then stop the
one-off container with `Ctrl+C` and start the normal service:

```powershell
docker compose up -d api
```

The one-time baseline marks the existing schema at version 1 without replaying
the baseline DDL or changing its data. Future versioned migrations then run
normally. Never drop the PostgreSQL volume to resolve a migration error.

Keycloak credential validation and invalid-credential messages stay on the
Keycloak-hosted login page. The realm uses a small Smart Gym CSS theme. SMTP
is not configured; password-recovery email is intentionally disabled and is
not operational.

The Compose stack routes `/api/membership`, `/api/courses`, `/api/training`,
and `/api/nutrition` to the secured business API while preserving the full
prefix. Nutrition endpoints provide member-scoped foods, meal plans, food
journal entries, body measurements, profiles, nutritional calculations, and
an optional local AI meal-plan assistant. Courses and training remain
security-protected and await their module services.

## Nutrition & Body Tracking (Module 4)

### Starting and using Nutrition

Prerequisites are Docker Desktop with Docker Compose v2 and access to the
container image registries used by the Compose file. No host-installed Java,
Maven, Node.js, or npm is required for the container-based commands below.

1. Copy `.env.example` to `.env`. Replace the placeholder values for
   `KEYCLOAK_ADMIN_PASSWORD`, `POSTGRES_PASSWORD`, and `PROVISIONING_TOKEN`
   with unique development-only secrets. Keep `.env` private and never commit
   it. `KEYCLOAK_ADMIN_USERNAME` defaults to `admin`.
2. Start the services:

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

   Compose starts `postgres`, `keycloak-db`, `keycloak`, `api`, and
   `frontend`; database health checks gate dependent services. `api` and
   `frontend` are built from the repository Dockerfiles.
3. Provision development accounts if this is a new local environment. In
   PowerShell, set `KEYCLOAK_ADMIN_USERNAME`, `KEYCLOAK_ADMIN_PASSWORD`,
   `PROVISIONING_TOKEN`, and unique values for `DEV_ADMIN_PASSWORD`,
   `DEV_COACH_PASSWORD`, `DEV_MEMBER_A_PASSWORD`, and
   `DEV_MEMBER_B_PASSWORD` in the current shell, then run:

   ```powershell
   .\scripts\provision-dev.ps1
   ```

   The script provisions `gym-admin`, `gym-coach`, `member-a`, and `member-b`.
   Supply passwords through environment variables; do not put them in source
   files or command history.
4. Open `http://localhost:8088`, sign in with an account assigned the
   `MEMBER` or `ADMIN` role, and select **Nutrition** from the dashboard.
   Enter profile details, add foods, then log journal entries or measurements.
   The Nutrition page includes daily totals, profile-based BMI and saved-target
   macro estimates, food and meal-plan CRUD, journal CRUD, and measurement
   history. The AI assistant is optional; see its configuration below.

The `.env` values directly required by Compose are the Keycloak bootstrap
username/password, PostgreSQL password, and provisioning token. Compose sets
the API datasource URL/username and Keycloak issuer/JWKS URLs in the API
service configuration. Groq is optional: set `GROQ_API_KEY` in the private
`.env` file and choose `GROQ_MODEL`. The README example uses
`openai/gpt-oss-20b`, which Groq's Structured Outputs documentation currently
uses as a supported-model example. Model availability can change and can
depend on account access; check the current [Groq model catalog](https://console.groq.com/docs/models)
and your account's available models before relying on it. The Groq catalog
also documents the authenticated Models API for listing active IDs; this
application does not call it. Do not put a real
API key in source control, build arguments, Angular code, or logs.
`APP_LLM_CONNECT_TIMEOUT` and `APP_LLM_READ_TIMEOUT` control backend HTTP
timeouts and default to `5s` and `120s`.

### Groq meal-plan integration

The backend sends requests only to Groq's official
`https://api.groq.com/openai/v1/chat/completions` endpoint. Compose passes
`GROQ_API_KEY` and `GROQ_MODEL` to the API container; neither is provided to
the frontend. The API key is sent in the backend-only `Authorization: Bearer`
header and is not included in the request body or logged by the adapter.

The request uses the configured model, `system` and `user` chat messages, and
`response_format` with `type: json_schema`, `strict: true`, and a meal-plan
schema. See Groq's [Structured Outputs documentation](https://console.groq.com/docs/structured-outputs).
Groq's response is read from `choices[0].message.content`, which must contain
the plan JSON. The application then performs its own DTO, numeric, meal-type,
serving, size, and restriction checks. Strict JSON mode does not replace
application validation.

Only the saved goal, saved calorie target, dietary preference, explicit
request restrictions, and member-owned food names/nutrition are sent as
generation context. JWTs, member subjects, profile age, height, and weight
are not sent. Do not add sensitive personal data to the prompt.

If `GROQ_API_KEY` is empty, generation returns `UNAVAILABLE` without a
network call. The model default is `openai/gpt-oss-20b`; override it with a
model currently available to your account using `GROQ_MODEL`. Authentication
errors, rate limits, timeouts, network failures, and malformed responses
produce non-secret unavailable messages. The call continues to run on the
existing bounded asynchronous executor. No live Groq request has been made
as part of this implementation; generation against an active account/model
remains unverified.

Groq-hosted model output is untrusted. Explicit restrictions are checked
before the suggestion is returned and are checked again when the member
saves a reviewed plan. This does not establish allergy safety or medical
suitability. Nutrition is recalculated from saved food data when a meal's
foods all match; otherwise the values remain model estimates.
Inference runs outside servlet request threads on a bounded pool (up to four
active calls and eight queued requests). The existing plan-generation route
keeps its response shape; saturated requests return an explicit unavailable
response instead of adding unbounded work.
Review and edit every meal before saving. The current schema stores each meal
as a separate member-owned meal-plan row and can preserve preparation
instructions in its description. Shopping lists and alternatives are shown
for review but are not persisted.

### Nutrition API

All routes below are under `/api/nutrition`. They require a valid Keycloak JWT
and the `ADMIN` or `MEMBER` application role. The server derives the data
owner from the JWT subject; there is no client-supplied owner ID. Lists and
record operations are scoped to that subject, including for admins.
Validation failures generally return `400`; missing or non-owned records
return `404`. Food deletion returns `409` while any journal entry or meal-plan
item references that food.

| Method and path | Behavior and principal validation |
| --- | --- |
| `POST /foods` | Create a food. Requires a nonblank name, positive serving size in grams, and finite nonnegative calories, protein, carbohydrates, fat, fiber, and sodium. Name/brand max 150 characters. |
| `GET /foods` | List the authenticated subject's foods. |
| `PUT /foods/{foodId}` | Replace a food's editable values, with the create validation rules; only its owner can update it. |
| `DELETE /foods/{foodId}` | Delete an owned food only when it is not referenced by a journal entry or meal plan. |
| `POST /meal-plans` | Create one meal record. Requires a name, supported meal type, nonnegative target calories, and at least one food with positive servings. Every food must belong to the caller. |
| `GET /meal-plans` | List the authenticated subject's meal plans. |
| `PUT /meal-plans/{mealPlanId}` | Update an owned meal plan, with create validation and food-ownership checks. |
| `DELETE /meal-plans/{mealPlanId}` | Delete an owned meal plan. |
| `POST /food-journal` | Create a journal entry with an owned food, meal type, ISO date, and positive servings. Notes max 300 characters. |
| `GET /food-journal` | List the authenticated subject's journal entries. |
| `PUT /food-journal/{entryId}` | Update an owned entry, with create validation and food-ownership checks. |
| `DELETE /food-journal/{entryId}` | Delete an owned journal entry. |
| `POST /body-measurements` | Create a dated measurement. Weight (kg), waist, hip, chest, arm, and thigh (cm) are required and positive; notes max 300 characters. |
| `GET /body-measurements` | List the authenticated subject's measurement history. |
| `PUT /body-measurements/{measurementId}` | Update an owned measurement, with create validation. |
| `DELETE /body-measurements/{measurementId}` | Delete an explicitly selected owned measurement record. |
| `POST /profiles` | Create or update the caller's latest profile. Profile name, goal, dietary preference, and activity level are required; optional age, height (cm), weight (kg), and target calories must be positive when provided. Text values have field-specific maximum lengths. |
| `GET /profiles` | Read the caller's latest profile; returns `404` when none exists. |
| `GET /calculations?foodIds={id}&servings={n}` | Sum calories, macros, fiber, and sodium for the supplied owned food IDs. Repeat `foodIds` and `servings` parameters for multiple foods. Servings are optional (default one per food); when supplied, there must be exactly one positive finite quantity per ID. |
| `GET /calculations/bmi` | Calculate BMI from profile kilograms and centimetres converted to metres. Requires a profile (`404` if absent); returns `422` if required height or weight is missing/invalid. The response labels BMI as an estimate, not body-fat percentage. |
| `GET /calculations/macros` | Estimate protein (1.6 g/kg), fat (25% of the saved calorie target), and carbohydrates from remaining calories. Requires a profile (`404` if absent) with positive saved weight and target calories; returns `422` if inputs are missing or the target cannot support nonnegative carbohydrates. This uses the manually saved target, not TDEE. |
| `GET /calculations/energy` | With a profile present, return an unavailable energy estimate and explanation. Returns `404` if there is no profile. Current profile fields do not provide the required Mifflin–St Jeor input; BMR/TDEE are not calculated. |
| `GET /food-journal/daily?date=YYYY-MM-DD` | Return the caller's daily totals and meal-level calories, macros, fiber, and sodium for the required date, scaled by logged servings. |
| `POST /assistant/plan` | Request an AI suggestion. The UI sends `{"restrictions":["..."]}`; profile, target, and saved-food context come from the authenticated caller's records. Returns `UNAVAILABLE` when not configured, unreachable, malformed, or rejected; executor saturation/failure returns `503`. |
| `POST /assistant/plan/save` | Save a reviewed plan using `planName`, `goal`, `restrictions`, and 1–12 meals; each meal requires a supported type, name, preparation instructions, and one or more owned saved-food IDs with positive servings. Restrictions and ownership are rechecked before persistence. |

All meal-type values are `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`,
`PRE_WORKOUT`, or `POST_WORKOUT`. Profile goals and dietary preference are
stored as text; the UI offers weight loss, maintenance, muscle gain and
balanced/vegetarian/vegan/pescatarian/other choices. No dedicated
preferences/allergy table exists.

### Build and verification commands

Run these from the repository root in PowerShell:

```powershell
# Validate Compose interpolation and service configuration.
docker compose config --quiet

# Run all backend tests and package with Java 21 / Maven in the project toolchain.
$backend = (Resolve-Path .\backend).Path
docker run --rm --mount "type=bind,source=$backend,target=/workspace" `
  -w /workspace maven:3.9-eclipse-temurin-21 mvn -B test package

# Build the frontend production image (its Dockerfile runs npm ci and npm run build).
docker compose build frontend

# Build the API and frontend images without restarting running services.
docker compose build api frontend

# Install frontend dependencies and run Angular unit/component tests.
$frontend = (Resolve-Path .\frontend).Path
docker run --rm --mount "type=bind,source=$frontend,target=/app" `
  -w /app node:24-bookworm-slim sh -c "apt-get update && apt-get install -y --no-install-recommends chromium && npm ci && CHROME_BIN=/usr/bin/chromium npm test"

# Run the same tests and generate HTML plus text-summary coverage.
docker run --rm --mount "type=bind,source=$frontend,target=/app" `
  -w /app node:24-bookworm-slim sh -c "apt-get update && apt-get install -y --no-install-recommends chromium && npm ci && CHROME_BIN=/usr/bin/chromium npm run test:coverage"

# Inspect service state and follow logs.
docker compose ps
docker compose logs -f api frontend
```

The frontend's Angular test target uses Karma, Jasmine, and headless Chromium.
For local execution, use Node.js 24, run `npm ci` from `frontend`, and ensure
Chrome or Chromium is installed; set `CHROME_BIN` to its executable path on
Linux. `npm test` runs the tests once. `npm run test:coverage` writes the HTML
report to `frontend/coverage/smart-gym/index.html` and prints a text summary.

To generate backend line/branch coverage, run `mvn -B test jacoco:report`
from `backend`. The HTML report is written to `target/site/jacoco/index.html`;
machine-readable XML and CSV reports are written alongside it. For Docker
Maven execution from the repository root:

```powershell
$backend = (Resolve-Path .\backend).Path
docker run --rm --mount "type=bind,source=$backend,target=/workspace" `
  -w /workspace maven:3.9-eclipse-temurin-21 mvn -B test jacoco:report
```

The opt-in `PostgresNutritionIntegrationTest` requires an isolated PostgreSQL
database named `smartgym_it` and username `smartgym_it`. It is skipped unless
`smartgym.postgres.it.url`, `.username`, and `.password` system properties are
provided. Never point these properties at the development or production
database. It runs Flyway against the supplied database; tests inspect data,
update and delete only their own rows, and verify migration behavior.

Latest backend test and coverage run (2026-10-24) used all existing H2 tests
plus the opt-in PostgreSQL tests against a disposable PostgreSQL 16 container:
55 tests, 0 failures, 0 errors, and 0 skipped. JaCoCo measured 89.32% line
coverage (903/1,011), 58.85% branch coverage (369/627), and 90.05% instruction
coverage (5,396/5,992) across application classes. The preceding Phase 2 run
was 48 tests, 0 failures, 0 errors, and 0 skipped, with 89.02% line and
58.85% branch coverage. PostgreSQL integration tests do not verify the
running Compose API, Keycloak token validation, or Groq inference.

The frontend test suite is configured and runs with mocked HTTP and
authentication dependencies; it does not call the live API, Keycloak, or
Groq. The production frontend build remains `npm run build`. For local host
builds, the backend requires Java 21 and Maven; the frontend requires Node.js
24 and npm.

Latest frontend verification (2026-10-24): `npm ci` followed by
`CHROME_BIN=/usr/bin/chromium npm run test:coverage` in Node.js 24 with
headless Chromium executed 37 tests: 37 passed, 0 failed, 0 skipped. Coverage
was 68.54% statements (329/480), 47.34% branches (89/188), 69.59% functions
(119/171), and 70.07% lines (295/421). The HTML report is
`frontend/coverage/smart-gym/index.html`. The frontend production build
(`npm run build`) also passed. These are frontend unit/component and mocked
authentication checks, not live browser-to-Keycloak or API end-to-end tests.

### Performance and load checks

Reproducible k6 scripts for read-only API baseline/concurrency, expected
unauthenticated errors, gated read-only ramp testing, and separately gated
single-request Groq latency are in [`performance/`](./performance/README.md).
The only available Compose stack during setup was the running development
stack, backed by the `smartgym` database; it was not used for load testing.
Actual performance results remain blocked until the dedicated isolated API,
database, and Keycloak test identity described in that guide are available.

### Module status and limitations

- The Nutrition page has profile editing, food CRUD/search, meal-plan
  CRUD, journal CRUD/daily totals, measurement entry/history and a saved-data
  weight chart. The body illustrations are presentation-only and do not infer
  body composition.
- The persisted profile supports age, height, weight, target calories, goal,
  activity level, and one dietary-preference text value. Target weight and
  training frequency are session-only in the UI; allergies/restrictions are
  supplied per AI request and are not persisted. Budget, cuisine, meals per
  day, disliked foods, and preparation preferences are not supported by the
  current API and are not collected.
- BMI is available from valid saved height/weight. Saved-target macro
  estimates are available from valid saved weight/calories. BMR/TDEE and
  TDEE-derived calorie targets are unavailable because the profile has no
  sex-related input to select the Mifflin–St Jeor variant. Required age,
  height, and weight must also be present before that equation could be
  evaluated.
- Journal entries store a food reference and serving quantity, not a nutrient
  snapshot. Since daily totals use the food's current nutrient values, editing
  a food can change recalculated totals for earlier journal dates.
- A generated multi-meal suggestion is saved as separate member-owned meal
  records. Preparation text is stored in each record description; the
  shopping list and alternatives are not persisted.
- No Groq API key has been configured or verified for inference. Live
  Keycloak-backed Nutrition browser/API integration and end-to-end persistence
  through the running Compose API have not been verified. The separate login
  smoke-check notes below do not verify these Module 4 workflows. Automated
  PostgreSQL tests use a disposable database and do not establish those live
  integration results.
- Step 8 verification ran the full backend suite: 37 tests, 0 failures,
  0 errors, and 0 skipped; backend packaging, the Angular production build,
  Compose validation, and API/frontend image builds passed. Step 9's
  `git diff --check` passed after removing the reported trailing blank line.
  These are prior verification results; documentation-only changes do not
  rerun those checks.

The Keycloak client allowlist contains only the Angular root and the exact
SPA routes (`/dashboard`, `/membership`, `/courses`, `/training`, `/nutrition`,
`/forbidden`, `/not-found`) plus `/silent-check-sso.html`; the post-logout
redirect is exactly `http://localhost:8088/`, and the allowed web origin is
exactly `http://localhost:8088`.

## Authentication and authorization

- The SPA uses the maintained official `keycloak-js` adapter with
  Authorization Code Flow, PKCE S256, in-memory adapter tokens, and
  a silent SSO-check iframe and initialization before route guards run. The public client has no secret,
  implicit flow and password grant are disabled, and only exact SPA redirect,
  logout, and web-origin URLs are configured.
- Angular attaches access tokens only to same-origin `/api/membership`,
  `/api/courses`, `/api/training`, and `/api/nutrition` requests. A failed
  refresh clears the local token once and returns the user to the public page;
  the SPA does not start a login/refresh redirect loop. `401` ends the local
  session, while `403` opens the forbidden page.
- Spring validates the JWT signature using the Docker-internal Keycloak JWKS
  URL and independently validates the browser-visible issuer
  `http://localhost:8080/realms/smart-gym` and the required
  `smart-gym-api` audience. Networking does not disable issuer or audience
  validation.
- `GET /api/membership/me` derives its subject, display name, and application
  roles from the validated JWT and returns only matching member/employee
  profile IDs plus onboarding status. Missing profiles do not create
  employees or confer roles.
- `GET /api/membership/members/{memberProfileId}/private` enforces access on
  the server: members can read only their own record, coaches only assigned
  members, and admins may read any member record. Hiding a link in Angular is
  not used as authorization. Other module API prefixes also require their
  application roles.
- The development-only profile provisioning endpoint is absent unless the
  Spring `dev` profile is enabled and additionally requires the
  `PROVISIONING_TOKEN`. Do not enable that profile or expose that endpoint in
  production.

For production, terminate HTTPS at a trusted reverse proxy, configure the
public HTTPS Keycloak issuer and exact HTTPS redirect/logout/web-origin
allowlists, configure SMTP before enabling recovery, use managed secrets,
remove development ports and provisioning, and configure the internal JWKS
URL to reach the Keycloak deployment while preserving exact issuer and
audience validation. Never use the development HTTP Compose setup in
production.

## Routes and module ownership

The SPA includes `/` (public landing), `/dashboard`, `/membership` (M1),
`/courses` (M2), `/training` (M3), `/nutrition` (M4), `/forbidden`, and
`/not-found`. Refreshes are handled by the Nginx SPA fallback. The dashboard
shows the backend identity/profile response, role-aware module navigation,
an account/session section, and logout. Route permissions are checked again
on the backend for API requests.

The demonstrated navigation policy is: `ADMIN` sees all four module areas
and an operations action; `COACH` sees membership, courses, training, and an
assigned-member action; `MEMBER` sees their membership, courses, training,
and nutrition areas. Coaches can list only their server-assigned members.
Members can read only their own private record; admins can read any member
record. These roles can overlap.

## Login acceptance status

The automated API tests cover identity/profile responses, missing/malformed
token `401`, module/admin/coach role checks, onboarding without profile
creation, and member/coach/admin private-record access checks. A Docker
Compose smoke run and browser login/logout smoke test were performed with
development-only accounts.

- [x] Compose starts the core platform.
- [x] Angular loads in a browser.
- [x] Sign in opens the Smart Gym Keycloak realm login page.
- [x] Valid credentials return to Angular; invalid credentials show Keycloak
      feedback.
- [x] Dashboard shows the authenticated user and the backend `/me` response.
- [x] Angular calls `GET /api/membership/me` with an access token.
- [x] Role-aware navigation and direct protected-route authentication work.
- [x] Reloading a protected Angular URL works.
- [x] Missing/invalid token returns backend `401`; insufficient permissions
      return `403`.
- [x] Member A cannot access Member B's private record (automated API test).
- [ ] Expired sessions recover without a redirect loop (not explicitly
      simulated in the browser).
- [x] Logout ends the Keycloak application session and returns to `/`.
- [x] Protected content cannot be accessed after logout.

SMTP password recovery and production HTTPS are not configured. The current
login smoke-check notes above do not establish live Module 4 browser,
database-persistence, or Groq integration.
