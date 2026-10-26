# SmartGym full test report

**Date:** 2026-10-26
**Result:** Frontend and backend suites pass, and the authenticated membership browser E2E passes, including assistant explanation against a local mock provider. The new API error handling distinguishes rejected provider credentials from model/endpoint configuration errors. Docker-dependent integration tests and the training module remain unverified.

## Summary

The frontend and API are running locally. The API's OpenAPI document lists 67 routes: 28 membership, 31 nutrition, 7 feedback, and 1 development-provisioning route. Membership, nutrition, feedback, and AI-related backend tests pass. The authenticated Playwright membership workflow passed against the local Compose stack and mock LLM. It exercised administrator-created Keycloak accounts with generated-ID linking, subscriptions, payment correction, reminder generation and delivery to MailHog, and member access control. No live AI provider request was made.

The repaired Flyway migrations were also applied to a disposable legacy-schema database and to the existing Compose database without resetting its volume. The original four business profiles were migrated into two member and two employee records; both coach assignments remain active.

## Results

| Area | Result | Details |
| --- | --- | --- |
| Compose configuration | Pass | `docker compose config --quiet` succeeds. |
| Frontend unit tests | Pass | `npm test`: 61 tests passed. |
| Frontend production build | Pass with warning | `npm run build` succeeds. Initial bundle is 711.02 kB, 61.02 kB above the 650 kB warning budget (below the 1 MB error budget). |
| Backend test suite | Pass with skips | Latest full suite: 209 tests total, 0 failures/errors, 10 skipped. Tests include safe LLM credential/model diagnostics and assistant error handling. |
| PostgreSQL nutrition integration tests | Pass | All six passed against a temporary `smartgym_it` database. The temporary database and role were removed after the run. |
| PostgreSQL migration Testcontainers tests | Skipped | Four migration tests remain skipped because Testcontainers cannot validate the local Docker Desktop named-pipe endpoint, although Docker CLI/Compose work. |
| Flyway migration validation | Pass | Disposable legacy-schema database applied migrations through v100. A synthetic member, employee, and coach assignment survived; the legacy profile table was removed. |
| Existing database migration | Pass | Existing database migrated without a volume reset. Four legacy profiles became two members and two employees; both assignments remain active. Flyway recorded 15 successful entries, including the baseline and development seed. |
| API startup and route discovery | Pass | API starts successfully; `/v3/api-docs` returns HTTP 200 and lists 67 routes: 28 membership, 31 nutrition, 7 feedback, and 1 development-provisioning route. |
| Frontend/API connectivity | Pass, unauthenticated | Frontend homepage returns HTTP 200. `/api/membership/me` reaches the API and returns HTTP 401 without an access token, as expected. |
| Authenticated browser E2E | Pass | The updated Playwright flow passed using local Keycloak, development accounts, and the mock LLM. It verified new member and coach accounts, their Keycloak-generated linked IDs, the required first-login password-change screen, membership operations, and owner-scope authorization. |
| Keycloak, PostgreSQL, MailHog, RabbitMQ | Pass | Compose dependencies report healthy/running. RabbitMQ management requires authentication. |
| AI unit tests | Pass | Backend assistant and Groq client/service unit tests ran as part of Maven tests. No external provider request was made. |
| AI E2E/live provider | Mock pass / live not run | Assistant explanation and reminder generation were exercised against the local mock LLM. No external provider request was made; the key shared in chat should be revoked/rotated. |
| Training module | Not testable from source | `training-service` contains only generated `target` artifacts; no source or build manifest is present, and it is not a root Compose service. |

## Module coverage

- **Membership:** Backend controller/service tests, Keycloak provisioning tests, and the authenticated admin/member browser flow passed. The live E2E verified member/coach account creation, generated-ID linking, required first-login password change, and assistant explanation using the mock provider.
- **Nutrition:** Backend controller, generation, calculations, persistence, and assistant tests passed, including all six PostgreSQL integration tests on a temporary database. The live external AI provider was not called.
- **Feedback:** Backend controller and AI service tests passed. Authenticated browser behavior was not exercised.
- **Frontend:** All 61 configured unit tests passed; production build succeeded with the bundle budget warning.
- **Infrastructure/migrations:** Compose config is valid, core containers are running, and legacy database migration/data preservation was verified separately. Nginx now re-resolves the API service address after container recreation; proxy recovery was verified after replacing the API container.
- **Training:** No source/build inputs are available to test.

## Changes made during verification

- Resolved duplicate Flyway migration versions by preserving membership migrations V1–V7 and moving nutrition/feedback migrations to V8–V13.
- Added a Flyway callback to normalize the legacy coach-assignment unique constraint before the history migration.
- Updated stale nutrition test fixtures to use the current member/employee domain model.
- Repaired the frontend test configuration; all 61 frontend unit tests pass.
- Updated Nginx API proxying to use Docker DNS resolution so API container IP changes no longer leave the frontend returning 502.
- Restored a placeholder value in the tracked `.env.example`; it must never contain a real API key.

## Remaining work

1. Resolve the local Docker Desktop/Testcontainers API compatibility issue to run the skipped integration tests.
2. Revoke/rotate the AI key exposed in the troubleshooting conversation. Keep any replacement in the local `.env`, not `.env.example` or chat.
3. Restore the training module source/build inputs or confirm that it is intentionally out of scope.
4. Reduce or split the frontend initial bundle if the 650 kB warning budget is a release requirement.
