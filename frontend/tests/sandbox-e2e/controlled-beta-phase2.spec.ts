import { createHmac } from "node:crypto";
import { expect, test } from "@playwright/test";

const username = process.env.SANDBOX_OPERATOR_USERNAME;
const password = process.env.SANDBOX_OPERATOR_PASSWORD;
const bootstrapToken = process.env.SANDBOX_BOOTSTRAP_TOKEN;

function decodeBase32(value: string) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const char of value.replace(/=+$/g, "").toUpperCase()) bits += alphabet.indexOf(char).toString(2).padStart(5, "0");
  const bytes = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(Number.parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(bytes);
}

function totp(secret: string, timestamp = Date.now()) {
  const counter = Math.floor(timestamp / 30_000);
  const buffer = Buffer.alloc(8);
  buffer.writeBigUInt64BE(BigInt(counter));
  const digest = createHmac("sha1", decodeBase32(secret)).update(buffer).digest();
  const offset = digest[digest.length - 1] & 0x0f;
  const code = ((digest[offset] & 0x7f) << 24) | ((digest[offset + 1] & 0xff) << 16)
    | ((digest[offset + 2] & 0xff) << 8) | (digest[offset + 3] & 0xff);
  return String(code % 1_000_000).padStart(6, "0");
}

test("gateway exposes only a hardened, unmistakably synthetic entry point", async ({ page, request }) => {
  const response = await page.goto("/");
  expect(response?.status()).toBe(200);
  await expect(page.locator("#synthetic-sandbox-banner")).toContainText("SYNTHETIC SANDBOX");
  expect(response?.headers()["x-frame-options"]).toBe("DENY");
  expect(response?.headers()["x-content-type-options"]).toBe("nosniff");
  expect(response?.headers()["referrer-policy"]).toBe("no-referrer");
  expect(response?.headers()["permissions-policy"]).toContain("payment=()");
  expect(response?.headers()["content-security-policy"]).toContain("frame-ancestors 'none'");
  expect(response?.headers()["x-environment-classification"]).toBe("SYNTHETIC_SANDBOX");

  for (const service of ["account-api", "transaction-api"]) {
    const metadata = await request.get(`/${service}/api/sandbox/metadata`);
    expect(metadata.ok()).toBeTruthy();
    expect(await metadata.json()).toMatchObject({ synthetic: true, realMoney: false });
  }
  const allowedOrigin = new URL(process.env.SANDBOX_BASE_URL ?? "https://127.0.0.1:8443").origin;
  const allowedBrowserRequest = await request.get("/account-api/api/sandbox/metadata", {
    headers: { Origin: allowedOrigin }
  });
  expect(allowedBrowserRequest.ok()).toBeTruthy();
  const wrongPortOrigin = allowedOrigin.endsWith(":65534")
    ? "https://127.0.0.1:65533"
    : "https://127.0.0.1:65534";
  const rejectedLoopbackPort = await request.get("/account-api/api/sandbox/metadata", {
    headers: { Origin: wrongPortOrigin }
  });
  expect(rejectedLoopbackPort.status()).toBe(403);
  const rejectedOrigin = await request.get("/account-api/api/sandbox/metadata", {
    headers: { Origin: "https://untrusted.example" }
  });
  expect(rejectedOrigin.status()).toBe(403);
});

