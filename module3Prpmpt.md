Act as a senior full-stack engineer.

Implement Module 3 — Training and Exercise Management — for our
“Smart Gym” final-year project.

Create working code, migrations, frontend screens, Docker configuration
and tests. Do not deliver only an explanation or mockup.

────────────────────────────────────────────────────────
1. PROJECT CONTEXT
────────────────────────────────────────────────────────

Our project has four independently owned business modules:

- M1 — Membership and staff: Nour.
- M2 — Courses, reservations and equipment: Yacine.
- M3 — Training and exercises: Moez or Mehdi, assignment pending.
- M4 — Nutrition and body tracking: the remaining member, pending.
- M5 — Keycloak: shared authentication infrastructure.

Do not arbitrarily assign M3 to Moez or Mehdi.

Application roles:
- ADMIN
- COACH
- MEMBER

Developer ownership is separate from application roles.

Required architecture:
- Angular frontend.
- Spring Boot business services.
- PostgreSQL.
- RabbitMQ.
- Keycloak.
- Local LLM through Ollama.
- Docker Compose.
- Nginx reverse proxy.

M3 must own its:
- Angular training feature area.
- training-service.
- training_db database.
- Business rules and CRUD.
- Programme-generation prompts.
- AI output validation.
- Tests.

First inspect the repository and reuse existing conventions, authentication,
shared infrastructure and dependency versions.

Do not replace working Keycloak login, create another Angular application,
duplicate shared containers or introduce a custom authentication service.

If the shared foundation is missing, clearly identify the dependencies
and provide the minimum compatible integration configuration.

────────────────────────────────────────────────────────
2. MODULE SCOPE
────────────────────────────────────────────────────────

Implement these capabilities:

A. Exercise catalogue
B. Training programme management
C. Programme sessions and exercise prescriptions
D. Member workout logging
E. Coach review and publication
F. Local LLM training-programme generation
G. Optional exercise-image generation and validation

Required LLM functionality:
Generate a structured training-programme draft from authorised inputs.

Exercise-image generation is an additional feature using a separate model.
It does not replace the LLM requirement.

Keep medical diagnosis, injury rehabilitation and treatment recommendations
outside this module’s scope.

────────────────────────────────────────────────────────
3. TECHNOLOGY AND CODE ORGANISATION
────────────────────────────────────────────────────────

Use the repository’s compatible versions.

If starting this service from scratch, use:
- Java 21.
- Compatible stable Spring Boot.
- Maven Wrapper.
- Spring Web.
- Spring Security OAuth2 Resource Server.
- Spring Data JPA.
- Bean Validation.
- Flyway.
- PostgreSQL.
- Spring AMQP.
- OpenAPI documentation.
- JUnit and Testcontainers where practical.

Backend structure:
- controller
- dto
- application/service
- domain
- repository
- security
- messaging
- ai
- configuration
- exception

Frontend:
- Add a lazy-loaded /training feature to the existing Angular application.
- Reuse the shared authentication interceptor, guards, layout and styling.
- Use typed models, reactive forms and clear loading/error states.

Do not expose JPA entities directly through REST responses.
Use DTOs and consistent structured error responses.

────────────────────────────────────────────────────────
4. DATABASE OWNERSHIP AND MODEL
────────────────────────────────────────────────────────

Use training_db with a dedicated restricted runtime user.

M3 must not:
- Read or modify M1, M2, M4 or Keycloak tables.
- Create cross-database foreign keys.
- Copy credentials into training_db.

External references:
- member_id: UUID referencing an M1 member profile.
- coach_id: UUID referencing an M1 employee/coach profile.
- Keycloak subjects: strings where an identity reference is needed.

Use Flyway migrations and Hibernate schema validation.
Do not use ddl-auto=update.

Design these entities:

1. Exercise
- id
- name
- description
- instructions
- primary muscle group
- difficulty
- equipment category or required equipment
- active/archived status
- created_at
- updated_at
- version

2. TrainingProgramme
- id
- member_id
- coach_id, nullable until assigned where appropriate
- title
- goal
- experience level
- duration_weeks
- status
- origin: MANUAL or AI
- revision
- source_ai_job_id, nullable
- created_by_subject
- created_at
- updated_at
- version

3. ProgrammeSession
- id
- programme_id
- title
- day_order
- notes

