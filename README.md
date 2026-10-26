# Smart Gym

Smart Gym is a gym-management platform. This repository delivers the M1
membership service: Keycloak-backed Angular sign-in, membership management,
subscriptions, payments, staff and coach assignments, RabbitMQ events, and
the administrative assistant. Other business modules remain placeholders.

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
`api`, and `frontend`. The optional `ollama` service is available with the
`local-llm` Compose profile. The Spring API and PostgreSQL are also
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

The Compose stack routes `/api/membership`, `/api/courses`, `/api/training`,
and `/api/nutrition` to the secured business API while preserving the full
prefix. Nutrition endpoints provide member-scoped foods, meal plans, food
journal entries, body measurements, profiles, nutritional calculations,
persistent meal plans, hydration, and an optional Groq meal-plan assistant.
Courses and training remain
security-protected and await their module services.

The Keycloak client allowlist contains only the Angular root and the exact
SPA routes (`/dashboard`, `/membership`, `/courses`, `/training`, `/nutrition`,
`/forbidden`, `/not-found`) plus `/silent-check-sso.html`; the post-logout
redirect is exactly `http://localhost:8088/`, and the allowed web origin is
exactly `http://localhost:8088`.

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
post-commit RabbitMQ events, and the administrative assistant and reminder
confirmation flow. The `/membership` Angular screens are delivered; the SPA
pages for the other modules remain placeholders. Business features for the
other modules are not delivered.

The following are not yet delivered:

- Application RabbitMQ consumers.
- SMTP password recovery and production HTTPS.
- Business features for modules M2-M4.

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
0 failures and 10 skipped because Docker-backed integration tests were not
executed in that run. Separate focused tests cover provisioning Keycloak
accounts and linking the generated ID to a profile. Additional tests verify
safe provider diagnostics for rejected keys and unavailable models/endpoints.
Angular unit tests passed 61 tests, and the production build passed. The
Playwright Chromium scenario covered the admin membership workflow, Keycloak
member and coach account creation, first-login password change, assistant
explanation against the mock LLM, payment correction history, MailHog
confirmation, and member ownership denial.
Not verified: Testcontainers against PostgreSQL in the latest Maven run,
Groq against the real remote provider, and a manual browser review of all
screens and roles. The automated E2E run is not a substitute for that manual
review.
The OpenAPI JSON document is publicly readable; the described API operations
still require a valid bearer JWT and enforce their normal role/ownership rules.

Run the backend tests from the `backend` directory:

```powershell
mvn -B test
```

The Playwright scenario uses the local Compose stack, MailHog, and a test-only
mock OpenAI-compatible LLM; it does not contact Groq. Put the same local
development passwords used by `scripts/provision-dev.ps1` into the ignored
repository-root `.env` as `DEV_ADMIN_PASSWORD` and `DEV_MEMBER_A_PASSWORD`.
The Playwright configuration loads only these credential variables (or
`E2E_ADMIN_PASSWORD` and `E2E_MEMBER_PASSWORD`) from `.env`; it does not load
`LLM_API_KEY` into the test process. Usernames default to `gym-admin` and
`member-a`, and can be overridden with `E2E_ADMIN_USERNAME` and
`E2E_MEMBER_USERNAME`. Then launch the test stack and install Chromium once:

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
