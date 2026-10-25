# Smart Gym — Project Memory

> Compact handoff for future work. Update this file when the project state,
> decisions, verification, or next priorities materially change. Do not store
> credentials, tokens, personal data, or copies of `.env` here.

## Current checkpoint — 2026-10-25

### Project state

- Initial runnable authentication vertical slice exists. M3 Phases 2–4 now
  include isolated exercise CRUD, multi-session programme/revision/review
  workflows, member workout logging, and deterministic progress summaries.
- The Phase 2–4 implementation has been expanded in this checkpoint with
  nested session/item CRUD and ordering, a full multi-session coach editor,
  member logging directly from published programme sessions, multi-set log
  editing/deletion, programme status filtering, paginated history, weekly
  activity visualization, and maximum-load summaries. Backend and frontend
  build checks passed after these changes.
- “Phases 2–4 complete” means the feature implementation and automated API
  coverage are in place. Live M3 acceptance through the running Compose stack
  with real Keycloak tokens and browser role workflows is still outstanding;
  do not describe end-to-end production acceptance as complete.
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
- `keycloak/realm-import/`: imported `smart-gym` realm, public SPA client
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
- `README.md`: startup instructions, development-only HTTP and account setup,
  production HTTPS guidance, ownership and acceptance status.
- `training-service/`: independent Spring Boot 3.4.5 / Java 21 application,
  separate `training_db` runtime identity, Flyway schema, Keycloak JWT
  issuer/audience/role validation, paginated catalogue, programme
  revision/review/publication workflow with scoped session/item CRUD and
  ordering, member-owned workout logs and progress. `/api/training` is routed
  to this service through Nginx.
- Phase 3 nested endpoints live under
  `/api/training/programmes/{id}/sessions`: create/update/delete/reorder
  sessions and create/update/delete/reorder items. Writes check coach
  assignment, draft-state restrictions, current programme version, and
  ordering IDs. Nested edits preserve existing unaffected IDs; content
  changes advance the revision and invalidate approval. Delete operations
  accept `version` as a query parameter; other nested writes accept it in the
  request body. Whole-programme `PATCH` remains available for replacing draft
  details and session/item content.
- M3 Angular programme editing now supports multiple sessions/items,
  reordering and typed repetition/time prescriptions. Members can log multiple
  actual sets from a published programme session, edit/delete their own logs,
  and view paginated history, weekly ISO-week activity and maximum explicitly
  recorded loads. Timed and bodyweight sets cannot contain an explicit load;
  volume includes only repetition-based sets with a supplied external load.
- Workout logging validates that a selected session belongs to the member's
  own published programme and limits chosen exercises to that session's
  prescriptions. Standalone logs remain supported. Programme-origin logs
  retain their programme/session references when edited.
- `docker-compose.messaging.yml`: pinned private RabbitMQ/Ollama development
  overlay; it is not part of normal startup and is not wired to an M3 worker.
- `scripts/seed-training-demo.ps1` + `.sql`: repeatable development data for
  four exercises, four programmes in draft/review/published states, a review
  record, and three Member A workout logs. It resolves current M1 profile
  identifiers through the protected development provisioning response and
  writes only to `training_db`.
- M3 resolves authenticated business profiles and coach assignments through
  the existing M1 `/api/membership/me` and
  `/api/membership/coach/assigned-members` endpoints, forwarding the original
  user bearer token for the synchronous HTTP check. M3 does not access M1's
  database.

### Verified

- `npm.cmd --prefix frontend run build`: passed with lazy exercise,
  programme, and workout routes.
- `mvn.cmd -B test` from `backend/` using Java 21: passed, 8 tests.
- `mvn.cmd -B -f training-service\pom.xml test`: passed, 20 tests (H2),
  covering session/item CRUD and ordering, stale versions, published-session
  workout validation, and rejection of loads on timed/bodyweight sets.
- This shell defaults `java` to JDK 17, which cannot run the Java 21 test
  classes. Set `JAVA_HOME` to
  `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot` and prepend
  `%JAVA_HOME%\bin` to `PATH` before running the training-service Maven tests.
  Maven printed an unrelated Flyway Maven repository metadata 401 warning;
  the cached dependencies were available and the test build succeeded.
