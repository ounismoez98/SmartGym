# Module 5 — Nutrition: Technical Implementation and Final Verification

**Verification date:** 2026-10-25
**Repository state:** Existing worktree changes were preserved. No files were staged or committed, no application services were restarted, and the development database was not modified.

> **Module naming:** This deliverable is titled Module 5 as requested. The repository's existing ownership table and Angular route metadata still identify Nutrition as M4; this report does not change project ownership or route metadata.

## 1. Executive summary and objectives

The Nutrition module now provides a member-centered workspace for profile onboarding, nutrition calculations, saved preferences, AI-generated meal suggestions, persistent daily and seven-day plans, food journaling, body measurements, and hydration. Members generate and approve their own plans. Assigned coaches can review and edit saved meals, with assignment and ownership checks performed by the backend.

The implementation retains the existing Angular, Spring Boot, PostgreSQL, Flyway, Keycloak/JWT and Groq architecture. New persistence is additive in Flyway V2–V5. The final standard backend verification, Angular tests/build, fresh PostgreSQL migration/persistence tests, Compose configuration, and k6 script inspection completed successfully as recorded below.

Implementation completeness is not equivalent to live service verification or production readiness: no real Groq inference, live Keycloak-authenticated nutrition journey, deployed API end-to-end test, or load benchmark was performed. Nutrition estimates and restriction checks do not guarantee medical suitability or allergy safety.

## 2. Module scope and implementation history

Work progressed through member-initiated AI generation and coach review; durable plan-specific restriction context; a redesigned Nutrition workspace and six-step onboarding; persistent daily/weekly plans, individual-day regeneration, and single-meal replacement; and persistent targets, preferences, timeline estimates, and hydration.

The current scope includes member and coach experiences, food CRUD, member-owned saved meals and plans, daily nutrition calculations from journal entries, body measurements, and hydration history. It does not include persisted shopping lists or alternatives, medical advice, guaranteed allergen filtering, or real-provider service-level verification.

## 3. Angular UI and member onboarding experience

The authenticated member route (`/nutrition`) uses the separate `NutritionWorkspaceComponent`, which reuses the nutrition component logic and presents distinct Overview, My Plan, My Progress, Food Library, Food Journal, and Profile setup views. Member access is guarded by authentication and the existing `ADMIN`/`MEMBER` route roles. The coach workspace is separately routed at `/coach/nutrition`.

The onboarding wizard displays one step at a time and includes:

1. Welcome and a visual-only male/female illustration selection.
2. Age, height, current weight and activity.
3. Backend BMI and available estimate explanations.
4. Fitness goal, target weight, optional desired date, and calorie-target choice.
5. Dietary style, separately recorded allergies/intolerances/dislikes, preferred ingredients, budget, preparation time, and cooking preferences.
6. Daily or weekly plan selection, generation, review and save.

Profile and preference values are persisted through authenticated APIs rather than only component state. Loading, validation, success, unavailable and error states are represented in the UI. Planned meals remain separate from the Food Journal. The repository has no food-photo assets; meal presentation uses local illustrations/fallback visuals rather than unstable external image URLs.

## 4. BMI and nutritional calculations, goal handling and timeline limitations

- **BMI:** Calculated from saved kilograms and centimetres converted to metres. It is described as a screening measurement, not a diagnosis or body-fat measure. The API rejects BMI calculation when a saved age is below 18; when age is absent, this endpoint currently does not have enough information to confirm adult status and still permits calculation. Target-weight validation and timeline estimates require adult age and valid current height/weight.
- **Macros:** Protein uses 1.6 g/kg, fat uses 25% of the saved calorie target, and carbohydrates receive remaining calories. Unsupported/nonpositive results are rejected rather than represented as valid targets.
- **Energy:** When age, height, weight, optional Mifflin–St Jeor equation selection, supported activity and goal are available, the backend estimates BMR/TDEE. The target applies an approximate 15% deficit for weight loss, maintenance calories, or a 5% surplus for muscle gain. Unsupported/missing inputs produce an unavailable explanation. This is not a personalized prescription.
- **Targets:** A target must be compatible with weight-loss, maintenance, or muscle-gain direction; target weight is rejected when it implies adult BMI below 18.5. An optional desired date must be future and is a personal goal, not a guaranteed completion date.
- **Timeline:** Backend-deterministic and limited to suitable adult weight-loss goals. The approximate range uses 0.25–0.5 kg/week and compares a requested date with the range. Maintenance, gain, minors, unknown age, invalid inputs and other unsupported cases return unavailable rather than applying the loss rate.

