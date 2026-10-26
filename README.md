# Smart Gym

Smart Gym is a gym-management platform. This repository includes the M1
membership service and M3 training service: Keycloak-backed Angular sign-in,
membership management, subscriptions, payments, coach assignments, training
programmes and workouts, RabbitMQ events, and AI-assisted workflows.

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
| Keycloak | `http://localhost:8180` |
| Realm | `gym` |
| Business API | `http://localhost:8088/api/...` (proxied by Nginx) |
| OpenAPI JSON | `http://localhost:8081/v3/api-docs` |
| RabbitMQ management | `http://localhost:15672` |
| Groq chat completions API | `https://api.groq.com/openai/v1/chat/completions` |

Compose services are `postgres`, `keycloak-db`, `keycloak`, `rabbitmq`,
`api`, `training-db-init`, `training-service`, and `frontend`. The optional
`ollama` service is available with the `local-llm` Compose profile. The Spring API and PostgreSQL are also
exposed on host ports `8081` and `5433` for local development and
provisioning. RabbitMQ exposes AMQP on port `5672` and its management
interface on `15672`. `keycloak-db` is internal to the Compose network and
has no host port. Browser API calls should use the Nginx `/api/...` URLs.

### Prerequisites

- Docker Desktop installed and running.
- Java 21 and Maven, and Node.js, only when developing or running the backend
  or frontend outside Docker.

### Configure local environment

1. Create the local environment file:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Replace the placeholder values in `.env` with unique local-only
   credentials. The following are the variables in `.env.example`:

   | Variable | Role | Required by Compose |
   | --- | --- | --- |
   | `KEYCLOAK_ADMIN_USERNAME` | Keycloak local administrator username | Yes |
   | `KEYCLOAK_ADMIN_PASSWORD` | Keycloak local administrator password | Yes |
   | `KEYCLOAK_PROVISIONER_SECRET` | Secret for the API's limited Keycloak account-provisioning client | Yes |
   | `POSTGRES_PASSWORD` | Password for the local PostgreSQL databases | Yes |
   | `PROVISIONING_TOKEN` | Token for the development-only profile provisioning API | Yes |
   | `DEV_ADMIN_PASSWORD` | Local-only password for `gym-admin` | Provisioning script |
   | `DEV_COACH_PASSWORD` | Local-only password for `gym-coach` | Provisioning script |
   | `DEV_MEMBER_A_PASSWORD` | Local-only password for `member-a` | Provisioning script / E2E fallback |
   | `DEV_MEMBER_B_PASSWORD` | Local-only password for `member-b` | Provisioning script |
   | `RABBITMQ_USER` | RabbitMQ username | Yes |
   | `RABBITMQ_PASSWORD` | RabbitMQ password | Yes |
   | `TRAINING_DB_USER` | Training database username | No |
   | `TRAINING_DB_PASSWORD` | Training database password | No |
   | `OLLAMA_MODEL` | Optional local training AI model | No |
   | `LLM_BASE_URL` | OpenAI-compatible API base URL; defaults to Groq | No |
   | `LLM_MODEL` | Groq model ID; defaults to `openai/gpt-oss-20b` | No |
   | `LLM_API_KEY` | Groq API key; replace the placeholder with a locally stored key | Yes |
   | `LLM_TIMEOUT_SECONDS` | LLM request timeout in seconds; defaults to `30` | No |

If the administrative assistant reports a provider error, its response
identifies whether the configured key was rejected or the model/API endpoint
was not found. Replace revoked or exposed keys in the ignored `.env`, confirm
`LLM_MODEL` and `LLM_BASE_URL` match the selected provider, and recreate the
API container. Never put provider response bodies or API keys in tracked files
or logs.

   Generate a local provisioning token with:

   ```powershell
   [guid]::NewGuid().ToString("N")
   ```

   Put the generated value in `.env` for `PROVISIONING_TOKEN`. `.env` is
   never committed; confirm it is ignored by Git:

   ```powershell
   git check-ignore .env
   ```

3. Before starting the services, validate the Compose configuration:

   ```powershell
   docker compose config --quiet
   ```

4. Start the platform and inspect the service state:

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

3. Wait for Keycloak and the API to become healthy, then provision the four
   development accounts and their business profiles:

   ```powershell
   docker compose logs api --tail 50
   ```

### Provision development accounts

Keycloak credential validation and invalid-credential messages stay on the
Keycloak-hosted login page. The realm uses a small Smart Gym CSS theme.
MailHog is configured as the local SMTP sink for assistant reminders;
password-recovery email is intentionally disabled and is not operational.

The Compose stack routes `/api/membership`, `/api/courses`, and
`/api/nutrition` to the secured membership API while preserving the full
prefix. Nutrition endpoints provide member-scoped foods, meal plans, food
journal entries, body measurements, profiles, nutritional calculations,
persistent meal plans, hydration, and an optional Groq meal-plan assistant.
The `/api/training` prefix is routed to the separate training-service; the
remaining routes use the membership API.

