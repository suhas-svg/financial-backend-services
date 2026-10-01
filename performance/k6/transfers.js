// Transfer load and soak scenario against the synthetic sandbox gateway.
//
//   k6 run performance/k6/transfers.js                      # load profile
//   PROFILE=soak DURATION=4h RATE=5 k6 run performance/k6/transfers.js
//
// Required env: BASE_URL (gateway, e.g. https://127.0.0.1:8443), SANDBOX_BOOTSTRAP_TOKEN,
// SANDBOX_OPERATOR_USERNAME, SANDBOX_OPERATOR_PASSWORD. Run the stack with
// docker-compose.synthetic-load.yml so the rapid-transfer step-up rule does not turn the
// test into a stream of 202 "authorization required" answers.
//
// Setup bootstraps the synthetic operator, enrolls TOTP and seeds one funded and one empty
// account. Every iteration moves 0.01 USD there and back (balances stay level for any
// duration) and reads balances and history, like a customer checking a transfer landed.
// Thresholds are the transfer SLOs in infrastructure/observability/prometheus/slo-rules.yml.
import http from "k6/http";
import crypto from "k6/crypto";
import { check, fail, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";
// Pinned jslib: renders the standard end-of-test summary alongside the JSON file.
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";

const BASE_URL = (__ENV.BASE_URL || "https://127.0.0.1:8443").replace(/\/$/, "");
const PROFILE = __ENV.PROFILE || "load";
const USERNAME = __ENV.SANDBOX_OPERATOR_USERNAME;
const PASSWORD = __ENV.SANDBOX_OPERATOR_PASSWORD;
const BOOTSTRAP_TOKEN = __ENV.SANDBOX_BOOTSTRAP_TOKEN;

const transferAuthorizationRequired = new Counter("transfer_authorization_required");

const profiles = {
  // Ramp to a sustained rate well above controlled-beta traffic, hold it, ramp down.
  load: {
    executor: "ramping-arrival-rate",
    startRate: 1,
    timeUnit: "1s",
    preAllocatedVUs: 20,
    maxVUs: 100,
    stages: [
      { duration: "1m", target: Number(__ENV.RATE || 10) },
      { duration: __ENV.DURATION || "8m", target: Number(__ENV.RATE || 10) },
      { duration: "1m", target: 0 }
    ]
  },
  // Moderate constant rate for hours: leaks, pool exhaustion, slow drift.
  soak: {
    executor: "constant-arrival-rate",
    rate: Number(__ENV.RATE || 3),
    timeUnit: "1s",
    duration: __ENV.DURATION || "4h",
    preAllocatedVUs: 10,
    maxVUs: 50
  }
};

export const options = {
  insecureSkipTLSVerify: true, // the sandbox gateway uses a self-signed certificate
  setupTimeout: "3m",
  scenarios: { transfers: profiles[PROFILE] },
  thresholds: {
    // Transfer SLOs: p99 <= 1 s, 99.9% without a 5xx.
    "http_req_duration{name:transfer}": ["p(99)<1000"],
    "server_errors{name:transfer}": ["rate<0.001"],
    // Everything else the scenario touches.
    "http_req_duration{name:read}": ["p(95)<500"],
    "server_errors{name:read}": ["rate<0.001"],
    checks: ["rate>0.999"],
    transfer_authorization_required: ["count==0"]
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"]
};

const serverErrors = new Rate("server_errors");

function base32Decode(input) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const clean = input.replace(/=+$/, "").toUpperCase();
  let bits = 0;
  let value = 0;
  const out = [];
  for (const char of clean) {
    value = (value << 5) | alphabet.indexOf(char);
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return new Uint8Array(out).buffer;
}

function totp(secret, step = Math.floor(Date.now() / 30000)) {
  const counter = new Uint8Array(8);
  let remaining = step;
  for (let i = 7; i >= 0; i--) {
    counter[i] = remaining & 0xff;
    remaining = Math.floor(remaining / 256);
  }
  const hex = crypto.hmac("sha1", base32Decode(secret), counter.buffer, "hex");
  const bytes = hex.match(/../g).map((pair) => parseInt(pair, 16));
  const offset = bytes[bytes.length - 1] & 0x0f;
  const code = ((bytes[offset] & 0x7f) << 24) | (bytes[offset + 1] << 16) | (bytes[offset + 2] << 8) | bytes[offset + 3];
  return String(code % 1000000).padStart(6, "0");
}

// A TOTP code is accepted once; wait for the next 30-second step before using another.
function nextTotp(secret, usedStep) {
  let step = Math.floor(Date.now() / 30000);
  while (step <= usedStep) {
    sleep(1);
    step = Math.floor(Date.now() / 30000);
  }
  return { code: totp(secret, step), step };
}

const json = (body) => JSON.stringify(body);
const jsonHeaders = (token, extra = {}) => ({
  headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...extra }
});

function expectOk(response, what) {
  if (response.status < 200 || response.status >= 300) {
    fail(`${what} failed: ${response.status} ${response.body}`);
  }
  return response.json();
}

function login() {
  const response = http.post(`${BASE_URL}/account-api/api/auth/login`, json({ username: USERNAME, password: PASSWORD }),
    { ...jsonHeaders(), tags: { name: "login" } });
  return expectOk(response, "login").accessToken;
}

export function setup() {
  if (!USERNAME || !PASSWORD || !BOOTSTRAP_TOKEN) {
    fail("SANDBOX_OPERATOR_USERNAME, SANDBOX_OPERATOR_PASSWORD and SANDBOX_BOOTSTRAP_TOKEN are required");
  }
  const status = expectOk(http.get(`${BASE_URL}/account-api/api/sandbox/bootstrap/status`), "bootstrap status");
  if (status.setupRequired) {
    expectOk(http.post(`${BASE_URL}/account-api/api/sandbox/bootstrap`, json({ username: USERNAME, password: PASSWORD }),
      jsonHeaders(null, { "X-Sandbox-Bootstrap-Token": BOOTSTRAP_TOKEN })), "operator bootstrap");
  }
  const token = login();

  const mfa = expectOk(http.get(`${BASE_URL}/account-api/api/security/mfa`, jsonHeaders(token)), "mfa status");
  if (mfa.enrolled) {
    fail("The operator already has TOTP enrolled; run against a fresh sandbox volume");
  }
  const enrollment = expectOk(http.post(`${BASE_URL}/account-api/api/security/mfa/totp/enroll`,
    json({ currentPassword: PASSWORD }), jsonHeaders(token)), "mfa enroll");
  let used = nextTotp(enrollment.secret, -1);
  expectOk(http.post(`${BASE_URL}/account-api/api/security/mfa/totp/confirm`, json({ code: used.code }),
    jsonHeaders(token)), "mfa confirm");

  const seedKey = `k6-seed-${Date.now()}`;
  const challenge = expectOk(http.post(`${BASE_URL}/transaction-api/api/sandbox/seed/challenge`, null,
    jsonHeaders(token, { "Idempotency-Key": seedKey })), "seed challenge");
  used = nextTotp(enrollment.secret, used.step);
  const proof = expectOk(http.post(`${BASE_URL}/account-api/api/security/challenges/${challenge.challengeId}/verify`,
    json({ credential: used.code }), jsonHeaders(token)), "challenge verify").proof;
  const seed = expectOk(http.post(`${BASE_URL}/transaction-api/api/sandbox/seed`,
    json({ challengeId: challenge.challengeId, proof }), jsonHeaders(token, { "Idempotency-Key": seedKey })), "seed");

  return { funded: String(seed.fundedAccountId), other: String(seed.zeroAccountId) };
}

let accessToken = null;

function authed(method, path, body, name, extraHeaders = {}) {
  if (!accessToken) accessToken = login();
  const send = () => http.request(method, `${BASE_URL}${path}`, body === null ? null : json(body),
    { ...jsonHeaders(accessToken, extraHeaders), tags: { name } });
  let response = send();
  if (response.status === 401) { // access token expired during a long soak
    accessToken = login();
    response = send();
  }
  serverErrors.add(response.status >= 500, { name });
  return response;
}

// Log the first few failures with enough detail to diagnose a failed drill from its log.
let loggedFailures = 0;
function explain(response, what) {
  if (loggedFailures >= 10) return;
  loggedFailures += 1;
  console.warn(`${what} failed: HTTP ${response.status} ${String(response.body).slice(0, 300)}`);
}

function transfer(from, to) {
  const response = authed("POST", "/transaction-api/api/transactions/transfer",
    { fromAccountId: from, toAccountId: to, amount: 0.01, currency: "USD", description: "k6 load", reference: "k6" },
    "transfer", { "Idempotency-Key": `k6-${__VU}-${__ITER}-${from}-${Date.now()}` });
  if (response.status === 202) transferAuthorizationRequired.add(1);
  const ok = check(response, { "transfer completed": (r) => r.status === 201 && r.json("status") === "COMPLETED" });
  if (!ok) explain(response, `transfer ${from} -> ${to}`);
}

export default function (accounts) {
  transfer(accounts.funded, accounts.other);
  transfer(accounts.other, accounts.funded);
  const balances = authed("GET", "/transaction-api/api/ledger/accounts", null, "read");
  if (!check(balances, { "balances readable": (r) => r.status === 200 })) explain(balances, "balance read");
  const history = authed("GET", "/transaction-api/api/transactions/user?page=0&size=10", null, "read");
  if (!check(history, { "history readable": (r) => r.status === 200 })) explain(history, "history read");
}

export function handleSummary(data) {
  const out = __ENV.SUMMARY_PATH || "k6-summary.json";
  return { [out]: JSON.stringify(data, null, 2), stdout: textSummary(data, { indent: " ", enableColors: false }) };
}