## 5. Groq integration, request handling, validation and error management

`LocalLlmMealPlanAssistant` calls the official Groq Chat Completions endpoint from the backend only. `GROQ_API_KEY` and `GROQ_MODEL` are configured through the API environment; the frontend does not receive the key. The request uses strict JSON Schema output. Returned content is still treated as untrusted: the application validates the response shape, meal types, quantities, numeric nutrition, and restricted terms before returning or persisting it.

Generation prompts use supported profile goal/calorie/diet-style context, saved food information, member restrictions and saved preference information. JWTs and personal age/height/weight are not included. Nutrition values are derived from saved food data only when supported; otherwise they remain model estimates requiring review.

The asynchronous executor has 2 core workers, 4 maximum workers and an 8-request queue. HTTP connect/read timeouts default to 5/120 seconds. Missing configuration, rate limits, provider authorization errors, timeouts, invalid HTTP responses, malformed JSON, rejected content and executor saturation return explicit unavailable/error outcomes instead of accepting fabricated plans. Automated Groq tests mock HTTP; they do not establish live inference.

## 6. Daily and weekly meal-plan generation, persistence, day regeneration and meal replacement

Members initiate generation from their own Nutrition workspace. The backend derives the member subject from the authenticated JWT. Generation supports a dated daily plan or seven-day weekly plan and returns structured draft days/meals, ingredients, quantities, preparation instructions and supported nutrition estimates.

On save, the backend binds the plan to its generation context and creates persistent dated day, meal and item records. The plan stores plan type, dates, member subject, goal, version and the original generation-context relationship. The original context stores the member subject and saved effective restrictions, including request restrictions, profile allergies/intolerances/dislikes and default pork/pork-derived exclusions.

Individual-day regeneration checks member ownership, weekly-plan day membership, generation context and expected version, then updates only the selected day. Individual-meal replacement likewise uses the stored context/version and preserves other meals. Failed generation is surfaced and does not intentionally clear the previously saved day/meal. Optimistic versioning (`@Version`) and expected-version requests protect concurrent edits.

Saved generation shopping lists and alternatives are not persisted. Generated suggestions and estimated nutrient values should not be interpreted as exact or medically verified.

## 7. Member approval and assigned-coach review/editing

Members create the suggestions and review them before saving. Coaches do not initiate AI generation and see saved member plans rather than a separate persisted draft queue. The schema has no separate draft/review state.

Coach routes require the `COACH` role. The backend resolves the supplied member profile ID, checks the authenticated coach-to-member assignment, scopes the operation to that member's subject, and checks plan/meal/food ownership. Persistent meal edits also carry an expected version and are checked against the plan's original generation restrictions. Cross-member access is not authorized by Angular state alone.

## 8. Foods, food journal, body measurements and hydration tracking

- **Foods and legacy meal records:** Members can create, list, edit and delete their own foods and meal records. Nutrient values and quantities are validated. Food deletion is rejected while journal or meal-plan references exist. Assigned coaches can review/edit permitted assigned-member food/meal data.
- **Food Journal:** Stores consumed-food references, servings, date, meal type and notes. Daily totals are calculated separately from planned meals. Because journal entries reference the current food record rather than a nutrient snapshot, editing that food may change recalculated totals for earlier dates.
- **Body measurements:** Member-owned dated measurements include weight and supported circumference measurements, with history and update/delete support.
- **Hydration:** Authenticated member-owned logs represent drinking water only. Records can be added, viewed, corrected and deleted, and history is queryable. The general estimate is 2,000 mL per UTC day; individual logs are limited to 1–2,000 mL, total daily logged water to 10,000 mL, and timestamps to the past five years through five minutes in the future. The target is neither a personalized medical prescription nor total water including food moisture.

## 9. Backend architecture, actual API endpoints, security, ownership and role separation

The API is Spring Boot with Spring Security resource-server JWT validation, Spring Data JPA repositories and service-layer ownership checks. `/api/nutrition/**` requires `ADMIN` or `MEMBER`; `/api/nutrition/coach/**` requires `COACH`. Nutrition record ownership is derived from JWT subject. Coach member profile IDs are resolved and verified against server-side assignment records for each operation.

Principal routes under `/api/nutrition` include:

