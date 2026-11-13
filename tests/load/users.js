import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const mode = __ENV.MODE || 'read';
const profile = __ENV.PROFILE || 'ramp';
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
if (!['read', 'create'].includes(mode)) {
  throw new Error('MODE must be read or create');
}
if (!['ramp', 'million'].includes(profile) || (profile === 'million' && mode !== 'read')) {
  throw new Error('PROFILE must be ramp or million; million supports MODE=read only');
}

export const options = {
  ...(profile === 'million' ? {
    scenarios: {
      million: { executor: 'shared-iterations', vus: 50, iterations: 1000000, maxDuration: '30m' },
    },
  } : { stages: [
    { duration: '5s', target: 1 },
    { duration: '10s', target: 5 },
    { duration: '10s', target: 10 },
    { duration: '5s', target: 0 },
  ] }),
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: [profile === 'million' ? 'rate==0' : 'rate<0.01'],
    'http_reqs{endpoint:read}': [profile === 'million' ? 'count==1000000' : 'count>0'],
    'http_req_duration{endpoint:read}': ['p(95)<500'],
    ...(mode === 'create' ? {
      'http_reqs{endpoint:create}': ['count>0'],
      'http_req_duration{endpoint:create}': ['p(95)<1000'],
    } : {}),
  },
};

export function setup() {
  const response = http.get(`${baseUrl}/users/1`, { tags: { endpoint: 'fixture' }, timeout: '10s' });
  if (response.status !== 200) {
    fail('Start the application with a fresh database before running this test');
  }
  const runId = __ENV.RUN_ID || String(Date.now()).slice(-6);
  if (!/^\d{6}$/.test(runId)) {
    fail('RUN_ID must contain exactly 6 digits');
  }
  return { runId };
}

function readUser(id) {
  return http.get(`${baseUrl}/users/${id}`, {
    tags: { endpoint: 'read', name: 'GET /users/:id' }, timeout: '10s',
  });
}

function validUser(response, expected) {
  if (response.status !== 200) return false;
  try {
    const user = response.json();
    return Object.entries(expected).every(([field, value]) => user[field] === value);
  } catch (_) {
    return false;
  }
}

export default function (data) {
  if (mode === 'read') {
    check(readUser(1), {
      'read returns the expected user in room 1': (response) =>
        validUser(response, { id: 1, name: 'Juan', roomId: 1 }),
    });
  } else {
    if (__VU > 999 || __ITER >= 1000000) fail('Unique test data range exhausted');
    const token = `${data.runId}${String(__VU).padStart(3, '0')}${String(__ITER).padStart(6, '0')}`;
    const payload = {
      name: 'carga', email: `load-${token}@example.test`, dni: token, phone: token,
      rol: __ITER % 2 === 0 ? 'admin' : 'superadmin',
    };
    const created = http.post(`${baseUrl}/users`, JSON.stringify(payload), {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'create', name: 'POST /users' }, timeout: '10s',
    });
    let id;
    try { id = created.json('id'); } catch (_) { /* Report malformed responses as failed checks. */ }
    const success = check(created, {
      'create returns 201 and a generated ID': (response) =>
        response.status === 201 && Number.isInteger(id) && id > 0,
    });
    if (success) {
      check(readUser(id), {
        'created user is persisted in room 1': (response) =>
          validUser(response, { ...payload, id, roomId: 1 }),
      });
    }
  }
  if (profile !== 'million') sleep(0.1);
}

export function handleSummary(data) {
  const metric = (name) => data.metrics[name]?.values || {};
  const report = {
    mode,
    profile,
    requests: metric('http_reqs').count,
    requestsPerSecond: metric('http_reqs').rate,
    failedRequestRate: metric('http_req_failed').rate,
    checkSuccessRate: metric('checks').rate,
    read: metric('http_req_duration{endpoint:read}'),
    readRequests: metric('http_reqs{endpoint:read}'),
    create: mode === 'create' ? metric('http_req_duration{endpoint:create}') : undefined,
    createRequests: mode === 'create' ? metric('http_reqs{endpoint:create}') : undefined,
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`/results/${profile === 'million' ? 'million-' : ''}${mode}.json`]: JSON.stringify(data, null, 2),
  };
}