4. ProgrammeItem
- id
- session_id
- exercise_id
- order_index
- sets
- repetitions, nullable for time-based exercises
- duration_seconds, nullable for repetition-based exercises
- rest_seconds
- suggested_load_kg, optional
- notes

Define valid combinations for repetitions and duration.
Use structured numeric fields, not an unvalidated free-text prescription.

5. WorkoutLog
- id
- member_id
- programme_id, optional
- programme_session_id, optional
- performed_at
- notes
- created_at
- updated_at
- version

6. WorkoutSet
- id
- workout_log_id
- exercise_id
- set_number
- repetitions, optional
- duration_seconds, optional
- load_kg, optional
- notes

7. CoachReview
- id
- programme_id
- reviewed_revision
- coach_id
- decision: APPROVED or CHANGES_REQUESTED
- comment
- reviewed_at

8. ExerciseIllustration
- id
- exercise_id
- start_image_reference, optional
- finish_image_reference, optional
- status
- generation_job_id, optional
- reviewed_by_coach_id, optional
- reviewed_at, optional
- rejection_reason, optional
- created_at
- updated_at
- version

9. AiJob
- id
- job_type
- requester_subject
- member_id, optional
- status
- correlation_id
- validated input/context reference
- result reference
- safe error code/message
- created_at
- started_at
- completed_at

10. Messaging infrastructure
- OutboxMessage
- InboxMessage or equivalent deduplication records

Add:
- Appropriate indexes.
- Referential integrity within training_db.
- Check constraints for positive quantities and valid ordering.
- Unique ordering constraints within programme sessions.
- Optimistic locking for concurrent edits.
- UTC-aware timestamps and decimal load values.

Document any simplifications.

────────────────────────────────────────────────────────
5. IDENTITY AND AUTHORISATION
────────────────────────────────────────────────────────

Integrate with the existing Keycloak realm:
smart-gym

Every API must validate:
- Signature.
- Issuer.
- Expiry.
- Intended audience.
- Application roles.

Reuse existing Keycloak role mapping.

Do not trust member_id or coach_id supplied by the browser as proof
of authorisation.

Resolve the authenticated identity to the appropriate M1 business profile
through an explicit integration contract.

Use the existing coach-member assignment source.
If none exists, propose the ownership and contract before implementing it.

Do not assume assignment to a programme alone authorises access to all
of a member’s records.

Permissions:

MEMBER:
- View the active exercise catalogue and approved illustrations.
- View their own published programmes.
- Create, read, update and delete their own workout logs.
- View their own progress summaries.
- Cannot publish programmes or approve illustrations.
- Cannot access another member’s private records.

COACH:
- Manage exercise content according to the agreed catalogue policy.
- Create and edit programmes for assigned members.
- Request AI programme drafts for assigned members.
- Review and publish permitted programmes.
- Review illustrations.
- View workout logs only for assigned members.

ADMIN:
- Manage explicitly authorised catalogue/operational functions.
- Does not automatically receive unrestricted access to private
  workout logs or health-related information.
- Does not gain coach publication rights merely by having ADMIN.

Support users with multiple roles.

Apply checks in backend services as well as frontend routes.
Return 401 for unauthenticated requests and 403 for forbidden operations.

Protect AI job status, draft previews and file downloads using the same
resource-level authorisation.

────────────────────────────────────────────────────────
6. EXERCISE CRUD
────────────────────────────────────────────────────────

Implement:
- Create an exercise.
- Paginated exercise list.
- Search by name.
- Filter by muscle group, difficulty and equipment.
- View exercise details.
- Edit exercise information.
- Archive an exercise.

Do not delete exercises referenced by programmes or workout history.
Archived exercises must remain readable in historical records but must
not be selectable for new programme items by default.

Validate:
- Required names and instructions.
- Supported enum values.
- Reasonable text lengths.
- Equipment references.

M3 may own descriptive equipment categories used for training.
M2 remains the owner of actual gym equipment inventory and availability.

────────────────────────────────────────────────────────
7. PROGRAMME MANAGEMENT AND REVIEW
────────────────────────────────────────────────────────

Implement manual programme creation and editing.

A programme contains ordered sessions.
A session contains ordered exercise prescriptions.

Support:
- Create a draft.
- List and filter programmes.
- View programme details.
- Add/edit/remove sessions.
- Add/edit/remove/reorder programme items.
- Submit for coach review.
- Request changes.
- Approve the current revision.
- Publish.
- Archive.

