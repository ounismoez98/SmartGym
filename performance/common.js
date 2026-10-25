import { Counter } from 'k6/metrics';

export const unexpectedResponses = new Counter('unexpected_responses');
export const overloadedResponses = new Counter('overloaded_responses');
export const transportFailures = new Counter('transport_failures');
export const requestTimeouts = new Counter('request_timeouts');

export function recordOutcome(response, endpoint) {
  if (response.status === 429 || response.status === 503) {
    overloadedResponses.add(1, { endpoint, status: String(response.status) });
  }
  if (response.status === 0) {
    transportFailures.add(1, { endpoint });
  }
  if (typeof response.error === 'string' && /timeout/i.test(response.error)) {
    requestTimeouts.add(1, { endpoint });
  }
}

export function isolatedTarget() {
  if (__ENV.PERF_TARGET_ISOLATED !== 'true') {
    throw new Error('Refusing to run: set PERF_TARGET_ISOLATED=true only for a dedicated isolated test deployment and database.');
  }

  const baseUrl = __ENV.PERF_BASE_URL;
  if (!baseUrl) {
    throw new Error('Set PERF_BASE_URL to the origin of a dedicated isolated API deployment.');
  }

  let target;
  try {
    target = new URL(baseUrl);
  } catch {
    throw new Error('PERF_BASE_URL must be a valid HTTP or HTTPS API origin.');
  }
  if (!['http:', 'https:'].includes(target.protocol)
      || !target.hostname
      || target.username
      || target.password
      || target.pathname !== '/'
      || target.search
      || target.hash) {
    throw new Error('PERF_BASE_URL must contain only the HTTP(S) origin of the isolated API.');
  }

  const hostname = target.hostname.toLowerCase();
  const sharedComposePorts = new Set(['8080', '8081', '8088']);
  if (['localhost', '127.0.0.1', '::1'].includes(hostname)
      || sharedComposePorts.has(target.port)) {
    throw new Error('Refusing to target loopback or a known shared Smart Gym Compose port (8080, 8081, 8088).');
  }

  return target.origin;
}

export function bearerHeaders() {
  const token = __ENV.PERF_BEARER_TOKEN;
  if (!token) {
    throw new Error('Set PERF_BEARER_TOKEN in the environment; do not put tokens in script files or command arguments.');
  }
  return { headers: { Authorization: `Bearer ${token}` } };
}

export function numericEnv(name, fallback, minimum, maximum = Number.POSITIVE_INFINITY) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be a number between ${minimum} and ${maximum}.`);
  }
  return value;
}

export const requestTimeout = __ENV.PERF_REQUEST_TIMEOUT || '5s';
