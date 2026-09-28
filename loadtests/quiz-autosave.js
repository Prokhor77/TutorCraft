// AC-10 / NFR-PERF-02: 500 виртуальных студентов проходят тест из 30 вопросов
// с автосохранением каждые 10 с в течение 30 минут. Запуск:
//   k6 run -e BASE_URL=http://localhost:3000 -e ITEM_ID=<quizItemId> -e PASSWORD=<SEED_DEMO_PASSWORD> loadtests/quiz-autosave.js
// Пользователи: loadstudent{N}@demo.local (создайте CSV-импортом в админке: email,firstName,lastName,courseShortName).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE = `${__ENV.BASE_URL || 'http://localhost:3000'}/api/v1`;
const ITEM_ID = __ENV.ITEM_ID;
const QUESTIONS = 30;
const AUTOSAVE_INTERVAL_SEC = 10;
const TEST_DURATION = '30m';
const VUS = 500;

export const options = {
  scenarios: { quiz: { executor: 'constant-vus', vus: VUS, duration: TEST_DURATION } },
  thresholds: {
    'http_req_duration{kind:autosave}': ['p(95)<400'],
    http_req_failed: ['rate<0.001'],
  },
};

function login(vu) {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ email: `loadstudent${vu}@demo.local`, password: __ENV.PASSWORD, tenantSlug: 'demo' }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login 200': (r) => r.status === 200 });
  return res.json('accessToken');
}

export default function () {
  const token = login(__VU);
  const auth = { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
  const attempt = http.post(`${BASE}/items/${ITEM_ID}/attempts`, null, auth).json();
  for (let i = 0; i < QUESTIONS; i++) {
    const question = attempt.questions[i % attempt.questions.length];
    const response = question.options ? { optionId: question.options[0].id } : { text: 'ответ' };
    const res = http.put(`${BASE}/attempts/${attempt.id}/answers/${question.slot}`, JSON.stringify({ response }),
      Object.assign({ tags: { kind: 'autosave' } }, auth));
    check(res, { 'saved': (r) => r.status === 200 });
    sleep(AUTOSAVE_INTERVAL_SEC);
  }
  const finish = http.post(`${BASE}/attempts/${attempt.id}/finish`, null,
    { headers: Object.assign({ 'Idempotency-Key': uuidv4() }, auth.headers) });
  check(finish, { 'finished': (r) => r.status === 200 });
}