Suggested statuses:
- DRAFT
- PENDING_REVIEW
- CHANGES_REQUESTED
- APPROVED
- PUBLISHED
- ARCHIVED

Enforce transitions in backend code.

Important rules:
- Approval applies to a specific revision.
- Editing approved content invalidates its approval.
- Publication requires approval of the current revision.
- Published programmes must not change silently.
- Implement a controlled new-revision workflow or an equivalent
  explicit policy preserving the published version.
- A programme cannot be published with empty sessions or invalid items.
- Members see published programmes only.
- Preserve coach-review history.

Use optimistic locking to reject stale updates with a clear conflict response.

────────────────────────────────────────────────────────
8. WORKOUT LOGGING AND PROGRESS
────────────────────────────────────────────────────────

Provide member-owned workout logging:

- Create a log from a published programme session or as a standalone workout.
- Record actual sets, repetitions, duration and load.
- View paginated history.
- Edit or delete the member’s own log.
- View exercise-specific progress.

Compute progress using backend code.

Examples:
- Completed workouts per week.
- Total repetitions.
- Maximum recorded load for an exercise.
- Training volume for applicable loaded, repetition-based exercises.

Do not invent load values for bodyweight or timed exercises.
Clearly document the meaning and limitations of each metric.

Check that programme/session references belong to the same member and
are internally consistent.

No LLM is required for arithmetic or progress aggregation.

────────────────────────────────────────────────────────
9. LOCAL LLM PROGRAMME GENERATION
────────────────────────────────────────────────────────

Implement a real asynchronous programme-generation workflow.

Inputs may include:
- Member goal.
- Experience level.
- Training days per week.
- Maximum session duration.
- Available equipment categories.
- Exercise preferences.
- Explicit non-medical constraints.

Use only authorised, necessary context.
Do not send names, emails, payments or unrelated member records to the LLM.

Do not send arbitrary unrestricted database access to the model.

Ground programme generation in the active exercise catalogue:
- Retrieve a bounded candidate list.
- Supply valid exercise IDs and instructions.
- Require the model to select from those IDs.
- Reject unknown or archived exercise references.

Require structured JSON output.

Define a JSON Schema containing:
- Programme title and goal.
- Duration in weeks.
- Ordered sessions.
- Ordered exercises.
- Sets.
- Repetitions or duration.
- Rest periods.
- Optional notes.

Treat all model output as untrusted.

Validate:
- JSON syntax and schema.
- Referenced exercises.
- Numeric ranges.
- Required fields.
- Number of sessions.
- Equipment compatibility.
- Repetition/duration consistency.
- Session-duration assumptions.
- Duplicate or contradictory items.

Document configurable bounds as project validation rules, not universal
medical guarantees.

Do not claim estimated session duration is exact.
Explain the estimation method.

On invalid output:
- Use a bounded repair/retry strategy if appropriate.
- Then fail clearly.
- Never publish or silently accept malformed output.

The result must be stored as DRAFT.
A coach must review it before publication.

Provide:
- Real Ollama adapter.
- Deterministic fake adapter for automated tests.
- Configurable model name and timeout.
- Prompt templates owned by M3.
- Sample input and validated output.

If a request concerns injury treatment, rehabilitation or another excluded
medical use case, return a clear limitation and require professional advice.

Do not present model output as medically verified.

────────────────────────────────────────────────────────
10. RABBITMQ WORKFLOW
────────────────────────────────────────────────────────

Reuse the shared RabbitMQ conventions.

Example command:
training.programme-generation.requested.v1

Example events:
training.programme-generation.completed.v1
training.programme-generation.failed.v1

Optional image command:
training.image-generation.requested.v1

Message envelope:
- messageId
- correlationId
- jobId
- messageType
- version
- occurredAt
- payload

Workflow:

1. Coach requests generation through the authenticated API.
2. API verifies role and member assignment.
3. API validates inputs.
4. API persists AiJob + outbox entry in one SQL transaction.
5. API returns 202 Accepted and job ID.
6. Outbox publisher sends the task.
7. M3-specific worker calls Ollama.
8. Worker durably publishes the result or failure.
9. M3 validates the result and stores a draft.
10. Angular polls the authorised job-status endpoint.
11. Coach reviews and publishes separately.

Implement:
- Publisher confirms.
- Durable queues.
- Persistent messages.
- Manual ACK after durable handling.
- Idempotency by messageId/jobId.
- Bounded retries.
- Dead-letter queue.
- Explicit handling of invalid messages.

