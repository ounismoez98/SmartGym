# Smart Gym

Smart Gym is a gym-management platform using Angular, Spring Boot, PostgreSQL,
and Keycloak. The repository includes the authenticated membership vertical
slice and a member-scoped Nutrition & Body Tracking module (M4 in the existing
project/module map). Courses and training API prefixes are protected but their
module services are not included.

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
initial PostgreSQL schema; V2–V6 add calorie-target mode, generation-context
snapshots, persistent dated plans, saved goals/preferences/hydration, and
feedback persistence.
New installations using a fresh, empty database apply the complete migration
sequence on first API startup; Hibernate validates the mapped schema and does
not modify it. If the Compose PostgreSQL volume already contains a database,
review the existing database procedure below before starting the API.

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
journal entries, body measurements, profiles, nutritional calculations,
persistent meal plans, hydration, and an optional Groq meal-plan assistant.
Courses and training remain
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
   Use the Nutrition workspace's Overview, My Plan, My Progress, Food Library,
   and Food Journal views. New members can complete the six-step profile setup;
   saved profile and goal values use the authenticated nutrition profile API.
   Choose automatic daily calories to estimate an adult target from age,
   height, current weight, activity, goal, and the optional Mifflin–St Jeor
   equation input; or choose a manual target. Automatic estimates recalculate
   when the profile is saved and are not medical advice. The Nutrition page
   includes daily totals, profile-based BMI and saved-target macro estimates,
   food and meal-plan CRUD, journal CRUD, and measurement history. Local
   illustrated meal cards are used because this repository has no food-photo
   assets; the UI does not fetch unstable remote images.
   Members generate profile-based recipe suggestions on their own Nutrition
   page, review ingredients, quantities, preparation instructions and
   estimated nutrition, then save daily or seven-day plans to their account.
   Saved plans have dated days and persisted meals; members can regenerate one
   day or replace one meal. Coaches can open **Coach Nutrition** to review and
   edit only assigned members' saved plans and foods. Coaches do not generate
   or approve member plans. Assignment, ownership and saved generation
   restrictions are checked server-side. Profile targets, goal estimates,
   separate allergy/intolerance/dislike/ingredient preferences, cooking
   preferences and drinking-water logs are persisted through authenticated
   APIs. Shopping lists and alternatives remain review-only and are not
   persisted. The AI assistant is optional; see its configuration below.

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
schema, with an explicit completion budget. It requests exactly three concise
meals per generated day and omits shopping-list and alternative suggestions
from the model response to reduce avoidable output. Those suggestions remain
unavailable for AI-generated drafts; saved-plan behavior is unchanged. See
Groq's [Structured Outputs documentation](https://console.groq.com/docs/structured-outputs).
If Groq specifically rejects the strict schema format with HTTP 400/422, the
backend retries once using JSON-object mode. It then applies the same DTO,
numeric, meal-type, serving, size, and restriction checks before returning
anything; this fallback does not bypass application validation. A provider
error explicitly identifying completion-token exhaustion is not retried, and
is reported separately from a strict-schema compatibility rejection.
Groq's response is read from `choices[0].message.content`, which must contain
the plan JSON. The application then performs its own DTO, numeric, meal-type,
serving, size, and restriction checks. Strict JSON mode does not replace
application validation.

Meal-plan prompts generate complete recipes whether or not their ingredients
exist in the saved food library, with ingredient quantities and preparation
instructions. Saved foods are optional matches and must not distort the recipe.
The model also estimates a complete meal serving's weight, calories, protein,
carbohydrates, fat, fiber, and sodium for coach review.
Prompts favor lean protein, vegetables, legumes, whole grains, and lighter
cooking methods while limiting deep-frying, processed meats, pastries, and
excess salt. These are generation preferences, not a medical or nutritional
guarantee; review meals and estimates before saving.

Saved goal, calorie target, dietary style, supported food preferences,
explicit request restrictions, and optional member-owned food names/nutrition
are sent as generation context. JWTs, member subjects, profile age, height,
and weight are not sent. Do not add sensitive personal data to the prompt.

