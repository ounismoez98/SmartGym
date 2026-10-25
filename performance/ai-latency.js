import http from 'k6/http';
import { check } from 'k6';
import { bearerHeaders, isolatedTarget, requestTimeout } from './common.js';

if (__ENV.PERF_LIVE_GROQ_APPROVED !== 'true') {
  throw new Error('Refusing to call the live AI provider without PERF_LIVE_GROQ_APPROVED=true.');
}

const baseUrl = isolatedTarget();
const headers = bearerHeaders();

export const options = {
  vus: 1,
  iterations: 1,
  summaryTrendStats: ['avg', 'min', 'max', 'p(90)', 'p(95)'],
  thresholds: {
    http_req_failed: ['rate<1'],
    checks: ['rate>0.99']
  }
};

export default function () {
  const response = http.post(
    `${baseUrl}/api/nutrition/assistant/plan`,
    JSON.stringify({ restrictions: [] }),
    { timeout: requestTimeout, headers: { ...headers.headers, 'Content-Type': 'application/json' },
      tags: { endpoint: 'optional live AI generation' } }
  );
  check(response, {
    'AI generation returns an available meal plan': (res) => {
      if (res.status !== 200) return false;
      try {
        const result = res.json();
        return result.available === true && result.plan !== null;
      } catch {
        return false;
      }
    }
  });
}