Retries must not create duplicate programmes.

Do not send access tokens or credentials through RabbitMQ.
Do not send image binaries through RabbitMQ.

Workers must not directly modify training business tables.
They return results through the agreed message contracts.

If workers need durable inbox/outbox storage, use the shared worker
infrastructure storage rather than business-database credentials.

────────────────────────────────────────────────────────
11. OPTIONAL EXERCISE ILLUSTRATIONS
────────────────────────────────────────────────────────

Implement behind an optional Docker Compose profile or feature flag.

Use a separate local image-generation adapter.
Do not claim Ollama text generation creates images.

Workflow:
- Authorised coach requests illustrations for an exercise.
- Use the validated exercise instructions to create a visual prompt.
- Generate start and finish images.
- Store files outside PostgreSQL.
- Store file references and review status in training_db.
- Coach previews and approves or rejects.
- Only approved illustrations are exposed to members.

Suggested statuses:
- GENERATING
- PENDING_REVIEW
- APPROVED
- REJECTED
- FAILED

Requirements:
- Reuse approved assets across programmes.
- Validate file type and size.
- Prevent path traversal.
- Do not expose raw filesystem paths.
- Protect non-approved files with authorisation.
- Record reviewer and rejection reason.
- Do not silently replace an approved image with an unreviewed image.

Human review must check posture and anatomy.
Do not promise automatic image safety or anatomical correctness.

If no image runtime is available, provide a clearly labelled disabled
state or test adapter. Do not present placeholder images as generated results.

────────────────────────────────────────────────────────
12. REST API
────────────────────────────────────────────────────────

Use /api/training as the public route prefix.

Provide documented endpoints for:

Exercises:
- POST /exercises
- GET /exercises
- GET /exercises/{id}
- PATCH /exercises/{id}
- POST /exercises/{id}/archive

Programmes:
- POST /programmes
- GET /programmes
- GET /programmes/{id}
- PATCH /programmes/{id}
- POST /programmes/{id}/submit
- POST /programmes/{id}/reviews
- POST /programmes/{id}/publish
- POST /programmes/{id}/archive

Sessions/items:
- CRUD and ordering endpoints scoped under their programme/session.

Workout logs:
- POST /workout-logs
- GET /workout-logs
- GET /workout-logs/{id}
- PATCH /workout-logs/{id}
- DELETE /workout-logs/{id}

Progress:
- GET /me/progress
- GET /members/{memberId}/progress for authorised coaches only.

AI:
- POST /programme-generations
- GET /ai-jobs/{id}

Illustrations, if enabled:
- POST /exercises/{id}/illustration-generations
- GET /exercises/{id}/illustrations
- POST /illustrations/{id}/reviews
- Protected file-preview endpoint.

Document:
- Request/response examples.
- Validation errors.
- Permission rules.
- Pagination.
- Conflict responses.
- Job states.

Preserve the existing Nginx route-prefix conventions.

────────────────────────────────────────────────────────
13. ANGULAR SCREENS
────────────────────────────────────────────────────────

Build functional responsive screens:

1. Training dashboard
- Role-aware actions.
- Programme summaries.
- Recent workout history.
- Pending reviews for authorised coaches.

2. Exercise catalogue
- Search and filters.
- Pagination.
- Create/edit form for permitted users.
- Detail page and approved illustrations.

3. Programme list
- Status filters.
- Own programmes for members.
- Assigned-member programmes for coaches.

4. Programme editor
- Programme details.
- Session management.
- Ordered exercise items.
- Validation messages.
- Save, submit and review actions.

5. AI generation form
- Goal, level, days, session duration and equipment.
- Clear statement that output is a draft.
- Job progress/status.
- Useful failure and retry feedback.

6. Coach review page
- Full programme preview.
- Current revision.
- Review history.
- Approve/request-changes controls.
- Explicit publication action.

7. Member programme view
- Published content only.
- Session/exercise prescriptions.
- “Log workout” action.

8. Workout logging/history
- Actual set entry.
- Edit/delete own logs.
- Simple progress charts based on real API data.

9. Illustration review, if enabled
- Start/finish preview.
- Approve/reject actions.
- Rejection reason.

Reuse existing Keycloak login.
Do not fake role checks or populate production screens with hardcoded data.

