# Smart Gym — Project Memory

> Compact handoff for future work. Update this file when the project state,
> decisions, verification, or next priorities materially change. Do not store
> credentials, tokens, personal data, or copies of `.env` here.

## Current checkpoint — 2026-10-25

### Project state

- Initial runnable authentication vertical slice exists. M3 Phases 2–6 now
  include isolated exercise CRUD, multi-session programme/revision/review
  workflows, member workout logging, deterministic progress summaries, and
  asynchronous AI programme generation plus a member workout voice companion.
- The Phase 2–4 implementation includes nested session/item CRUD and ordering,
  the multi-session coach editor, member logging from published sessions,
  multi-set log editing/deletion, paginated history, weekly activity and
  maximum-load summaries. Automated API coverage passed. Live browser
  acceptance verified member catalogue/programme/history views, a temporary
  workout log create/edit/delete cycle, coach assigned-member access, and
  coach draft creation/editing with two sessions. The browser session expired
  during a subsequent reorder-save attempt, so that save and the remaining
  review/publish/exercise-management UI flows are not claimed as verified.
- Phase 5 is implemented. The live RabbitMQ exchange/queues and connected
  workers were verified; a coach UI generation request was accepted and
  published through the outbox. The optional Ollama runtime and `qwen2.5:3b`
  model were started/pulled, and both a direct model inference and app-level
  workout insight succeeded. App-level programme generation succeeded on a
  second request, creating a linked AI-origin `DRAFT` without approving or
  publishing it. An earlier three-day request failed schema/training-rule
  validation; another pre-Ollama request failed bounded retries. Keep both
  failures in the test record and require coach review of the generated
  exercise selections before any publication.
- Phase 6 is implemented: member-owned 28-day workout insights, browser voice
  input/output where supported, deterministic encouragement, and explicit
  user-triggered alarm/check-in with a manual call link. A signed-in member
  successfully received an Ollama workout-history explanation for the last
  28 days; its verified totals were 3 workouts, 6 sets, 50 repetitions and
  200 kg×reps. The earlier typed request correctly surfaced `503` while
  Ollama was unavailable. Speech recognition was attempted but returned the
  UI fallback error; browser API capability alone does not establish
  successful capture. The SOS alarm and cancel action were exercised with a
  dummy phone value; no call was made, and countdown expiry was not tested.
  There is no background sound monitoring, injury detection, or automatic
  emergency call.
- Automated checks and live-stack acceptance are recorded below; this local
  browser run does not constitute production acceptance. Stop for user
  review before implementing another phase.
- Development stack was started and browser-smoke-tested at
  `http://localhost:8088`; Keycloak is at `http://localhost:8180`.
- Local secrets are in ignored `.env`; use `.env.example` as the template and
  never copy actual secret values into documentation or source control.
- Existing `.env` files created before Phase 5 may lack `RABBITMQ_USER` and
  `RABBITMQ_PASSWORD`; add fresh local values before running Compose. Do not
  overwrite an existing local `.env` blindly.
- For this local run, missing RabbitMQ and training database variables were
  added to the ignored `.env`; no secret values belong in this file or in
  source control.

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
- Core `docker-compose.yml` contains the single private, persistent RabbitMQ
  development broker (no host port); `docker-compose.messaging.yml` now only
  contributes opt-in Ollama under profile `ai`. M3 owns its durable exchange,
  command/result/retry/dead-letter topology and queue worker.
- Phase 5 persists accepted `AiJob` requests and transactional outbox rows in
  `training_db`. The outbox publishes only after broker confirmation. A
  training-service Rabbit listener invokes the configurable Ollama adapter,
  validates structured output against active compatible exercise IDs and
  prescription constraints, and publishes result events. The result
  consumer idempotently stores a linked AI-origin `DRAFT`; it never approves
  or publishes a plan automatically. No separate worker inbox database was
  added: the worker does not mutate business state, and result application is
  idempotent by job ID.
- Phase 5 APIs: coach-authorized
  `POST /api/training/programme-generations` returns `202`; protected
  `GET /api/training/ai-jobs/{id}` reports status. Generation data excludes
  bearer tokens and member identity/profile details. Ollama does not pull a
  model during normal startup; it is opt-in and configured by `OLLAMA_MODEL`.
- Phase 6 adds member-only
  `POST /api/training/me/workout-insights`: application code computes
  last-28-day workout/set/repetition/loaded-volume totals and passes those
  aggregates plus the question to local Ollama; raw logs, notes, member ID
  and bearer token are not sent. Encouragement is generated deterministically
  from the verified workout count. A model/service failure is surfaced as
  `503`, not substituted with invented output.
- The workout companion blocks pain/injury/treatment questions with a fixed
  safety response and does not send those questions to the model.
- The member workout UI uses browser speech recognition only after a
  microphone-button click and always permits typed questions. Browser speech
  recognition may use the browser vendor's external service; SmartGym stores
  no audio. Speech output uses browser synthesis when available.
- Phase 6 SOS behavior requires a user click, sounds a local browser alarm and
  runs a visible 30-second check-in. A user-entered phone number remains only
  in the page's memory and is used only after tapping its `tel:` link. The
  countdown does not call automatically or infer injury from sound/silence;
  the browser/device must remain active for the alarm to work.
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
- `mvn.cmd -B -f training-service\pom.xml test` using Java 21: passed, 29
  tests total, including AI generation authorization/idempotency/validation,
  deterministic fake adapter, and worker bounded-retry/DLQ tests.