| Area | Routes |
|---|---|
| Foods / legacy meals | `POST/GET /foods`, `PUT/DELETE /foods/{foodId}`; `POST/GET /meal-plans`, `PUT/DELETE /meal-plans/{mealPlanId}` |
| Journal / measurements | `POST/GET /food-journal`, `PUT/DELETE /food-journal/{entryId}`, `GET /food-journal/daily?date=...`; `POST/GET /body-measurements`, `PUT/DELETE /body-measurements/{measurementId}` |
| Profile / calculations | `POST/GET /profiles`, `GET /calculations`, `/calculations/bmi`, `/calculations/macros`, `/calculations/energy`, `/calculations/goal-timeline` |
| Hydration | `POST/GET /hydration`, `GET /hydration/history`, `PUT/DELETE /hydration/{logId}` |
| AI / plans | `POST /assistant/plan`, `/assistant/plans/draft`, `/assistant/plan/save`; `POST/GET /plans`, `GET /plans/{planId}`, `POST /plans/{planId}/days/{date}/regenerate`, `POST /plans/{planId}/meals/{mealId}/replace` |

Coach routes under `/api/nutrition/coach/members/{memberProfileId}` expose assigned-member foods, legacy meal plans, persistent plans, and persistent meal editing. They include `GET /foods`, `PUT/DELETE /foods/{foodId}`, `GET /meal-plans`, `PUT /meal-plans/{mealPlanId}`, `GET /plans`, and `PUT /plans/{planId}/days/{date}/meals/{mealId}`.

The current request contracts preserve the existing identity model; member IDs supplied in route parameters identify the requested coach-review subject but are not trusted without assignment resolution. Client restrictions are not authoritative during a saved-plan edit.

## 10. Database entities, relationships, Flyway migrations V1–V5, and data preservation

Key entities are `NutritionProfile`, `Food`, `MealPlan`, `MealPlanGenerationContext`, `NutritionPlan`, `NutritionPlanDay`, `NutritionPlanMeal`/meal items, `FoodJournal`, `BodyMeasurement`, and `HydrationLog`.

| Migration | Schema change |
|---|---|
| V1 — baseline | Membership/coach assignment tables and legacy nutrition profile, foods, individual meal plans/items, journal and body measurements. |
| V2 — automatic calorie target | Adds calorie-target mode (existing records default to `MANUAL`) and optional equation selection. |
| V3 — generation context | Adds member-owned contexts, restriction collection and a nullable context link from legacy meals. |
| V4 — persistent nutrition plans | Adds daily/weekly plan, dated-day, meal and meal-item tables; plans reference generation context, have constrained date ranges and optimistic version storage. |
| V5 — goals/preferences/hydration | Adds target weight/date and cooking fields, separate allergy/intolerance/dislike/preferred-ingredient collection tables, and indexed hydration logs with amount constraints. |

The legacy `nutrition_meal_plans` and its food-item relationship remain in place for existing APIs/data. New complete daily/weekly plans are represented by the separate plan/day/meal/item aggregate rather than pretending a series of legacy meals is a dated full plan. V2–V5 are additive in the current worktree; none was applied to the development database. The final migration run was against a fresh disposable PostgreSQL database only. The current worktree reports V2–V5 as untracked files; they were preserved and not staged.

## 11. Unit-test results

**Status: PASS** for the standard backend test/package run.
**Command:** `mvn -B verify` in the Maven 3.9 / Java 21 container, from `backend`.

Surefire reported **78 total tests, 72 passed, 0 failures, 0 errors, 6 skipped**. The six skips are the opt-in PostgreSQL integration tests, which were run separately. Groq-facing tests use mocked HTTP responses. Relevant test files include `NutritionControllerTest`, `NutritionControllerAsyncHandlingTest`, `NutritionGoalTimelineTest`, `NutritionGenerationContextTest`, `NutritionPlanPersistenceTest`, `CoachNutritionControllerTest`, and `LocalLlmMealPlanAssistantTest`. The package and JaCoCo verify lifecycle completed successfully.

## 12. White-box testing and measured JaCoCo coverage

**Status: PASS — current report generated by the final backend `mvn -B verify` run.** JaCoCo 0.8.12 report files are under `backend/target/site/jacoco/`.

| Metric | Covered / total | Coverage |
|---|---:|---:|
| Instructions | 9,845 / 11,068 | 88.95% |
| Lines | 1,646 / 1,843 | 89.31% |
| Branches | 724 / 1,194 | 60.64% |

HTML: `backend/target/site/jacoco/index.html`; XML and CSV are adjacent. These measurements are from the standard verify run; the separately executed PostgreSQL tests are not folded into these coverage figures. Coverage is measurement, not a correctness or safety guarantee.