Handle:
- Loading.
- Empty results.
- Validation failures.
- 401/403.
- Not found.
- Stale-edit conflicts.
- AI failure.

────────────────────────────────────────────────────────
14. DOCKER AND CONFIGURATION
────────────────────────────────────────────────────────

Add training-service to the existing Compose architecture.

Provide:
- Multi-stage Dockerfile.
- Health/readiness checks.
- training_db provisioning.
- Dedicated database credentials.
- RabbitMQ configuration.
- Nginx API route.
- Environment variables in .env.example.
- Optional AI/image profiles.
- Persistent image-storage volume where needed.

Do not expose PostgreSQL, RabbitMQ, Ollama or training-service publicly
by default.

Use the shared Keycloak issuer and API audience conventions.
Do not disable issuer validation to solve container networking.

Do not commit real secrets.
Do not use “latest” container tags.
Do not download a large model automatically during normal core startup.

Document how to:
- Start the core module.
- Enable real LLM generation.
- Use the fake adapter for tests.
- Enable optional image generation.

────────────────────────────────────────────────────────
15. TESTS
────────────────────────────────────────────────────────

Provide unit and integration tests for:

Security:
- Missing token → 401.
- Invalid role → 403.
- Member cannot access another member’s programme/log/job.
- Coach cannot access an unassigned member.
- ADMIN does not automatically bypass private-record rules.

CRUD:
- Exercise creation and filtering.
- Archive preserves historical references.
- Programme/session/item validation.
- Workout ownership and reference consistency.
- Stale edits produce a conflict.

Workflow:
- Invalid status transitions are rejected.
- Approval applies to a revision.
- Editing invalidates approval.
- Publication requires current approval.
- Members cannot see unpublished drafts.

AI:
- Valid structured output creates exactly one draft.
- Unknown exercise IDs are rejected.
- Invalid numeric fields are rejected.
- Malformed JSON is handled.
- Timeout is handled.
- Duplicate messages do not duplicate programmes.
- Retries eventually reach DLQ where appropriate.
- Generated programmes are never automatically published.

Progress:
- Metrics are calculated correctly from stored logs.
- Timed/bodyweight exercises do not receive invented load values.

Frontend:
- Guards and role-aware actions.
- Form validation.
- Job polling cleanup.
- Error/empty/loading states.

Use a deterministic fake AI adapter in automated tests.
Mark any real-model or browser checks that could not be executed.

────────────────────────────────────────────────────────
16. DOCUMENTATION AND DELIVERY
────────────────────────────────────────────────────────

Deliver:
- Working backend and frontend code.
- Flyway migrations.
- OpenAPI documentation.
- ER diagram.
- Programme state-transition diagram.
- AI-generation sequence diagram.
- RabbitMQ message examples.
- Role/permission matrix.
- Seed data for development.
- Example prompts and validated JSON output.
- Startup commands.
- Test commands and actual results.
- Integration assumptions for M1 and shared infrastructure.
- Remaining work and limitations.

Development seed data should include:
- A small exercise catalogue.
- A coach and assigned member referenced through the shared identity setup.
- A published manual programme.
- A pending AI-generated draft.
- Sample workout logs.

Seed scripts must be repeatable and development-only.

Do not modify another module’s database to create seed references.
Use its documented provisioning interface or coordinated seed process.

────────────────────────────────────────────────────────
17. IMPLEMENTATION ORDER
────────────────────────────────────────────────────────

Work incrementally:

Phase 1:
Inspect the repository.
Confirm existing authentication, profile/assignment contracts, API routing,
messaging conventions and database setup.
State assumptions and any missing dependencies.

Phase 2:
Implement migrations, entities, security and exercise CRUD.

Phase 3:
Implement manual programme editing, revisions, review and publication.

Phase 4:
Implement member workout logging and deterministic progress summaries.

Phase 5:
Implement asynchronous local LLM generation with validation and tests.

Phase 6:
Implement optional exercise illustrations.

Phase 7:
Complete integration tests, documentation and Docker validation.

Start with a working vertical slice:
Keycloak-authenticated coach
→ Angular exercise form
→ training-service
→ PostgreSQL
→ exercise appears in the catalogue.

Then expand phase by phase.

At the end of each phase:
- List files created or changed.
- Show commands run.
- Report actual test results.
- Identify blockers and unfinished work.

Do not claim unexecuted tests passed.
Do not label mocks, scaffolding or disabled features as complete.