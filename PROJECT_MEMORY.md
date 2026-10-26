# Smart Gym — Project Memory

> Compact handoff for future work. Update this file when the project state,
> decisions, verification, or next priorities materially change. Do not store
> credentials, tokens, personal data, or copies of `.env` here.

## Current checkpoint — 2026-10-25

### Project state

- Authentication vertical slice and **Feedback & Satisfaction Analysis** module with
  Qwen AI via Groq API are fully implemented and verified.
- Development stack configured for Angular, Spring Boot, PostgreSQL, and Keycloak.
- Local secrets in `.env` (template in `.env.example`), including `GROQ_API_KEY`
  and `GROQ_MODEL`.

### Implemented architecture

- `frontend/`:
  - Angular SPA with Keycloak authentication.
  - Added `FeedbackService` and `FeedbackComponent` at `/feedback`.
  - Member mode: 1–5 star interactive rating, category selection, detailed comments,
    instant submission with real-time AI analysis feedback, and personal feedback history.
  - Admin mode: Executive Satisfaction Dashboard featuring total reviews, average rating,
    positive satisfaction %, sentiment distribution breakdown bars (Positive, Neutral, Negative),
    star distribution (1–5), common feedback themes cloud (clickable filter), category performance
    metrics, search & filter controls, and management actions (delete, re-analyze with AI).
  - Reverse proxy configured in `nginx.conf` for `/api/feedback/`.
  - Same-origin API token interceptor allowlist includes `/api/feedback/`.
- `backend/`:
  - `com.smartgym.membership.feedback`:
    - Entity `Feedback` with rating (1-5), category enum (`CLEANLINESS`, `EQUIPMENT`, `CLASSES`,
      `STAFF`, `SERVICE`), comment, AI sentiment (`POSITIVE`, `NEUTRAL`, `NEGATIVE`), summary,
      and themes (`feedback_themes` collection table).
    - `GroqAiService`: calls Qwen via Groq API (`https://api.groq.com/openai/v1/chat/completions`)
      with structured JSON response format (`response_format: {"type": "json_object"}`), strict schema
      validation, API key isolation, and automatic fallback to intelligent heuristic satisfaction analysis
      if Groq is unconfigured or encounters API failures. Never exposes API key to client or allows
      LLM direct database access.
    - `FeedbackService`: manages submissions, personal history (`jwt.getSubject()` isolation),
      admin filtering/searching, deletion, re-analysis, and dashboard statistics aggregation.
    - `FeedbackController`: REST endpoints at `/api/feedback` secured with Spring Security JWT.
- Backend access checks:
  - `POST /api/feedback`: members and admins can submit.
  - `GET /api/feedback/my`: strictly returns authenticated user's own feedback records.
  - `GET /api/feedback/admin/**`, `DELETE /api/feedback/admin/**`, `POST /api/feedback/admin/**`:
    strictly restricted to `ROLE_ADMIN`.

### Verified

- `npm.cmd run build` in `frontend/`: passed without errors or warnings.
- `mvn.cmd test` in `backend/`: passed, 19 tests across Membership and Feedback controllers/services.
- Role isolation, unauthenticated access denials (401), authorization denials (403), AI fallback,
  input validation, and stats calculations verified through automated test suites.

### Important paths

- Start stack: `docker compose up --build -d`
- Provision users: see commands in `README.md`; requires local environment
  variables. Never paste development passwords into chat or docs.
- Angular: `frontend/src/app/`
- JWT/security/API: `backend/src/main/java/com/smartgym/membership/`
- API access-control tests:
  `backend/src/test/java/com/smartgym/membership/api/MembershipControllerTest.java`
- Realm/client/claims: `keycloak/realm-import/gym-realm.json`

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