## 13. Black-box API testing

**Status: PASS for in-process API/controller contract tests; NOT RUN for an external deployed API black-box campaign.** `NutritionControllerTest` ran 31 tests, `CoachNutritionControllerTest` ran 3, and `NutritionControllerAsyncHandlingTest` ran 2, all passing in the final standard backend suite. MockMvc exercises HTTP mappings/statuses and synthetic authenticated principals; these tests cover profile/hydration validation, role/ownership behavior, plan operations and async handling.

These tests do not prove live JWT signature validation by Keycloak, real reverse-proxy routing, or browser-to-running-API behavior.

## 14. PostgreSQL integration tests

**Status: PASS.** Command executed with `-Dtest=PostgresNutritionIntegrationTest test` against a newly created PostgreSQL 16.15 container using a disposable `smartgym_it` database and a tmpfs data directory.

Result: **6 tests, 6 passed, 0 failures, 0 errors, 0 skipped**. Flyway validated and applied V1, V2, V3, V4 and V5 in order, reaching schema version v5. Tests cover empty-schema migration, refusal to silently baseline a nonempty schema without history, entity persistence/update/delete, profile/preferences/hydration persistence, dated plan/context persistence, foreign-key integrity and transaction rollback. The disposable container was stopped after the run. No development database, existing volume or running service was altered.

The integration suite must only be pointed at a disposable test database. It applies Flyway migrations to the configured database.

## 15. Angular tests and frontend coverage

**Status: PASS.** The full Angular Karma/Jasmine suite ran in headless Chromium using `npm run test:coverage`: **56 tests passed, 0 failed**. The production build also passed with `npm run build`.

| Metric | Covered / total | Coverage |
|---|---:|---:|
| Statements | 552 / 833 | 66.26% |
| Branches | 161 / 340 | 47.35% |
| Functions | 183 / 269 | 68.02% |
| Lines | 513 / 744 | 68.95% |

The configured report is `frontend/coverage/smart-gym/index.html`. Tests cover the Nutrition API client, workspace/onboarding, generation/review/save, hydration form behavior, assigned-coach UI and auth routing. They use test doubles; they are not live-service end-to-end tests.

## 16. Performance-test scripts and actual performance results

**Script validation: PASS. Benchmark: NOT RUN.** The four k6 files (`api-readonly.js`, `overload-readonly.js`, `auth-errors.js`, and `ai-latency.js`) were inspected with pinned k6 0.55.2 and explicit placeholder-only environment values satisfying their safety guards. `k6 inspect` parsed configuration; it did not start VUs or send HTTP requests.

Isolation confirmation, loopback/shared Compose port rejection, the explicit overload approval gate, and the explicit live-Groq approval gate remain in the scripts. No throughput, latency, overload, resource, or Groq performance result exists. A validated k6 script is not a completed load benchmark.

## 17. Groq and Keycloak real-service verification status

- **Groq live inference: NOT RUN / NOT VERIFIED.** `.env.example` leaves `GROQ_API_KEY` empty and the model value is only a default. Mocked Groq responses verify application handling, not that the account, model, credentials, quotas, schema support or live output work.
- **Keycloak live Nutrition authorization: NOT RUN / NOT VERIFIED.** Automated tests use synthetic JWT principals/test security. They do not prove issuer/JWKS connectivity, live role claims, browser redirect/callback, audience configuration, or a complete authenticated Nutrition journey.
- **Compose configuration: PASS** via `docker compose config --quiet`. This validates configuration, not deployed service health or live integration.

## 18. Known limitations, blocked checks and remaining risks

1. Shopping lists and alternatives are review-only; they are not persisted in the plan schema.
2. There is no separate persisted draft/review/approval state. The member saves after reviewing; coach review follows save.
3. AI nutrition estimates may be approximate. Restriction matching is text-based and may miss synonyms, ambiguous ingredients, cross-contamination or facts omitted by a provider. Neither this nor the saved restriction snapshot guarantees allergy safety or medical suitability.
4. BMI is a screening measure, not diagnosis. BMI calculation currently denies a known under-18 profile but does not reject an age-missing profile. Target validation and timeline calculation require an adult age.
5. Timeline estimates are weight-loss-only and do not model plateaus, health conditions, adherence, or individual response; gain/maintenance timelines are unavailable.
6. Hydration is drink-only, uses UTC days and a broad 2,000 mL reference; it is not an individualized target.
7. Journal entries reference foods rather than nutrient snapshots, so editing a saved food can change historical recalculated totals.
8. Live Groq inference, live Keycloak-backed API/browser verification, deployment readiness, and performance benchmark are not established by this verification.
9. The repository worktree already contained extensive modified and untracked files. They were not staged or committed; the additional documentation change is limited to this report and the README consistency corrections.