- `mvn.cmd -B -f training-service\pom.xml -Dtest=WorkoutControllerTest test`
  using Java 21: passed, 6 tests, including 28-day own-member filtering,
  server-calculated metrics, and coach denial for the new insights endpoint.
- `npm.cmd --prefix frontend run build`: passed with voice entry/output,
  typed fallback, and manual SOS UI.
- `npm.cmd --prefix frontend run build`: passed after adding the Phase 5
  coach generation form and job polling.
- Core Compose and optional AI-profile Compose configuration checks passed
  using `.env.example`; the training-service Docker image built successfully.
- Latest full training-service suite: 31 tests passed; M1 membership suite:
  8 tests passed. Angular production build, core/AI-profile Compose
  configuration checks, training-service Docker image build, and
  `git diff --check` also passed.
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
- Demo seed was run twice successfully; baseline database counts were 4
  exercises, 4 programmes (1 DRAFT, 1 PENDING_REVIEW, 2 PUBLISHED), 3 logs,
  and 6 sets. Browser acceptance added a two-session coach draft named
  `UI Acceptance Check 2026-10`; it was archived after the UI test.
- Core Compose stack was running; Keycloak, PostgreSQL, and RabbitMQ were
  healthy, training-service healthcheck reported healthy, and frontend,
  Keycloak discovery, and membership API returned expected responses
  (including unauthenticated `401`).
- Live browser checks verified Member A sign-in, profile/dashboard,
  membership connection, four seeded exercises, published programme and
  workout history (3 logs, 50 repetitions, 200 kg×reps); a temporary workout
  log was created, edited and deleted, restoring the seeded history. Coach
  sign-in and assigned-member listing worked. A member's coach-only workout
  route was denied. Coach programme list/editor rendered and the temporary
  two-session draft was created and saved.
- Optional Ollama image/model pull completed: `ollama/ollama:0.12.5` is
  healthy, `qwen2.5:3b` (1.9 GB) is installed, and a direct local inference
  smoke test returned the expected response.
- Signed-in member UI: typed workout-insight question completed successfully.
  The displayed explanation matched server totals for 2026-09-27 to
  2026-10-25: 3 workouts, 6 sets, 50 repetitions and 200 kg×reps. An earlier
  request before Ollama was available displayed the expected `503` fallback.
- Signed-in coach UI: a generation request for one beginner full-body session
  completed and created `Beginner Strength Training Programme Draft` as a
  linked `DRAFT`. UI showed a session with bodyweight squat, push-up and front
  plank prescriptions; the draft remained unapproved/unpublished. Human
  review remains necessary, especially because the generated 3×60-second
  prescriptions for squat/push-up may not be sensible demonstrations of the
  requested strength plan.
- Another coach generation request (three days/week) failed with
  `The model returned a programme that did not meet the required JSON schema
  and training rules.` A pre-Ollama generation request failed bounded retries.
  The model can be slow on this laptop's CPU; Ollama logs recorded calls up
  to about 2m27s. At final check, worker/retry queues were empty with one
  connected worker and one prior failed message remained in the durable DLQ.
- The temporary `UI Acceptance Check 2026-10` editor test draft was archived
  after testing; the AI-generated draft was retained as demo data.
- Speech recognition attempt fell back to typed input; speech output is
  unverified. The manual SOS link/cancel flow worked; no call was made. The
  full 30-second SOS expiry behavior, all exercise create/edit/archive UI
  actions, published-programme review/publish flow, and session reorder
  persistence were not verified in the browser.

### Important paths

- Start stack: `docker compose up --build -d`
- Provision users: see commands in `README.md`; requires local environment
  variables. Never paste development passwords into chat or docs.
- Angular: `frontend/src/app/`
- JWT/security/API: `backend/src/main/java/com/smartgym/membership/`
- API access-control tests:
  `backend/src/test/java/com/smartgym/membership/api/MembershipControllerTest.java`
- Realm/client/claims: `keycloak/realm-import/gym-realm.json`

## Next work — one phase at a time

Implement remaining phases strictly one at a time. For each phase, first
inspect the relevant prompt requirements and shared-module conventions; then
implement only that phase, add/run focused tests, update this memory and
README with actual results, and report blockers before starting the next phase.
Do not treat a container, stub, mock, or disabled UI as a completed feature.

1. **Optional browser follow-up.** Review the retained AI-generated draft
   before submitting it for review. Repeat ordering/review/publish and
   exercise create/edit/archive checks if a complete UI acceptance matrix is
   required. Keep SOS testing local and never initiate a real call.
2. **Improve AI generation reliability if requested.** One real generation
   completed but a separate request failed model-output validation. Capture
   or safely expose validation diagnostics and improve prompt/output
   robustness before claiming dependable generation; retest on CPU because
   the local model is slow. Do not weaken catalogue, assignment, prescription,
   or publication validation to force success.
3. **Stop for user review before implementing any additional phase.**
   Independent foundation and coordination follow-ups: confirm M3/M4
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