- `docker compose --env-file .env.example config --quiet`: passed.
- `docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.messaging.yml config --quiet`: passed.
- Training-service Docker image build passed.
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
- M3 Phase 2–4 automated API tests cover exercise roles/validation/archive,
  programme assignment checks, session/item CRUD/reordering, revision/
  review/publish, stale edits, private drafts, published-session workout
  references, workout ownership, invalid prescriptions, deletion, and
  progress math.
- The new session/item API test exercises create, update, delete, and ordering
  routes, checks version conflicts, and confirms ordering updates. Workout
  tests additionally exercise a valid published-session log, reject an
  unrelated exercise, and verify member log deletion.
- Demo seed was run twice successfully; database remained at 4 exercises,
  4 programmes (1 DRAFT, 1 PENDING_REVIEW, 2 PUBLISHED), 3 logs, and 6 sets.
- Rebuilt/recreated API, training-service, and frontend containers; UI
  returned HTTP 200 and training-service actuator reported UP.
- New M3 Compose service has not been started against the running Keycloak/
  PostgreSQL stack with real user tokens or browser M3 workflows; live
  cross-service authorization and browser acceptance remain unverified.

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

Implement remaining phases strictly one at a time. For each phase, first
inspect the relevant prompt requirements and shared-module conventions; then
implement only that phase, add/run focused tests, update this memory and
README with actual results, and report blockers before starting the next phase.
Do not treat a container, stub, mock, or disabled UI as a completed feature.

1. **Phase 5 — asynchronous local LLM programme generation.** Before coding
   the RabbitMQ integration, coordinate with M5 on the shared broker,
   ownership, exchange/queue names, durability, publisher confirms, retries,
   dead-letter handling, and deployment conventions. Then implement the
   authorized generation API and input validation; persisted `AiJob` and
   transactional outbox; idempotent worker/inbox; configurable Ollama adapter
   plus deterministic fake for tests; prompt and strict structured-output
   validation against active exercise IDs and numeric prescription rules;
   retry/failure states; protected job status; Angular generation form and
   polling/cleanup. AI output must create an unpublished draft only—coach
   review and explicit publication remain separate. Never put bearer tokens
   or credentials in messages. Add tests for valid output, malformed output,
   unknown exercises, invalid numbers, timeout, duplicate delivery and
   retry/DLQ behavior. Document opt-in model setup without pulling a large
   model during normal startup. **Stop after Phase 5 validation and report.**
2. **Phase 6 — optional exercise illustrations.** Start only after Phase 5 is
   finished and reviewed. Keep image generation behind an explicit feature
   flag/profile with a separate model/provider; implement generation-job
   tracking, protected file storage/preview, start/finish image validation,
   coach approve/reject with required rejection reason, and approved-image
   display. Add storage persistence/configuration without public file access
   or public-by-default ports. When disabled, explain the feature state in the
   UI and API rather than presenting mock images as real. Test authorization,
   validation, review transitions, and disabled behavior. **Stop after Phase 6
   validation and report.**
3. **Phase 7 — acceptance, tests, and delivery.** Start only after Phase 6
   is complete or its optional/disabled scope is explicitly agreed. Verify
   real signed Keycloak JWT issuer/audience/expiry/role checks and M1 bearer
   forwarding, coach assignment enforcement, member isolation, shared-role
   behavior and ADMIN private-record restrictions. Run Compose and migrations
   against PostgreSQL; exercise the Angular coach/member flows in a browser;
   verify health/readiness and repeatable development seed behavior. Add or
   complete applicable integration tests and frontend tests, then deliver the
   ER diagram, state/sequence diagrams, message examples, prompt/validated
   JSON examples, permission matrix, setup and test commands, actual results,
   assumptions, and limitations. Do not claim browser/live tests that were
   not run.
4. **Independent foundation and coordination follow-ups.** Confirm M3/M4
   developer ownership with the team; retain current pending labels until
   confirmed. Separately test auth expiry/failed refresh and redirect-loop
   protection; investigate the non-fatal Keycloak iframe warning without
   weakening issuer/audience validation. Before production, use HTTPS and
   managed secrets, remove development ports/provisioning, and configure
   SMTP before enabling password recovery. These follow-ups do not authorize
   skipping or combining the sequential M3 phases above.

## Update convention

Keep this file compact: replace the current checkpoint date/status and record
material changes, decisions, verification results, blockers, and next tasks.
Do not duplicate full feature documentation from `README.md`.
