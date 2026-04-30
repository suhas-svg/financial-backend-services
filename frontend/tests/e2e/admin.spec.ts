import { expect, test } from "@playwright/test";

const adminUsername = process.env.E2E_ADMIN_USERNAME ?? "admin_phase2_212714";
const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? "password123";
const customerPassword = "password123";

async function signIn(page: import("@playwright/test").Page, username: string, password: string) {
  await page.goto("/login");
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

test("admin can open monitoring pages when logged in with admin credentials", async ({ page }) => {
  await signIn(page, adminUsername, adminPassword);

  await expect(page.getByText("Operations")).toBeVisible();
  await expect(page.getByRole("link", { name: "Admin Accounts" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Monitoring" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Ops Transactions" })).toBeVisible();

  await page.getByRole("link", { name: "Monitoring" }).click();
  await expect(page.getByRole("heading", { name: "Monitoring" })).toBeVisible();
  await expect(page.getByText("Account health")).toBeVisible();
  await expect(page.getByText("Transaction health")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Available metrics" })).toBeVisible();
});

test("normal user cannot see or open admin routes", async ({ page }) => {
  const username = `e2e_guard_${Date.now()}`;

  await page.goto("/register");
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(customerPassword);
  await page.getByRole("button", { name: "Register" }).click();
  await expect(page.getByText(`Registered ${username}. You can sign in now.`)).toBeVisible();

  await signIn(page, username, customerPassword);
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByText("Operations")).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Admin Accounts" })).toHaveCount(0);

  await page.goto("/admin/monitoring");
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Monitoring" })).toHaveCount(0);
});
