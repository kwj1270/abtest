import http from 'k6/http';
import { randomBytes } from 'k6/crypto';
import encoding from 'k6/encoding';
import { check, sleep } from 'k6';

const base = __ENV.BASE_URL || 'http://localhost:8080';
export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '2m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};
export default function () {
  const userId = encoding.b64encode(randomBytes(16), 'rawurl');
  const response = http.get(`${base}/accounts/demo-0001/balance`, {
    headers: { user_id: userId, device_id: `load-device-${userId}` },
    tags: { name: 'GET /accounts/{accountNo}/balance' },
    timeout: '5s',
  });
  check(response, { 'balance is 200': (r) => r.status === 200 });
  sleep(0.1);
}
