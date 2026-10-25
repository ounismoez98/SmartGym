import http from 'k6/http';
import { check, sleep } from 'k6';
import { bearerHeaders, isolatedTarget, numericEnv, recordOutcome, requestTimeout, requestTimeouts, unexpectedResponses } from './common.js';

const baseUrl = isolatedTarget();
const headers = bearerHeaders();
const vus = numericEnv('PERF_VUS', 1, 1);
const p95Ms = numericEnv('PERF_P95_MS', 1000, 1);
const maxFailureRate = numericEnv('PERF_MAX_FAILURE_RATE', 0.01, 0, 1);

export const options = {
  vus,
  duration: __ENV.PERF_DURATION || '30s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)'],
  thresholds: {
    http_req_duration: [`p(95)<${p95Ms}`],
    http_req_failed: [`rate<${maxFailureRate}`],
    checks: ['rate>0.99'],
    unexpected_responses: ['count==0'],
    transport_failures: ['count==0'],
    request_timeouts: ['count==0']
  }
};

export default function () {
  const date = new Date().toISOString().slice(0, 10);
  const requests = [
    ['nutrition profile', `${baseUrl}/api/nutrition/profiles`],
    ['body measurements', `${baseUrl}/api/nutrition/body-measurements`],
    ['daily journal totals', `${baseUrl}/api/nutrition/food-journal/daily?date=${date}`]
  ];

  for (const [name, url] of requests) {
    const response = http.get(url, { ...headers, timeout: requestTimeout, tags: { endpoint: name } });
    recordOutcome(response, name);
    const successful = check(response, { [`${name}: HTTP 200`]: (res) => res.status === 200 });
    if (!successful) unexpectedResponses.add(1, { endpoint: name, status: String(response.status) });
  }
  sleep(1);
}