If `GROQ_API_KEY` is empty, generation returns `UNAVAILABLE` without a
network call. The model default is `openai/gpt-oss-20b`; override it with a
model currently available to your account using `GROQ_MODEL`. Authentication
errors, rate limits, timeouts, network failures, and malformed responses
produce non-secret unavailable messages. HTTP 400/422 responses point to model
availability and strict JSON Schema support; 404 identifies an unavailable
model, 413 identifies an oversized request, and 5xx responses are reported as
temporary provider failures. For 429 rate limits, the API includes a bounded
`Retry-After` hint when Groq supplies one and otherwise advises checking
account usage/model limits. Provider response bodies are not returned to the
member. The call continues to run on the existing bounded asynchronous executor. No live Groq request has been made
as part of this implementation; generation against an active account/model
remains unverified.

Groq-hosted model output is untrusted. Explicit and saved restrictions,
including the default pork and pork-derived ingredient exclusion, are checked
before a suggestion is returned and again when a member saves a plan or a
member/assigned coach edits a saved meal. Each saved plan retains its
generation context; later profile changes do not rewrite that context. These
checks do not establish allergy safety or medical suitability. Nutrition
remains an estimate and requires member review before saving.
Inference runs outside servlet request threads on a bounded pool (up to four
active calls and eight queued requests). The existing plan-generation route
keeps its response shape; saturated requests return an explicit unavailable
response instead of adding unbounded work.
Review every meal before saving. Daily and weekly plans use a persisted plan,
dated-day, meal and meal-item relationship. The older individual meal-plan
records remain available for compatibility. Shopping lists and alternatives
are shown for review but are not persisted.

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
| `POST /profiles` | Create or update the caller's latest profile, including goal, current/target weight, optional desired date, dietary style, allergies, intolerances, dislikes, preferred ingredients, budget and preparation preferences. Identity comes from the JWT. Targets require an adult profile, valid current measurements, a compatible supported goal, and a target not below the adult BMI 18.5 screening threshold. |
| `GET /profiles` | Read the caller's latest profile; returns `404` when none exists. |
| `GET /calculations/goal-timeline` | Return a deterministic approximate range for supported adult weight-loss goals only; includes the 0.25–0.5 kg/week assumptions and comparison with an optional requested date. Other goals, minors, and missing/invalid inputs return an unavailable response. |
| `POST /hydration` | Add a drinking-water log (1–2,000 mL). The server supplies the timestamp if omitted; member identity is derived from the JWT. |
| `GET /hydration?date=YYYY-MM-DD` | Read one member-owned UTC-day total, logs and the general estimated 2,000 mL/day target. This tracks drinks, not moisture from food. |
| `GET /hydration/history?from=YYYY-MM-DD&to=YYYY-MM-DD` | Read the caller's bounded hydration history. |
| `PUT /hydration/{logId}` / `DELETE /hydration/{logId}` | Correct or remove an owned log, with amount, timestamp and daily-total validation. |
| `GET /calculations?foodIds={id}&servings={n}` | Sum calories, macros, fiber, and sodium for the supplied owned food IDs. Repeat `foodIds` and `servings` parameters for multiple foods. Servings are optional (default one per food); when supplied, there must be exactly one positive finite quantity per ID. |
| `GET /calculations/bmi` | Calculate BMI from profile kilograms and centimetres converted to metres. Returns `422` for a known minor or missing/invalid height or weight. This is a screening measurement, not a diagnosis. |
| `GET /calculations/macros` | Estimate protein (1.6 g/kg), fat (25% of the saved calorie target), and carbohydrates from remaining calories. Requires a profile (`404` if absent) with positive saved weight and target calories; returns `422` if inputs are missing or the target cannot support nonnegative carbohydrates. This uses the manually saved target, not TDEE. |
| `GET /calculations/energy` | Calculate an adult Mifflin–St Jeor estimate when age, height, weight, equation selection, supported activity and goal are available; otherwise return an unavailable response with the missing requirement. This is an estimate, not medical advice. |
| `GET /food-journal/daily?date=YYYY-MM-DD` | Return the caller's daily totals and meal-level calories, macros, fiber, and sodium for the required date, scaled by logged servings. |
| `POST /assistant/plan` | Request a legacy AI suggestion. The UI sends request restrictions; profile, saved preferences and food context come from the authenticated caller. An available response includes a server-issued generation-context ID. Returns `UNAVAILABLE` when not configured, unreachable, malformed, or rejected; executor saturation/failure is surfaced. |
| `POST /assistant/plan/save` | Save a reviewed legacy suggestion. The server resolves the one-time generation context for the authenticated member and rechecks its restrictions rather than trusting save-request restrictions. |
| `POST /assistant/plans/draft` | Generate a daily or seven-day suggestion for the authenticated member, returning structured dated days and meals with quantities, instructions and supported nutrition estimates. |
| `POST /plans` | Save the member-reviewed draft as a persistent daily or weekly plan with its immutable generation context. |
| `GET /plans` / `GET /plans/{planId}` | List or retrieve the caller's saved plans and dated meals. |
| `POST /plans/{planId}/days/{date}/regenerate` | Regenerate only an owned plan day using its original context and expected plan version. |
| `POST /plans/{planId}/meals/{mealId}/replace` | Replace one meal while preserving other plan content, using the plan's saved context and version. |

