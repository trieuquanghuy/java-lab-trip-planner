// k6-search-load.js — Load test for /api/destinations/search endpoint.
// Target: 100 RPS for 60 seconds, p95 < 500ms threshold.
//
// Prerequisites: k6 installed (brew install k6), services running via docker compose.
// Usage: k6 run scripts/k6-search-load.js
//   or:  k6 run --env BASE_URL=http://localhost:8180 scripts/k6-search-load.js

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    constant_rate: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 150,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180';
const queries = ['Tokyo', 'Paris', 'London', 'New York', 'Sydney', 'Rome', 'Bangkok', 'Berlin', 'Seoul', 'Madrid'];

export default function () {
  const q = queries[Math.floor(Math.random() * queries.length)];
  const res = http.get(`${BASE_URL}/api/destinations/search?query=${encodeURIComponent(q)}&page=0&size=10`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'has content': (r) => r.body.length > 2,
  });
}