test("one-time bootstrap, payload-safe reservation replay, zero closure, and recovery", async ({ page, request }) => {
  test.skip(!username || !password || !bootstrapToken, "Runtime-only operator inputs are required");
  const status = await request.get("/account-api/api/sandbox/bootstrap/status");
  expect(status.ok()).toBeTruthy();
  if ((await status.json()).setupRequired) {
    const created = await request.post("/account-api/api/sandbox/bootstrap", {
      headers: { "X-Sandbox-Bootstrap-Token": bootstrapToken! }, data: { username, password }
    });
    expect(created.status()).toBe(201);
  }
  const replay = await request.post("/account-api/api/sandbox/bootstrap", {
    headers: { "X-Sandbox-Bootstrap-Token": bootstrapToken! }, data: { username, password }
  });
  expect(replay.status()).toBe(409);

  const login = await request.post("/account-api/api/auth/login", { data: { username, password } });
  expect(login.ok()).toBeTruthy();
  const token = (await login.json()).accessToken as string;
  const headers = { Authorization: `Bearer ${token}` };

  const mfaStatus = await request.get("/account-api/api/security/mfa", { headers });
  let secret: string;
  if (!(await mfaStatus.json()).enrolled) {
    const enrollment = await request.post("/account-api/api/security/mfa/totp/enroll", {
      headers, data: { currentPassword: password }
    });
    secret = (await enrollment.json()).secret;
    const confirmation = await request.post("/account-api/api/security/mfa/totp/confirm", {
      headers, data: { code: totp(secret) }
    });
    expect(confirmation.ok()).toBeTruthy();
  } else {
    test.skip(true, "Fresh-volume test requires the TOTP secret created during this run");
    return;
  }

  const key = "phase2-e2e-seed-v1";
  const challenge = await request.post("/transaction-api/api/sandbox/seed/challenge", {
    headers: { ...headers, "Idempotency-Key": key }
  });
  expect(challenge.ok()).toBeTruthy();
  const challengeId = (await challenge.json()).challengeId as string;
  const verified = await request.post(`/account-api/api/security/challenges/${challengeId}/verify`, {
    headers, data: { credential: totp(secret) }
  });
  expect(verified.ok()).toBeTruthy();
  const proof = (await verified.json()).proof as string;
  const seedBody = { challengeId, proof };
  const firstSeed = await request.post("/transaction-api/api/sandbox/seed", {
    headers: { ...headers, "Idempotency-Key": key }, data: seedBody
  });
  expect(firstSeed.status()).toBe(201);
  const first = await firstSeed.json();
  const replaySeed = await request.post("/transaction-api/api/sandbox/seed", {
    headers: { ...headers, "Idempotency-Key": key }, data: seedBody
  });
  expect(replaySeed.status()).toBe(201);
  expect(await replaySeed.json()).toMatchObject({
    zeroAccountId: first.zeroAccountId,
    fundedAccountId: first.fundedAccountId,
    fundingTransactionId: first.fundingTransactionId,
    fundedAmount: 1000
  });

  const accountBefore = await request.get(`/account-api/api/accounts/${first.fundedAccountId}`, { headers });
  expect(accountBefore.ok()).toBeTruthy();
  const balanceBefore = Number((await accountBefore.json()).balance);
  const reservationKey = "spending-reservation-payload-safe-v1";
  const withdrawalBody = {
    accountId: String(first.fundedAccountId),
    amount: 25,
    currency: "USD",
    description: "Controlled beta reservation replay",
    reference: "reservation-payload-safe"
  };
  const firstWithdrawal = await request.post("/transaction-api/api/transactions/withdraw", {
    headers: { ...headers, "Idempotency-Key": reservationKey }, data: withdrawalBody
  });
  expect(firstWithdrawal.status()).toBe(201);
  const firstWithdrawalBody = await firstWithdrawal.json();

  const identicalReplay = await request.post("/transaction-api/api/transactions/withdraw", {
    headers: { ...headers, "Idempotency-Key": reservationKey }, data: withdrawalBody
  });
  expect(identicalReplay.status()).toBe(201);
  expect(await identicalReplay.json()).toMatchObject({
    transactionId: firstWithdrawalBody.transactionId,
    amount: 25,
    currency: "USD"
  });

  const mismatchedReplay = await request.post("/transaction-api/api/transactions/withdraw", {
    headers: { ...headers, "Idempotency-Key": reservationKey },
    data: { ...withdrawalBody, amount: 30 }
  });
  expect(mismatchedReplay.status()).toBe(409);
  expect(JSON.stringify(await mismatchedReplay.json())).toContain("different");

  await expect.poll(async () => {
    const account = await request.get(`/account-api/api/accounts/${first.fundedAccountId}`, { headers });
    expect(account.ok()).toBeTruthy();
    return Number((await account.json()).balance);
  }, { timeout: 45_000 }).toBe(balanceBefore - 25);

  const concurrentKey = "spending-reservation-concurrent-conflict-v1";
  const concurrentBalanceBefore = balanceBefore - 25;
  const [concurrentA, concurrentB] = await Promise.all([
    request.post("/transaction-api/api/transactions/withdraw", {
      headers: { ...headers, "Idempotency-Key": concurrentKey },
      data: { ...withdrawalBody, amount: 10 }
    }),
    request.post("/transaction-api/api/transactions/withdraw", {
      headers: { ...headers, "Idempotency-Key": concurrentKey },
      data: { ...withdrawalBody, amount: 11 }
    })
  ]);
  expect([concurrentA.status(), concurrentB.status()].sort((left, right) => left - right))
    .toEqual([201, 409]);
  const concurrentWinner = concurrentA.status() === 201 ? concurrentA : concurrentB;
  const concurrentWinnerBody = await concurrentWinner.json();
  expect([10, 11]).toContain(Number(concurrentWinnerBody.amount));
  await expect.poll(async () => {
    const account = await request.get(`/account-api/api/accounts/${first.fundedAccountId}`, { headers });
    expect(account.ok()).toBeTruthy();
    return Number((await account.json()).balance);
  }, { timeout: 45_000 }).toBe(concurrentBalanceBefore - Number(concurrentWinnerBody.amount));

  await page.goto("/login");
  await page.getByRole("button", { name: "Admin operations" }).click();
  await expect(page.getByRole("heading", { name: "Operations sign in" })).toBeVisible();
  await page.getByLabel("Username").fill(username!);
  await page.getByLabel("Password", { exact: true }).fill(password!);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL(/\/admin$/);
  await page
    .getByRole("navigation", { name: "Admin navigation" })
    .getByRole("link", { name: "Accounts" })
    .click();
  await page.waitForURL(/\/admin\/accounts$/);
  await page.getByRole("button", { name: `Close account ${first.zeroAccountId}` }).click();
  await expect(page.getByRole("heading", { name: `Close account #${first.zeroAccountId}` })).toBeVisible();
  await page.getByLabel("Action reason").fill("Phase 2 synthetic zero-account closure smoke");
  await page.getByRole("button", { name: "Confirm account closure" }).click();
  await expect(page.getByRole("button", { name: `Close account ${first.zeroAccountId}` })).toBeDisabled();

  const recovery = await request.post("/transaction-api/api/scheduled-transfers/admin/recover-stale?batchSize=10", { headers });
  expect(recovery.ok()).toBeTruthy();
  expect((await recovery.json()).processed).toBeGreaterThanOrEqual(0);
});
