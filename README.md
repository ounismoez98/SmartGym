# Smart Gym

Smart Gym is a gym-management platform. This repository currently delivers the
first runnable security vertical slice: Angular sign-in through Keycloak,
an authenticated dashboard, and the Spring Boot membership identity API.

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
The seeded gym accounts below are development identities and are not mapped
to any developer.

## Development-only URLs and startup

The local HTTP configuration is for development only:

| Component | URL |
| --- | --- |
| Angular/Nginx | `http://localhost:8088` |
| Keycloak | `http://localhost:8180` |
| Realm | `gym` |
| Business API | `http://localhost:8088/api/...` (proxied by Nginx) |
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
   | `POSTGRES_PASSWORD` | Password for the local PostgreSQL databases | Yes |
   | `PROVISIONING_TOKEN` | Token for the development-only profile provisioning API | Yes |
   | `RABBITMQ_USER` | RabbitMQ username | Yes |
   | `RABBITMQ_PASSWORD` | RabbitMQ password | Yes |
   | `LLM_BASE_URL` | OpenAI-compatible API base URL; defaults to Groq | No |
   | `LLM_MODEL` | Groq model ID; defaults to `llama-3.3-70b-versatile` | No |
   | `LLM_API_KEY` | Groq API key; replace the placeholder with a locally stored key | Yes |
   | `LLM_TIMEOUT_SECONDS` | LLM request timeout in seconds; defaults to `30` | No |

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

   Wait for Keycloak to become healthy, then check that the API logs show
   startup has completed:

   ```powershell
   docker compose logs api --tail 50
   ```

### Provision development accounts

The realm import defines the `ADMIN`, `COACH`, and `MEMBER` roles and imports
three development accounts (`gym-admin`, `gym-coach`, and `member-a`) without
passwords. The provisioning script adds `member-b` and sets passwords for
all four accounts.

The script reads environment variables from the current PowerShell session;
it does not read `.env` itself. Load the `.env` values into that session:

```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^\s*([^#=]+)=(.*)$') { Set-Item "env:$($matches[1].Trim())" $matches[2].Trim() } }
```

In addition to the variables in `.env.example`, set these development-only
passwords in the same PowerShell session. They are not in `.env.example`:

```powershell
$env:DEV_ADMIN_PASSWORD = "<choose a local-only password>"
$env:DEV_COACH_PASSWORD = "<choose a local-only password>"
$env:DEV_MEMBER_A_PASSWORD = "<choose a local-only password>"
$env:DEV_MEMBER_B_PASSWORD = "<choose a local-only password>"
```

`PROVISIONING_TOKEN` is also required by the script and must be present in
the PowerShell session. Then run:

```powershell
.\scripts\provision-dev.ps1
```

Account usernames are `gym-admin`, `gym-coach`, `member-a`, and `member-b`.
The script creates or updates these Keycloak users, applies their application
roles, and idempotently provisions their business profiles and coach
assignments through the Keycloak Admin API and development provisioning
endpoint. It prints the usernames, never their passwords. Passwords are
supplied only through environment variables and are not stored in source
control. The Keycloak administrator account is infrastructure-only; it is
not an application administrator.

### Configure Groq and sign in

Create a Groq API key in the [Groq Console](https://console.groq.com/keys) and
put it in `LLM_API_KEY` in your local `.env`. Keep it private; it is provided
only to the API container and is never part of the frontend configuration.
The example uses the documented Groq endpoint and model ID:

```dotenv
LLM_BASE_URL=https://api.groq.com/openai/v1
LLM_MODEL=llama-3.3-70b-versatile
LLM_API_KEY=replace-with-your-groq-api-key
LLM_TIMEOUT_SECONDS=30
```

The optional Ollama container remains available under the `local-llm` Compose
profile and does not start by default:

```powershell
docker compose --profile local-llm up --build -d
```

The API currently does not call either Groq or Ollama; the LLM client and
assistant are planned for a later implementation step.

Data sent to the LLM provider is limited to minimal, non-identifying facts.
The PFE specification asks for a local LLM; confirm this choice with the
supervisor.

Open `http://localhost:8088` and select **Sign in**.

Keycloak credential validation and invalid-credential messages stay on the
Keycloak-hosted login page. The realm uses a small Smart Gym CSS theme. SMTP
is not configured; password-recovery email is intentionally disabled and is
not operational.

The Compose stack routes `/api/membership`, `/api/courses`, `/api/training`,
and `/api/nutrition` to the secured business API while preserving the full
prefix. The latter three route prefixes are security-protected, ready for
their module services, and do not claim to implement those business features
yet.

The public `gym-frontend` Keycloak client uses Authorization Code + PKCE S256.
Its allowlist contains only the Angular root and the exact
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

`MembershipControllerTest` is the current automated API test class. It uses
MockMvc, simulated JWTs, and H2; it does not verify live Keycloak signatures
or browser flows. Browser and Compose checks are not re-verified.

| Acceptance item | Covered by automated test | Browser/Compose check |
| --- | --- | --- |
| Compose starts the core platform | No | not re-verified |
| Angular loads in a browser | No | not re-verified |
| Sign-in opens the Keycloak login page; valid and invalid credentials behave as expected | No | not re-verified |
| Dashboard displays the authenticated identity and membership API response | No | not re-verified |
| Angular calls `GET /api/membership/me` with an access token | No | not re-verified |
| Role-aware navigation, protected routes, and reload behavior | No | not re-verified |
| Missing/malformed token returns `401`; role and ownership denials return `403` | Yes — `MembershipControllerTest` | not re-verified |
| Member A cannot read another member's private record | Yes — `MembershipControllerTest` | not re-verified |
| Logout ends the application session and protected content is inaccessible | No | not re-verified |

- [ ] Expired sessions recover without a redirect loop (not tested).

## Project status

The delivered scope is the Keycloak-backed authentication and authorization
vertical slice: Angular sign-in and route guards, the Spring membership
identity API, role checks, member ownership checks, and development profile
provisioning, member and employee CRUD, and coach assignments. The SPA module
pages are placeholders; subscription plan, subscription, and payment
management are also delivered. Business CRUD for the other modules is not
delivered.

The following are not yet delivered:

- Application RabbitMQ events or consumers. RabbitMQ is deployed in Compose
  only.
- The administrative LLM assistant. Groq configuration is prepared, but the
  API does not call an LLM yet.
- SMTP password recovery and production HTTPS.

Flyway manages the PostgreSQL schema, and Hibernate uses `ddl-auto: validate`.
Development sample plans and policies are loaded only by the `dev` profile.
Current backend tests include MockMvc tests with simulated JWTs and H2, plus
Testcontainers PostgreSQL migration, active-subscription uniqueness, and
payment deletion-guard tests. Testcontainers cases are skipped when Docker
is unavailable. There are no Angular E2E tests in the repository.

Run the backend tests from the `backend` directory:

```powershell
mvn -B test
```

Docker Desktop must be running for the Testcontainers PostgreSQL tests.

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