## 19. Exact commands required to reproduce the tests and builds

Run from the repository root in PowerShell. These commands use the project's documented container toolchain; use only disposable databases for integration tests.

```powershell
# Compose and whitespace checks
docker compose config --quiet
git diff --check

# Backend tests, package and JaCoCo report (standard H2 test profile)
$repo = (Get-Location).Path
$backend = Join-Path $repo 'backend'
docker run --rm `
  -v "${backend}:/workspace" `
  -v smartgym_maven_cache:/root/.m2 `
  -w /workspace maven:3.9-eclipse-temurin-21 mvn -B verify

# Fresh, disposable PostgreSQL only. Choose an unused host port and throwaway
# password; never substitute the development database or its credentials.
$env:SMARTGYM_IT_PASSWORD = [guid]::NewGuid().ToString('N')
docker run --rm -d --name smartgym-module5-it `
  -e POSTGRES_DB=smartgym_it `
  -e POSTGRES_USER=smartgym_it `
  -e "POSTGRES_PASSWORD=$env:SMARTGYM_IT_PASSWORD" `
  -p 55449:5432 --tmpfs /var/lib/postgresql/data postgres:16-alpine

# Wait until `docker exec smartgym-module5-it pg_isready -U smartgym_it
# -d smartgym_it` reports accepting connections, then run the integration test:
docker run --rm `
  -v "${backend}:/workspace" `
  -v smartgym_maven_cache:/root/.m2 `
  -w /workspace maven:3.9-eclipse-temurin-21 mvn -B `
  "-Dsmartgym.postgres.it.url=jdbc:postgresql://host.docker.internal:55449/smartgym_it" `
  "-Dsmartgym.postgres.it.username=smartgym_it" `
  "-Dsmartgym.postgres.it.password=$env:SMARTGYM_IT_PASSWORD" `
  "-Dtest=PostgresNutritionIntegrationTest" test
docker stop smartgym-module5-it
Remove-Item Env:SMARTGYM_IT_PASSWORD

# Frontend tests/coverage and production build. Install dependencies with
# `npm ci` in frontend if the node_modules cache is absent.
$frontend = Join-Path $repo 'frontend'
docker run --rm `
  -v "${frontend}:/workspace" `
  -v smartgym_node_modules:/workspace/node_modules `
  -w /workspace node:24-alpine sh -c `
  "apk add --no-cache chromium && CHROME_BIN=/usr/bin/chromium-browser npm run test:coverage"
docker run --rm `
  -v "${frontend}:/workspace" `
  -v smartgym_node_modules:/workspace/node_modules `
  -w /workspace node:24-alpine npm run build

# Validate k6 configuration only; use placeholder environment values and
# inspect, not `run`, to avoid HTTP requests.
docker run --rm `
  -e PERF_TARGET_ISOLATED=true `
  -e PERF_BASE_URL=https://api.isolated.example `
  -e PERF_BEARER_TOKEN=validation-only `
  -e PERF_OVERLOAD_APPROVED=true `
  -e PERF_LIVE_GROQ_APPROVED=true `
  -v "${repo}\performance:/scripts:ro" `
  grafana/k6:0.55.2 inspect /scripts/api-readonly.js
```

Repeat the k6 `inspect` command for `overload-readonly.js`, `auth-errors.js`, and `ai-latency.js` to inspect all scripts. Do not use `k6 run` against development, shared, or production services. A real performance campaign requires the isolated setup and operator approvals documented in `performance/README.md`.

## 20. Final assessment and recommended next actions

**Implementation completeness:** The member profile/preferences, plan generation and persistence, saved restriction context, assigned-coach editing, journal, measurements and hydration paths are implemented and verified by current automated test/build checks and fresh PostgreSQL migration/persistence tests.

**Live end-to-end verification:** Incomplete. No Groq inference or full Keycloak-authenticated browser-to-API scenario was executed. These checks require approved test identities and provider credentials in an isolated environment.

**Production readiness:** Not established. Before production use, review the outstanding safety/privacy and deployment risks, validate migrations against a production-like copy, establish monitoring/backup/restore and operational policies, and run approved end-to-end and isolated performance tests. Do not interpret estimates or text restriction checks as medical or allergy guarantees.