The Keycloak client allowlist contains only the Angular root and the exact
SPA routes (`/dashboard`, `/membership`, `/courses`, `/training`,
`/training/programmes`, `/training/workouts`, `/nutrition`, `/forbidden`,
`/not-found`) plus `/silent-check-sso.html`; the post-logout
redirect is exactly `http://localhost:8088/`, and the allowed web origin is
exactly `http://localhost:8088`.
For an already-imported local realm, update the
`smart-gym-frontend` redirect URI list in Keycloak Admin Console because
Keycloak does not overwrite an existing realm on import.

Member and employee account creation is available to administrators from the
Membership screen. It creates the Keycloak login, assigns the `MEMBER` or
`COACH` role, and links the Keycloak-generated user ID to the matching profile.
Set a unique local `KEYCLOAK_PROVISIONER_SECRET` in `.env` before starting the
stack, then run `scripts/provision-dev.ps1` with the required local
administrator and development-account variables in the PowerShell session.
The script creates the confidential `smartgym-provisioner` client and grants
only the user-management roles required by the API. Newly created users must
change their temporary password at first login. Do not use the Keycloak admin
password as the provisioner secret.

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
  `http://localhost:8180/realms/gym` and the required
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
- Membership endpoints delivered under `/api/membership/members`:
  `POST` (ADMIN), paginated/filterable `GET` list (ADMIN or assigned-member
  scope for COACH), `GET /{id}` (ADMIN, assigned COACH, or owning MEMBER),
  `PUT /{id}` (ADMIN; MEMBER may change only their own email and phone), and
  `POST /{id}/deactivate` (ADMIN). Deactivation is refused while an ACTIVE
  subscription exists. There is no member `DELETE` endpoint.
