# Smart Gym performance checks

These scripts use k6 and the authenticated, member-scoped Nutrition API.
They do not create, update, or delete nutrition data. They do not call Groq
unless the separately gated `ai-latency.js` script is explicitly selected.

## Safety requirement

**No performance run was executed for this change.** At inspection time, the
only available application stack was the existing development Compose project:
its API, Keycloak, and PostgreSQL services were already running, and the API
uses the `smartgym` development database. Do not point load tests at that
stack, its database, production, or any shared environment.

Before a real run, provide a dedicated non-production deployment with all of
the following:

- Its own API instance and isolated database/volumes, with only disposable
  test-member records.
- An isolated Keycloak realm/issuer and a test member whose token has the
  `MEMBER` role and `smart-gym-api` audience accepted by that API.
- An API origin reachable from the k6 container. On Docker Desktop for
  Windows, a host-published isolated API is commonly reachable as
  `http://host.docker.internal:<isolated-port>`.
- `PERF_TARGET_ISOLATED=true` as an explicit operator confirmation.

Use the real issuer and signature-validated token for this isolated API.
Supply the bearer token through `PERF_BEARER_TOKEN` in the shell environment;
never put it in a script, shell history, checked-in file, or logs. The guard
does not prove environment isolation; the operator must verify the deployment,
database, ports, and identity issuer before confirming it.

Prerequisites: Docker and Docker Compose v2. The following commands mount
only this directory read-only and pin k6 to `grafana/k6:0.55.2`. Compose
configuration and existing services are not changed or restarted.

## Configure environment (PowerShell)

Set these values in the current PowerShell process, not in a committed file.
The token should be acquired using the isolated test realm's approved login
flow and copied without displaying it:

```powershell
$env:PERF_TARGET_ISOLATED = "true"
$env:PERF_BASE_URL = "http://host.docker.internal:8181"
$env:PERF_BEARER_TOKEN = "<short-lived isolated test-member access token>"
$env:PERF_VUS = "2"
$env:PERF_DURATION = "30s"
$env:PERF_REQUEST_TIMEOUT = "5s"
$env:PERF_P95_MS = "1000"
$env:PERF_MAX_FAILURE_RATE = "0.01"
$performance = (Resolve-Path .\performance).Path
```

Replace the example URL with the dedicated API's origin. Do not use the
development API's port `8081`. The script also refuses loopback URLs and the
known Compose ports `8080` (Keycloak), `8081` (API), and `8088` (frontend),
even when the isolation flag is set. This cannot identify every alias,
proxy, NAT mapping, or database behind an arbitrary origin; the operator
must still verify the isolated API's database and identity provider before
setting the flag. The profile and daily-totals requests require the test
member to have a profile; all three read-only requests must return HTTP 200
for this baseline.

## Scenario A/B — read-only API baseline and concurrent users

`api-readonly.js` requests the implemented `GET /api/nutrition/profiles`,
`GET /api/nutrition/body-measurements`, and
`GET /api/nutrition/food-journal/daily?date=YYYY-MM-DD` endpoints. It uses one
iteration per VU at a time with a one-second pause. Configure the fixed VU
count, duration, request timeout, and thresholds with the environment above:

```powershell
docker run --rm -i --mount "type=bind,source=$performance,target=/scripts,readonly" `
  -w /scripts -e PERF_TARGET_ISOLATED -e PERF_BASE_URL -e PERF_BEARER_TOKEN `
  -e PERF_VUS -e PERF_DURATION -e PERF_REQUEST_TIMEOUT -e PERF_P95_MS `
  -e PERF_MAX_FAILURE_RATE grafana/k6:0.55.2 run /scripts/api-readonly.js
