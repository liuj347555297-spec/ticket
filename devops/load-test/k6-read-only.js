import http from 'k6/http'
import { check, sleep } from 'k6'

const baseUrl = __ENV.SERVICEHUB_RUNTIME_BASE_URL?.replace(/\/$/, '')
const headersJson = __ENV.SERVICEHUB_LOAD_HEADERS_JSON
if (!baseUrl || !/^https?:\/\/[^/\s]+$/.test(baseUrl)) {
  throw new Error('SERVICEHUB_RUNTIME_BASE_URL must be an http(s) origin without a path')
}
if (!headersJson) {
  throw new Error('SERVICEHUB_LOAD_HEADERS_JSON is required; do not embed credentials in this script')
}

let headers
try {
  headers = JSON.parse(headersJson)
} catch {
  throw new Error('SERVICEHUB_LOAD_HEADERS_JSON must be a JSON object')
}
if (!headers || Array.isArray(headers) || typeof headers !== 'object') {
  throw new Error('SERVICEHUB_LOAD_HEADERS_JSON must be a JSON object')
}
if (__ENV.SERVICEHUB_LOAD_ENVIRONMENT === 'production' && Object.keys(headers).some((key) => key.toLowerCase() === 'x-servicehub-dev-identity')) {
  throw new Error('the local development identity header is forbidden for a production load test')
}

export const options = {
  scenarios: {
    read_only_baseline: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.SERVICEHUB_LOAD_RAMP_UP || '2m', target: Number(__ENV.SERVICEHUB_LOAD_VUS || '100') },
        { duration: __ENV.SERVICEHUB_LOAD_HOLD || '5m', target: Number(__ENV.SERVICEHUB_LOAD_VUS || '100') },
        { duration: __ENV.SERVICEHUB_LOAD_RAMP_DOWN || '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    checks: ['rate>0.99'],
  },
}

export default function () {
  const response = http.get(`${baseUrl}/api/v1/system/ping`, { headers, tags: { operation: 'system-ping' } })
  check(response, { 'read-only request returned 200': (result) => result.status === 200 })
  sleep(Number(__ENV.SERVICEHUB_LOAD_THINK_SECONDS || '1'))
}
