import http from 'k6/http';
import { check, sleep } from 'k6';
import { isolatedTarget, numericEnv, recordOutcome, requestTimeout, requestTimeouts, unexpectedResponses } from './common.js';

const baseUrl = isolatedTarget();
const vus = numericEnv('PERF_VUS', 1, 1);

export const options = {
  vus,
  duration: __ENV.PERF_DURATION || '15s',
  summaryTrendStats: ['avg', 'min', 'max', 'p(90)', 'p(95)'],
  thresholds: {
    http_req_duration: [`p(95)<${numericEnv('PERF_P95_MS', 1000, 1)}`],
    checks: ['rate>0.99'],
    unexpected_responses: ['count==0'],
    transport_failures: ['count==0'],
    request_timeouts: ['count==0']
  }
};

export default function () {
  const response = http.get(`${baseUrl}/api/nutrition/profiles`, {
    timeout: requestTimeout,
    tags: { endpoint: 'unauthenticated profile' }
  });
  recordOutcome(response, 'unauthenticated profile');
  const expected = check(response, {
    'missing bearer token is rejected with HTTP 401': (res) => res.status === 401
  });
  if (!expected) unexpectedResponses.add(1, { endpoint: 'unauthenticated profile', status: String(response.status) });
  sleep(1);
}
