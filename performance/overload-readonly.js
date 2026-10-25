import http from 'k6/http';
import { check, sleep } from 'k6';
import { bearerHeaders, isolatedTarget, numericEnv, recordOutcome, requestTimeout, requestTimeouts, unexpectedResponses } from './common.js';

if (__ENV.PERF_OVERLOAD_APPROVED !== 'true') {
  throw new Error('Refusing to run ramp/load scenario without PERF_OVERLOAD_APPROVED=true.');
}

const baseUrl = isolatedTarget();
const headers = bearerHeaders();
const peakVus = numericEnv('PERF_PEAK_VUS', 5, 1);
const ramp = __ENV.PERF_RAMP_DURATION || '10s';
const hold = __ENV.PERF_HOLD_DURATION || '20s';
const p95Ms = numericEnv('PERF_P95_MS', 1500, 1);
const maxFailureRate = numericEnv('PERF_MAX_FAILURE_RATE', 0.02, 0, 1);

export const options = {
  stages: [
    { duration: ramp, target: peakVus },
    { duration: hold, target: peakVus },
    { duration: ramp, target: 0 }
  ],
  summaryTrendStats: ['avg', 'min', 'max', 'p(90)', 'p(95)'],
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
  const response = http.get(`${baseUrl}/api/nutrition/body-measurements`, {
    ...headers,
    timeout: requestTimeout,
    tags: { endpoint: 'body measurements under controlled ramp' }
  });
  recordOutcome(response, 'body measurements');
  const successful = check(response, { 'body measurements returns HTTP 200': (res) => res.status === 200 });
  if (!successful) unexpectedResponses.add(1, { endpoint: 'body measurements', status: String(response.status) });
  sleep(1);
}