### Coach meal-plan review

Coach nutrition routes are under `/api/nutrition/coach` and require the
`COACH` application role. Each operation resolves the member profile ID to its
authenticated member subject and checks the coach assignment server-side:

| Method and path | Behavior |
| --- | --- |
| `GET /members/{memberProfileId}/foods` | List foods belonging to an assigned member. |
| `PUT /members/{memberProfileId}/foods/{foodId}` | Update an assigned member's existing food; the food must belong to that member. |
| `DELETE /members/{memberProfileId}/foods/{foodId}` | Delete an assigned member's food only when it is not referenced by a journal entry or meal plan. |
| `GET /members/{memberProfileId}/meal-plans` | Review saved meal plans belonging to an assigned member. |
| `PUT /members/{memberProfileId}/meal-plans/{mealPlanId}` | Edit a plan belonging to an assigned member; every linked food must also belong to that member. |
| `GET /members/{memberProfileId}/plans` | List persistent plans for an assigned member. |
| `PUT /members/{memberProfileId}/plans/{planId}/days/{date}/meals/{mealId}` | Edit a meal in an assigned member's saved plan. The backend checks assignment, ownership, expected version and original plan restrictions. |

Plan-specific generation restrictions are durably stored with the associated
context and rechecked during save, day regeneration, meal replacement and
coach edits. Coaches see suggestions only after a member saves them; there is
no separate persisted draft/review state. Shopping lists and alternatives are
not persisted. Restriction checks do not guarantee medical or allergy safety.

All meal-type values are `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`,
`PRE_WORKOUT`, or `POST_WORKOUT`. Profile goals and dietary style are stored as
text; the UI offers weight loss, maintenance, muscle gain and
balanced/vegetarian/vegan/pescatarian/other choices. Allergy, intolerance,
dislike and preferred-ingredient lists are stored separately.

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

To run the full backend test suite, package the application, and generate
JaCoCo coverage, run `mvn -B verify` from `backend`. The HTML report is written to `target/site/jacoco/index.html`;
machine-readable XML and CSV reports are written alongside it. For Docker
Maven execution from the repository root:

```powershell
$backend = (Resolve-Path .\backend).Path
docker run --rm --mount "type=bind,source=$backend,target=/workspace" `
  -w /workspace maven:3.9-eclipse-temurin-21 mvn -B verify
