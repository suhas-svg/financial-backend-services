import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/sandbox-e2e",
  timeout: 120_000,
  expect: { timeout: 30_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.SANDBOX_BASE_URL ?? "https://127.0.0.1:8443",
    ignoreHTTPSErrors: true,
    trace: "retain-on-failure"
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }]
});