- Employee endpoints delivered under `/api/membership/employees`:
  `POST` (ADMIN), paginated/filterable `GET` list (ADMIN), `GET /{id}`
  (ADMIN or the employee's own COACH profile), `PUT /{id}` (ADMIN), and
  `POST /{id}/deactivate` (ADMIN). There is no employee `DELETE` endpoint.
- Account-creation endpoints under `/api/membership/accounts`: `POST /members`
  and `POST /employees` are ADMIN-only. Each request includes a username,
  temporary password, and profile details; the server creates and assigns the
  Keycloak account before linking its generated subject to the profile. If
  profile creation fails, the newly created Keycloak account is removed.
- Coach assignment endpoints: `POST
  /api/membership/employees/{coachId}/members/{memberId}` (ADMIN) assigns an
  active coach and member; `POST
  /api/membership/employees/{coachId}/members/{memberId}/unassign` (ADMIN)
  ends an active assignment without deleting its history; and `GET
  /api/membership/employees/{coachId}/members` is available to ADMIN or that
  coach. Keycloak accounts are not created or deactivated by these endpoints.
- Subscription plan endpoints under `/api/membership/plans`: `POST`, `PUT`,
  and `DELETE` are ADMIN-only; paginated/filterable `GET` list and `GET /{id}`
  are available to authenticated application roles. Deactivate a plan with
  `PUT /{id}` and `"active": false` (recommended); a plan referenced by
  subscription history cannot be deleted.
- Subscription endpoints under `/api/membership/subscriptions`: ADMIN creates,
  filters, updates, and cancels subscriptions; MEMBER can list and read only
  their own. Creation starts immediately and calculates `endsAt` from the
  selected plan duration and requires an ACTIVE member. `PUT /{id}` changes
  only `planId` on an ACTIVE subscription; `startsAt` and `endsAt` remain
  unchanged, and the audit record contains the old and new plan IDs.
  `POST /{id}/cancel` requires a reason. Only one ACTIVE subscription per
  member is allowed; a daily UTC task marks due subscriptions EXPIRED, after
  which a renewal can be created. There is no subscription `DELETE` endpoint.
- Payment endpoints under `/api/membership/payments`: `POST` recording and
  `POST /{id}/corrections` are ADMIN-only; paginated
  `GET` list (filters: `memberId`, `status`, `from`, `to`, `page`, `size`) and
  `GET /{id}` are available to ADMIN or the owning MEMBER. A payment may omit
  `subscriptionId`; when supplied, it must belong to `memberId`. Correction
  creates a linked `RECORDED` entry, marks the original `CORRECTED` without
  changing its amount, and preserves the payment date, currency, and method.
  Detail responses include the correction chain. `GET
  /api/membership/members/{id}/payments/total` returns the sum of `RECORDED`
  amounts for ADMIN or that member. There are no payment `PUT` or `DELETE`
  endpoints.
- Administrative assistant endpoints under `/api/membership/assistant` are
  ADMIN-only: `POST /explain` answers from validated policies and plan
  conditions; `GET /renewals?days=N` summarizes SQL-selected renewals;
  `POST /reminders` creates a stored `DRAFT`; `PUT /reminders/{id}` edits a
  draft; and `POST /reminders/{id}/confirm` or `/reject` changes its final
  state. Confirmation sends the message to the member email read from the
  database. Local SMTP uses MailHog at `http://localhost:8025` (SMTP port
  `1025`).
- The assistant does not send member first or last names, email addresses,
  phone numbers, or IDs to Groq. Reminder prompts use the `{{FIRST_NAME}}`
  marker, which the API replaces with the member's first name only after the
  generated date and amount pass validation; a missing name becomes
  `Bonjour`. Explain questions and policy/plan text are scanned for email
  addresses, phone-like number sequences, and UUIDs, which are masked before
  transmission. This does not reliably detect a person's name written in
  free-form text: do not enter personal data, and keep validated gym policies
  general and free of personal information. Generated reminder dates and
  amounts are checked against database facts; invalid or incomplete output
  becomes an editable draft marked `[À COMPLÉTER]`.
- The `/membership` Angular workspace provides role-aware member, subscription,
  payment, employee/assignment, assistant, and member-profile screens with
  reactive forms, loading/empty/error states, and server-side authorization.
  Member search includes pagination; navigation visibility is only a UX aid,
  not an authorization boundary.
- Membership, employee, subscription, and payment changes publish the event
  types documented in [docs/events.md](docs/events.md) to RabbitMQ after
  commit. Delivery is best effort; failed broker publication is logged and is
  not retried.
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
`/courses` (M2), `/training` (M3), `/training/programmes`,
`/training/workouts`, `/nutrition` (M4), `/forbidden`, and `/not-found`.
Refreshes are handled by the Nginx SPA fallback. The dashboard
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

Backend API tests use MockMvc, simulated JWTs, and H2; they do not verify live
Keycloak signatures. The latest checks: Maven reported 209 tests with 0
failures and 10 Docker-dependent tests skipped; Angular unit tests passed 61
tests; and the authenticated Chromium Playwright membership flow passed
against local Keycloak, Compose, MailHog, and a test-only LLM mock.

| Acceptance item | Covered by automated test | Browser/Compose check |
| --- | --- | --- |
| Compose starts the core platform | No | Yes — services running in this session |
| Angular loads in a browser | No | Not verified in a browser (HTTP root returned 200) |
| Sign-in opens the Keycloak login page; valid and invalid credentials behave as expected | No | not re-verified |
| Dashboard displays the authenticated identity and membership API response | No | not re-verified |
| Angular calls `GET /api/membership/me` with an access token | No | not re-verified |
| Role-aware navigation, protected routes, and reload behavior | No | not re-verified |
| Missing/malformed token returns `401`; role and ownership denials return `403` | Yes — MockMvc tests | not re-verified live |
| Member A cannot read another member's private record | Yes — MockMvc tests | Yes — Playwright E2E received `403` |
| Logout ends the application session and protected content is inaccessible | No | not re-verified |

The account-provisioning browser flow verifies member and coach creation,
Keycloak-generated ID linking, and the required password change at first
login. The assistant explanation flow was checked against the local mock LLM;
no live Groq request was made. SMTP password recovery, production HTTPS, and
a full browser simulation of token expiry are not implemented.

## Project status

The delivered scope includes the Keycloak-backed authentication and
authorization vertical slice, member and employee CRUD, coach assignments,
subscription plans and subscriptions, payments with traceable corrections,
post-commit RabbitMQ events, the administrative assistant and reminder
confirmation flow, and M3's separate training service and Angular workflows.
M2 courses and M4 nutrition service integrations remain incomplete; the
nutrition screens and membership API currently provide the implemented
nutrition features.

The following are not yet delivered:

- Membership-service RabbitMQ consumers (the M3 programme-generation worker
  is implemented).
- SMTP password recovery and production HTTPS.
- The separate M2 courses service and M4 nutrition service integrations.

Flyway manages the PostgreSQL schema, and Hibernate uses `ddl-auto: validate`.
Development sample plans and policies are loaded only by the `dev` profile.
In the `dev` profile only, Flyway permits pending lower-numbered migrations to
run after the development seed migration, allowing existing local volumes to
receive migrations added after their initial seed.
Membership schema changes retain migration versions V1-V7. The nutrition
baseline and subsequent nutrition/feedback changes use V8-V13 so the merged
Flyway history has one migration per version. A migration callback also
normalizes the legacy coach-assignment constraint before V3 when upgrading a
pre-Flyway development database.
Current backend tests include MockMvc tests with simulated JWTs and H2, plus
Testcontainers PostgreSQL migration, active-subscription uniqueness, and
payment deletion-guard tests. The latest full Maven run reported 209 tests:
0 failures, errors, or skips. Testcontainers 1.21.4 is pinned explicitly
because the Spring Boot-managed 1.20.6 Docker client could not communicate
with the current Docker Desktop engine. The six nutrition PostgreSQL
integration tests are intentionally gated by `smartgym.postgres.it.url` and
require a dedicated disposable database and user both named `smartgym_it`;
without these properties they are skipped to avoid modifying an arbitrary
database. The full run supplied that isolated database. Separate focused
tests cover provisioning Keycloak accounts and linking the generated ID to a
profile. Additional tests verify safe provider diagnostics for rejected keys
and unavailable models/endpoints. Angular unit tests passed 61 tests, and the
production build passed with an initial-bundle budget warning. Three
Playwright Chromium tests passed: the admin membership workflow (account
creation, payment correction, assistant/reminder flows, and owner denial),
public routes, and admin/coach/member route authorization.
Not verified: Groq against the real remote provider, local Ollama inference,
or a manual functional review of every screen and workflow. The route smoke
tests verify navigation and role access but do not replace that full manual
review.
The OpenAPI JSON document is publicly readable; the described API operations
still require a valid bearer JWT and enforce their normal role/ownership rules.

Run the backend tests from the `backend` directory:

```powershell
mvn -B "-Dsmartgym.postgres.it.url=jdbc:postgresql://localhost:5432/smartgym_it" "-Dsmartgym.postgres.it.username=smartgym_it" "-Dsmartgym.postgres.it.password=$env:SMARTGYM_IT_PASSWORD" test
```

`SMARTGYM_IT_PASSWORD` must be set for a disposable PostgreSQL test database
named `smartgym_it` and owned by user `smartgym_it`. The nutrition integration
tests reset their own rows and exercise migrations, so do not point this
property at a development or shared database. With Docker Desktop available,
the separate Testcontainers migration tests run automatically.

The Playwright scenario uses the local Compose stack, MailHog, and a test-only
mock OpenAI-compatible LLM; it does not contact Groq. Put the same local
development passwords used by `scripts/provision-dev.ps1` into the ignored
repository-root `.env` as `DEV_ADMIN_PASSWORD` and `DEV_MEMBER_A_PASSWORD`.
The Playwright configuration loads only the admin, coach, and member login
credentials (or corresponding `E2E_*_PASSWORD` overrides) from `.env`; it
does not load `LLM_API_KEY` into the test process. Usernames default to
`gym-admin`, `gym-coach`, and `member-a`, and can be overridden with
`E2E_ADMIN_USERNAME`, `E2E_COACH_USERNAME`, and `E2E_MEMBER_USERNAME`. Then
launch the test stack and install Chromium once:

```powershell
docker compose -f docker-compose.yml -f frontend/e2e/docker-compose.e2e.yml up --build -d
npx --prefix frontend playwright install chromium
npm --prefix frontend run test:e2e
```

If credentials are missing, the test reports all required password variable
names without displaying their values. Do not put credentials in tracked
files. The E2E test creates a member login through the Membership UI, confirms
that Keycloak's generated ID is linked to the profile, then checks that the
existing `member-a` identity receives `403` when requesting that profile.
The route-smoke scenario also loads every SPA route as admin, coach, and
member, checks each role's allowed and forbidden pages, and fails on uncaught
browser errors or API 5xx responses.
Stop the test stack without deleting local data with:

```powershell
docker compose -f docker-compose.yml -f frontend/e2e/docker-compose.e2e.yml down
```

Docker Desktop must be running for the Testcontainers PostgreSQL tests.

Run the frontend checks from the repository root:

```powershell
npm --prefix frontend run test -- --run
npm --prefix frontend run build
```

## Troubleshooting

- `required variable ... is missing a value`: `.env` is missing or the
  referenced variable is empty. Create it from `.env.example` and fill in
  the required values.
- The `gym` realm is missing or does not reflect changes to the realm export:
  Keycloak imports the realm only on its first startup with an empty data
  volume. To reset the local Keycloak and application data, run:

  ```powershell
  docker compose down -v
  docker compose up --build -d
  ```

  `docker compose down -v` deletes local data, including database contents
  and data in the optional Ollama volume.
- The provisioning script exits immediately: one or more required variables
  are absent from the current PowerShell session. Load `.env` as described
  above and set `PROVISIONING_TOKEN`, `DEV_ADMIN_PASSWORD`,
  `DEV_COACH_PASSWORD`, `DEV_MEMBER_A_PASSWORD`, and
  `DEV_MEMBER_B_PASSWORD`.
- To inspect service logs:

  ```powershell
  docker compose logs <service> --tail 50
  ```
## Module 3 — training-service (Phases 2–4)

M3 is a separate Spring Boot 3.4.5 / Java 21 service using the existing
`gym` issuer, `smart-gym-api` audience, and Keycloak realm-role mapping.
It owns a separate `training_db`; Hibernate uses schema validation and Flyway
owns DDL. The service does not connect to M1's database.

- `GET /api/training/exercises`: paginated active catalogue; supports `name`,
  `muscleGroup`, `difficulty`, and `equipmentCategory` filters. Page size is
  limited to 50.
- `GET /api/training/exercises/{id}`: retrieve a catalogue item, including an
  archived item for historical reference.
- `POST /api/training/exercises`, `PATCH /api/training/exercises/{id}`, and
  `POST /api/training/exercises/{id}/archive`: coach/admin-only management.
  Updates accept a `version`; a stale version returns HTTP 409.
- Exercise endpoints return DTOs. Invalid JSON/enums/fields return HTTP 400;
  unauthenticated and insufficient-role requests return HTTP 401 and 403.
- Exercise catalogue enum values are `CHEST`, `BACK`, `SHOULDERS`, `BICEPS`,
  `TRICEPS`, `CORE`, `QUADRICEPS`, `HAMSTRINGS`, `GLUTES`, `CALVES`,
  `FULL_BODY`, `OTHER`; difficulties are `BEGINNER`, `INTERMEDIATE`,
  `ADVANCED`; equipment categories are `BODYWEIGHT`, `DUMBBELL`, `BARBELL`,
  `KETTLEBELL`, `CABLE`, `MACHINE`, `RESISTANCE_BAND`, `OTHER`.
- `POST /api/training/programmes`, `GET /api/training/programmes[/{id}]`,
  and `PATCH /api/training/programmes/{id}` provide coach-authored drafts
  with ordered sessions and typed exercise prescriptions. A prescription
  must specify either repetitions or seconds, not both. `PATCH` replaces the
  draft details/sessions and requires the current `version`.
- Sessions and items also have programme-scoped create, update, delete, and
  order endpoints under `/api/training/programmes/{id}/sessions`. Each
  mutation requires the current programme version (in the request body for
  create/update/order, or the `version` query parameter for delete); use the
  returned version on the next write. Ordering requests must include every
  existing scoped ID exactly once. Nested mutations preserve unaffected
  session/item identifiers.
- `POST /api/training/programmes/{id}/submit`, `/reviews`, `/publish`,
  `/archive`, and `/revisions` implement the review/publish workflow.
  Publishing requires approval of the current revision. Published content
  is immutable; `/revisions` creates a linked draft and leaves its source
  published. Review history remains attached to the original programme.
- Members can list/read their own published programmes only. Coaches can
  manage and review programmes only for currently assigned members. An
  `ADMIN` role alone does not grant access to private programmes or logs.
- `POST /api/training/programme-generations` accepts coach-authorized goal,
  level, schedule, duration and equipment inputs and returns `202` with a job
  ID. `GET /api/training/ai-jobs/{id}` is restricted to coaches currently
  assigned to that job's member. Completion creates a linked `AI`-origin
  `DRAFT`; it is never automatically approved or published.
- Generation includes only a bounded set of active catalogue exercises
  compatible with requested equipment plus bodyweight exercises. Member
  names, email addresses, tokens and unrelated profile data are not sent to
  the model or in RabbitMQ command messages. Unknown, archived, duplicate,
  equipment-incompatible, malformed or out-of-range output fails the job.
- `POST /api/training/workout-logs`, `GET /api/training/workout-logs[/{id}]`,
  `PATCH`, and `DELETE` support member-owned logging and coach reads for
  assigned members. Programme/session IDs are checked against the member's
  published programme and selected session. Members can record multiple sets
  from a published session or create standalone workouts, then edit or delete
  their own logs.
- `GET /api/training/me/progress` and coach-only
  `GET /api/training/members/{memberId}/progress` return completed workout
  counts by ISO week, repetition totals, maximum explicitly recorded load,
  and loaded repetition volume (`load_kg × repetitions`). Timed sets and sets
  without a recorded load contribute no load or volume; no bodyweight is
  inferred.
- Stale catalogue, programme, and workout writes return HTTP 409 using
  optimistic locking/version checks. `/api/training/openapi` and
  `/api/training/swagger-ui` expose the API specification/docs; business
  operations still require a bearer token.
- Validation failures return HTTP 400 Problem Details; missing/invalid JWTs
  return HTTP 401; role/resource denials return HTTP 403; missing resources
  return HTTP 404; unavailable M1 identity/assignment checks return HTTP 503.
  Exercise and workout-history lists use zero-based `page` and bounded `size`.

Example draft request (UUIDs are illustrative; response includes `id`,
`version`, `revision: 1`, `status: "DRAFT"`, ordered sessions/items, and an
empty review history):

```json
{
  "memberId": "c6b12091-c34d-4d09-8e99-944b0f5cb8d0",
  "title": "Strength basics",
  "goal": "Build general strength",
  "experienceLevel": "BEGINNER",
  "durationWeeks": 4,
  "version": null,
  "sessions": [{
    "title": "Workout A",
    "notes": null,
    "items": [{
      "exerciseId": "10000000-0000-4000-8000-000000000002",
      "sets": 3,
      "repetitions": 8,
      "durationSeconds": null,
      "restSeconds": 60,
      "suggestedLoadKg": null,
      "notes": null
    }]
  }]
}
```

Example nested programme mutations (use the latest response `version` and the
session/item IDs returned by the service):

```json
{
  "version": 1,
  "session": {
    "title": "Workout B",
    "notes": null,
    "items": [{
      "exerciseId": "10000000-0000-4000-8000-000000000002",
      "sets": 3,
      "repetitions": 10,
      "durationSeconds": null,
      "restSeconds": 60,
      "suggestedLoadKg": null,
      "notes": null
    }]
  }
}
```

Send that body to `POST /api/training/programmes/{id}/sessions`; use
`PUT /api/training/programmes/{id}/sessions/order` with
`{"version":2,"orderedIds":["<session-id-1>","<session-id-2>"]}` to reorder.
Item mutations use the same versioned pattern under
`/sessions/{sessionId}/items`.

Example standalone workout request (member identity is resolved from M1;
clients must not send `memberId`):

```json
{
  "programmeId": null,
  "programmeSessionId": null,
  "performedAt": "2026-10-22T10:00:00Z",
  "notes": "Good session",
  "sets": [{
    "exerciseId": "10000000-0000-4000-8000-000000000002",
    "repetitions": 10,
    "durationSeconds": null,
    "loadKg": 12.5,
    "notes": null
  }]
}
```

Private-record permission summary:

| Operation | MEMBER | COACH | ADMIN only |
| --- | --- | --- | --- |
| Read active exercise catalogue | Own role | Own role | Own role |
| Manage exercise catalogue | No | Yes | Yes |
| Create/edit/review/publish programme | No | Assigned members only | No |
| Read programme | Own published only | Assigned members | No |
| Create/edit/delete workout log | Own member profile | No | No |
| Read workout log/progress | Own member profile | Assigned members | No |

### M1 identity and assignment integration

The existing M1 API provides the profile/assignment contract:
`GET /api/membership/me` maps the validated subject to its business profile;
`GET /api/membership/coach/assigned-members` returns that coach's current
assigned profile IDs. Training-service calls these M1 endpoints using the
incoming user's bearer token, so M1 continues to enforce identity and
assignment against its own database. Member IDs supplied in a programme
request are accepted only after the M1 assignment list confirms them. No
M1 tables are queried or changed, and no tokens are sent through messaging.

The dedicated `training_db` is provisioned by the idempotent
`training-db-init` Compose job using `TRAINING_DB_USER` /
`TRAINING_DB_PASSWORD`; the runtime service does not use M1's database
credentials. Compose defaults these to `training_service` /
`training-local-change-me` for development, so older `.env` files continue
to work. Override both values in `.env` for a local custom setup; never use
the development default in production. Flyway owns schema creation and
Hibernate uses `validate`.
On an existing Compose stack, the init job provisions the separate database
without deleting or recreating the existing PostgreSQL volume.

Start/rebuild the service with the normal command after setting the two
training database values in `.env`:

```powershell
docker compose --env-file .env up --build -d training-service frontend
```

Run the service tests and frontend production build:

```powershell
mvn -f training-service\pom.xml test
npm.cmd --prefix frontend run build
```

Verified on 2026-10-25: the training-service suite passed all 31 tests, the
M1 backend suite passed all 8 tests, the Angular production build passed, and
the current Compose configuration validates. The training-service Docker
image built and was previously started healthy with demo records. Live JWT
signature/issuer/audience checks and browser M3 acceptance remain unverified.

The complete training demo seed is repeatable and development-only. Rebuild
the API first so the development provisioning endpoint returns profile
identifiers via its existing provisioning contract, then run:

```powershell
docker compose up --build -d api training-service frontend
.\scripts\seed-training-demo.ps1
```

It uses Keycloak's admin API to find the existing dev accounts, sends their
profile/assignment setup through M1's protected development provisioning
endpoint, then seeds only `training_db`. It does not reset user passwords or
read/write M1's database. Re-running is safe and refreshes the deterministic
sample catalogue/programmes/logs.

The sample coach can inspect a published plan, a plan pending review, and a
draft for Member A, plus Member B's published starter plan. Sign in as
`gym-coach` to review/publish drafts; `member-a` and `member-b` can view their
own published plans. Member A has three sample logs across different dates,
with both explicit load/repetition and timed/bodyweight sets for progress
screens. Exercises include squat, push-up, dumbbell row, and timed plank.

After seeding, open `http://localhost:8088/training` and refresh if the SPA
was already open. As `gym-coach`, open **Programmes** to see draft, pending
review, and published states; submit the draft, approve the pending revision,
and publish it to exercise those transitions. As `member-a`, open **Programmes**
to see only the published plan and use **Log this session**; **Workout history**
also lets you inspect sample logs and progress or add/edit/delete standalone
and programme-session logs. As `member-b`, verify that only Member B's own
published plan is visible. The exercise catalogue supports coach/admin create,
edit, search/filter, and archive.

The asynchronous AI-generation and generated-image features are not
implemented, so no seeded records pretend to demonstrate them.

The programme screen supports multi-session, multi-item drafts with explicit
ordering, typed repetition/time prescriptions, and review/publication actions.
Members can log a completed published programme session, enter multiple
actual sets, edit/delete logs, and view weekly activity and per-exercise
maximum recorded loads.

### M3 workflow and delivery limits

Programme lifecycle:

```mermaid
stateDiagram-v2
  [*] --> DRAFT
  DRAFT --> PENDING_REVIEW: submit
  CHANGES_REQUESTED --> PENDING_REVIEW: resubmit
  PENDING_REVIEW --> APPROVED: approve current revision
  PENDING_REVIEW --> CHANGES_REQUESTED: request changes
  APPROVED --> DRAFT: edit draft invalidates approval
  APPROVED --> PUBLISHED: publish current revision
  PUBLISHED --> ARCHIVED: archive
  PUBLISHED --> PUBLISHED: create linked DRAFT record
```

Coach review/publication is server-enforced. New revisions are separate
linked draft records, so changing a draft cannot mutate the published source.

Training database relationships (membership IDs are external UUID references
and deliberately have no cross-database foreign keys):

```mermaid
erDiagram
  EXERCISE ||--o{ PROGRAMME_ITEM : prescribes
  TRAINING_PROGRAMME ||--o{ PROGRAMME_SESSION : contains
  PROGRAMME_SESSION ||--o{ PROGRAMME_ITEM : orders
  TRAINING_PROGRAMME ||--o{ COACH_REVIEW : records
  TRAINING_PROGRAMME ||--o{ WORKOUT_LOG : optional_source
  TRAINING_PROGRAMME o|--o| AI_JOB : generated_from
  AI_JOB ||--o{ TRAINING_OUTBOX : publishes_request
  PROGRAMME_SESSION ||--o{ WORKOUT_LOG : optional_source
  WORKOUT_LOG ||--o{ WORKOUT_SET : records
  EXERCISE ||--o{ WORKOUT_SET : performed
  EXTERNAL_MEMBER ||--o{ TRAINING_PROGRAMME : owns
  EXTERNAL_MEMBER ||--o{ WORKOUT_LOG : logs
```

### Phase 5 — asynchronous programme generation

The private RabbitMQ development broker is part of the core Compose stack
with a persistent volume, healthcheck, and configurable `smartgym` virtual
host. It is not published to the host. M3 declares its own durable topology
in `training-service`; the broker is intended to be the single shared
infrastructure deployment. This is the project-local development contract
implemented at Moez's direction, not independent M5 approval; confirm with
the shared M5 team before reusing it as a production/shared-environment
contract.

Messaging contract:

- Durable topic exchange `smartgym.training`; durable direct dead-letter
  exchange `smartgym.training.dlx`.
- Request route `training.programme-generation.requested.v1`, worker queue
  `training.programme-generation.worker.v1`; persistent JSON envelope with
  `messageId`, `correlationId`, `jobId`, `messageType`, `version`,
  `occurredAt`, and `payload`.
- API stores `AiJob` and its request in `training_db`, and the transactional
  outbox row in the same transaction. The outbox publisher waits for RabbitMQ
  publisher confirms before marking a row published; unconfirmed rows remain
  retryable.
- Worker messages are manually acknowledged only after a result/retry publish
  is confirmed. Model errors get up to three deliveries via a durable
  five-second retry queue; exhausted and invalid messages are routed to a
  durable dead-letter queue. Results use
  `training.programme-generation.completed.v1` or
  `training.programme-generation.failed.v1` and a durable result queue.
- Result handling is idempotent by `jobId`: a job can create at most one
  programme. The worker component does not update business entities; it
  returns result events to the training API. The worker has no separate inbox
  database because RabbitMQ durability/retry plus job-level result
  idempotency provide its delivery state; the API outbox is in the same
  transaction database as `AiJob`, as required for atomic job acceptance.
- No member profile ID, identity token, credential, name, email, or unrelated
  member data is included in model context or command payload. The model sees
  the stated goal/constraints and at most 50 active compatible exercises.

Start core services (including the single broker):

```powershell
docker compose up --build -d
```

If you already have a `.env` from before Phase 5, add `RABBITMQ_USER` and
`RABBITMQ_PASSWORD` to it using fresh local-only values; the updated
`.env.example` shows the required variable names. Do not replace an existing
`.env` blindly, since it may contain other local credentials.

Enable the optional local model runtime only when needed; no model is pulled
automatically:

```powershell
docker compose --env-file .env -f docker-compose.yml -f docker-compose.messaging.yml --profile ai up -d ollama
docker compose --env-file .env -f docker-compose.yml -f docker-compose.messaging.yml exec ollama ollama pull qwen2.5:3b
```

`OLLAMA_MODEL` in `.env` selects the configured model (replace the pull
argument to match it). Automated tests use
`DeterministicFakeProgrammeGenerator`; they do not need RabbitMQ or Ollama.
M3 owns the M3 prompt and output JSON Schema at
`training-service/src/main/resources/ai/prompts/programme-generation-v1.txt`
and `training-service/src/main/resources/ai/programme-generation-v1.schema.json`.
Session-duration validation estimates four seconds per repetition plus the
rest between sets; timed sets use their specified duration plus inter-set
rest. It is a configured plausibility bound, not an exact time prediction.

The generation UI is in the coach programme workspace. It submits a job,
polls the protected job endpoint, reports failure, and links a completed
unreviewed draft back to the programme list. AI generation does not publish
plans. Medical diagnosis, injury rehabilitation and treatment requests are
rejected with a limitation message.

### Phase 6 — workout voice companion and user-controlled SOS

The member workout page has a voice companion that answers questions from
verified aggregate workout facts for the signed-in member's last 28 days.
`POST /api/training/me/workout-insights` is member-role protected, validates a
question (maximum 300 characters), reads only that member's own recent logs,
and calculates workout, set, repetition, and explicitly loaded volume totals.
Only those aggregates and the question are sent to the configured local
Ollama model; log notes, member IDs, identity tokens, and raw member profiles
are not included. The application, not the model, calculates the metrics and
the deterministic encouragement. If Ollama is unavailable the API returns an
explicit service-unavailable error rather than a fabricated answer.

Voice input is one-shot and starts only after the member activates the
microphone control. The browser's speech-recognition provider may process
audio under that provider's own policy; SmartGym does not record or store
audio. Typed input is always available. Spoken output uses the browser's
built-in speech synthesis where available. Browser support, microphone
permissions, internet requirements and privacy behavior vary, so the typed
workflow remains the fallback. The text request and response use the local
Ollama configuration described above; no paid LLM API is required.

The SOS check-in is not an audio classifier, monitoring service, or emergency
dispatch feature. Only an explicit button press starts a local alarm and
30-second on-screen check-in. The user can cancel it, or can tap a `tel:`
link for a phone number they entered on that page. That number is neither
saved nor sent to the server. The timer never places a call automatically,
including when it expires. Alarm playback depends on the browser tab and
device; this feature is not a substitute for contacting local emergency
services. The assistant cannot diagnose pain or injury and is not a medical
professional.

Acceptance run (2026-10-25): the training-service suite passed 31 tests, the
membership suite passed 8 tests, the Angular production build passed, both
core and AI-profile Compose configurations validated, the training-service
image built, and `git diff --check` passed. The live core stack started
healthy. Member and coach sign-in, role-aware navigation, seeded catalogue,
programme/history views, coach assigned-member listing, and member workout
log create/edit/delete were exercised in the browser. A coach draft with two
sessions was created and saved; the temporary draft named
`UI Acceptance Check 2026-10` was archived after testing.

The RabbitMQ topology and connected workers were verified. The optional
`ollama/ollama:0.12.5` runtime started healthy, `qwen2.5:3b` downloaded
successfully, and direct local inference passed. In the signed-in member UI,
a workout-insight request returned a response for 2026-09-27 to 2026-10-25
matching server totals: 3 workouts, 6 sets, 50 repetitions and 200 kg×reps.
The typed request correctly showed the service-unavailable response before
Ollama was available.

In the signed-in coach UI, a one-session beginner programme generation
completed and created a linked `DRAFT`; it was not approved or published.
The generated squat and push-up prescriptions were timed 3×60 seconds, so a
coach should review the exercise selection and prescription before proceeding
through review/publishing. A separate three-day generation failed the
programme schema/training-rule validation, and a pre-Ollama request failed
after bounded retries. Model inference is slow on this laptop's CPU, and
generation reliability still needs improvement/testing; retain server-side
validation and do not auto-publish AI drafts.

Browser speech-recognition capability was present, but the attempted capture
failed and fell back to typed input; speech output was not verified. The
user-triggered SOS alarm/cancel flow worked with a dummy number and no call
was made; countdown expiry was not tested. Session expiry interrupted a
subsequent coach reorder-save check, so reorder persistence, exercise
create/edit/archive UI, programme review/publish, and full SOS expiry remain
unverified. The temporary `UI Acceptance Check 2026-10` draft was archived
after testing. See `PROJECT_MEMORY.md` for the detailed run record and
remaining checks.

The development seed uses the shared M1 provisioning contract to resolve
profile IDs and does not access or modify M1's database.