```

The opt-in `PostgresNutritionIntegrationTest` requires an isolated PostgreSQL
database named `smartgym_it` and username `smartgym_it`. It is skipped unless
`smartgym.postgres.it.url`, `.username`, and `.password` system properties are
provided. Never point these properties at the development or production
database. It runs Flyway against the supplied database; tests inspect data,
update and delete only their own rows, and verify migration behavior.

Final Module 5 verification (2026-10-25) ran `mvn -B verify` with the
standard H2 test profile: 78 tests discovered, 72 passed, 0 failed, 0 errors,
and 6 opt-in PostgreSQL tests skipped. JaCoCo measured 89.31% line coverage
(1,646/1,843), 60.64% branch coverage (724/1,194), and 88.95% instruction
coverage (9,845/11,068) across application classes. The six PostgreSQL tests
were then run separately against a fresh disposable PostgreSQL 16 database:
6 passed, 0 failed, 0 errors, 0 skipped. These tests do not verify the
running Compose API, live Keycloak token validation, or Groq inference.

The frontend test suite is configured and runs with mocked HTTP and
authentication dependencies; it does not call the live API, Keycloak, or
Groq. The production frontend build remains `npm run build`. For local host
builds, the backend requires Java 21 and Maven; the frontend requires Node.js
24 and npm.

Final frontend verification (2026-10-25): `CHROME_BIN=/usr/bin/chromium npm
run test:coverage` in Node.js 24 with headless Chromium executed 56 tests:
56 passed, 0 failed, 0 skipped. Coverage was 66.26% statements (552/833),
47.35% branches (161/340), 68.02% functions (183/269), and 68.95% lines
(513/744). The HTML report is
`frontend/coverage/smart-gym/index.html`. The frontend production build
(`npm run build`) also passed. These are frontend unit/component and mocked
authentication checks, not live browser-to-Keycloak or API end-to-end tests.

### Performance and load checks

Reproducible k6 scripts for read-only API baseline/concurrency, expected
unauthenticated errors, gated read-only ramp testing, and separately gated
single-request Groq latency are in [`performance/`](./performance/README.md).
The scripts refuse loopback targets and the known shared Compose ports
8080/8081/8088; the operator must still verify that any other target maps to
an isolated API, database, and identity provider.
The only available Compose stack during setup was the running development
stack, backed by the `smartgym` database; it was not used for load testing.
Actual performance results remain blocked until the dedicated isolated API,
database, and Keycloak test identity described in that guide are available.
Final verification parsed and inspected all four k6 scripts using `k6 inspect`
with non-production placeholder configuration. This validated their script
configuration only; it sent no HTTP requests and is not a load benchmark.

### Module status and limitations

- The Nutrition page has profile editing, food CRUD/search, meal-plan
  CRUD, journal CRUD/daily totals, measurement entry/history and a saved-data
  weight chart. The body illustrations are presentation-only and do not infer
  body composition.
- The persisted profile supports target weight/date, allergies,
  intolerances, disliked foods, preferred ingredients, budget, preparation
  time and cooking preferences, separately from dietary style. Timeline
  estimates are restricted to adult weight-loss goals and use an approximate
  0.25–0.5 kg/week reference; other goals and unsupported inputs are
  unavailable. Targets implying adult BMI below 18.5 are rejected. BMI is a
  screening measurement, not a diagnosis; profiles with unknown age are not
  treated as confirmed minors by the BMI endpoint.
- Energy estimates use adult Mifflin–St Jeor inputs when available; the
  optional equation selection, supported activity and goal are required.
  Macro estimates use saved calories, 1.6 g/kg protein and 25% calories from
  fat, with remaining calories allocated to carbohydrates. These are
  estimates, not personalized medical prescriptions.
- Journal entries store a food reference and serving quantity, not a nutrient
  snapshot. Since daily totals use the food's current nutrient values, editing
  a food can change recalculated totals for earlier journal dates.
- Daily and seven-day plans persist as plan/day/meal/item records, linked to
  their original generation context. Legacy individual meal-plan records
  remain compatible. Shopping lists and alternatives are not persisted.
- Hydration records persist drinking water separately from the food journal.
  The 2,000 mL daily target is a general estimate and excludes food moisture.
- No Groq API key has been configured or verified for inference. Live
  Keycloak-backed Nutrition browser/API integration and end-to-end persistence
  through the running Compose API have not been verified. The separate login
  smoke-check notes below do not verify these Module 4 workflows. Automated
  PostgreSQL tests use a disposable database and do not establish those live
  integration results.
- Groq tests use mocked HTTP responses and do not prove live inference.
  Synthetic JWT/MockMvc tests do not prove Keycloak service integration.
  No deployed API/browser end-to-end run or load benchmark was performed.
  k6 scripts have explicit isolated-target and overload/live-Groq safety
  gates; validated scripts are not performance results.
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