```

k6 reports total `http_reqs`, request rate (requests/second), response-time
average/median/p90/p95/maximum, failed-request rate, checks, and the custom
counts for unexpected responses, 429/503 overload responses, and transport
failures (status 0) and request timeouts (timeout errors). Threshold defaults (p95 under 1000 ms and fewer than
1% failed HTTP requests) are **provisional starting values, not measured
service guarantees**. Override them only for the isolated run being assessed.

## Scenario C — authentication errors and controlled ramp

`auth-errors.js` deliberately makes unauthenticated GET requests to the
existing profile endpoint and checks that the API responds with HTTP 401.
This measures expected authentication rejection latency and unexpected
responses; it does not send a token.

```powershell
docker run --rm -i --mount "type=bind,source=$performance,target=/scripts,readonly" `
  -w /scripts -e PERF_TARGET_ISOLATED -e PERF_BASE_URL -e PERF_VUS `
  -e PERF_DURATION -e PERF_REQUEST_TIMEOUT -e PERF_P95_MS `
  grafana/k6:0.55.2 run /scripts/auth-errors.js
```

`overload-readonly.js` ramps only read-only measurement-list requests. It
requires the additional explicit `PERF_OVERLOAD_APPROVED=true` environment
gate. Its defaults are a 10-second ramp to 5 VUs, a 20-second hold, and a
10-second ramp down; configure `PERF_PEAK_VUS`, `PERF_RAMP_DURATION`, and
`PERF_HOLD_DURATION` only for the isolated system. The script reports
429/503 responses and transport failures separately while k6 reports all HTTP
failures and response-time percentiles.

```powershell
$env:PERF_OVERLOAD_APPROVED = "true"
$env:PERF_PEAK_VUS = "5"
$env:PERF_RAMP_DURATION = "10s"
$env:PERF_HOLD_DURATION = "20s"
docker run --rm -i --mount "type=bind,source=$performance,target=/scripts,readonly" `
  -w /scripts -e PERF_TARGET_ISOLATED -e PERF_OVERLOAD_APPROVED -e PERF_BASE_URL `
  -e PERF_BEARER_TOKEN -e PERF_PEAK_VUS -e PERF_RAMP_DURATION -e PERF_HOLD_DURATION `
  -e PERF_REQUEST_TIMEOUT -e PERF_P95_MS -e PERF_MAX_FAILURE_RATE `
  grafana/k6:0.55.2 run /scripts/overload-readonly.js
```

The overload scenario is deliberately not run as part of routine validation.
Obtain separate approval before running it. It does not saturate the Groq
executor. No isolated mock-AI load harness currently exists; executor
rejection/saturation testing remains blocked until one is provided.

## Scenario D — optional real Groq latency

This is separate from ordinary performance testing and makes a real request
through the isolated API to the configured Groq provider. It requires a valid
backend `GROQ_API_KEY`, supported `GROQ_MODEL`, an eligible test member with a
profile, and explicit authorization to incur provider usage and send the
generation context permitted by the application. The script defaults to
exactly one VU and one iteration and refuses to run unless
`PERF_LIVE_GROQ_APPROVED=true`.

```powershell
$env:PERF_LIVE_GROQ_APPROVED = "true"
docker run --rm -i --mount "type=bind,source=$performance,target=/scripts,readonly" `
  -w /scripts -e PERF_TARGET_ISOLATED -e PERF_LIVE_GROQ_APPROVED -e PERF_BASE_URL `
  -e PERF_BEARER_TOKEN -e PERF_REQUEST_TIMEOUT `
  grafana/k6:0.55.2 run /scripts/ai-latency.js
```

No live Groq request is made by the other scripts. The optional result is one
sample, not a latency distribution or a model availability guarantee. Never
pass the Groq API key to k6; it remains backend-only.

## Validation and resource observations

Validate script imports and options without sending HTTP requests:

```powershell
docker run --rm -i --mount "type=bind,source=$performance,target=/scripts,readonly" `
  -w /scripts -e PERF_TARGET_ISOLATED -e PERF_BASE_URL -e PERF_BEARER_TOKEN `
  grafana/k6:0.55.2 inspect --include-system-env-vars /scripts/api-readonly.js
```

When available, observe only the isolated API and database container resource
usage during a run with `docker stats <isolated-api-container>
<isolated-postgres-container>`. Capture CPU and memory alongside the k6
summary. Do not include Keycloak or other shared services in a test target.

There are no measured throughput, latency, overload, resource, or Groq
performance results yet. The current running development Compose stack is not
an acceptable target, so all real load scenarios remain **BLOCKED** until the
isolated environment above is prepared and verified.
