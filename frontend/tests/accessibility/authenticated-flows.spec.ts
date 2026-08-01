import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

function token(roles: string[]) {
  const payload = Buffer.from(JSON.stringify({ sub: "synthetic-tester", roles, exp: 4102444800 }))
    .toString("base64url");
  return `header.${payload}.signature`;
}

async function mockApis(page: import("@playwright/test").Page, roles: string[]) {
  await page.route("**/account-api/**", route => {
    const url = route.request().url();
    const body = url.endsWith("/api/auth/login")
      ? { accessToken: token(roles) }
      : url.includes("/api/notifications/summary")
        ? { unread: 0, total: 0 }
        : { content: [], totalElements: 0 };
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.route("**/transaction-api/**", route => {
    const body = route.request().url().includes("/api/ledger/accounts")
      ? []
      : { content: [], totalElements: 0 };
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
}

async function signIn(page: import("@playwright/test").Page, admin: boolean) {
  await mockApis(page, admin ? ["ROLE_ADMIN"] : ["ROLE_USER"]);
  await page.goto("/login");
  if (admin) await page.getByRole("button", { name: "Admin operations" }).click();
  await page.getByLabel("Username").fill("synthetic-tester");
  await page.getByLabel("Password", { exact: true }).fill("synthetic-only-password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL(admin ? /\/admin$/ : /^https?:\/\/[^/]+\/$/);
}

for (const flow of [{ name: "customer dashboard", admin: false }, { name: "admin overview", admin: true }]) {
  test(`${flow.name} has keyboard focus support and no WCAG A/AA violations`, async ({ page }) => {
    await signIn(page, flow.admin);
    const main = page.locator("#main-content");
    await expect(main).toBeVisible();
    await expect(main).toBeFocused();

    await page.evaluate(() => {
      document.body.tabIndex = -1;
      document.body.focus();
      document.body.removeAttribute("tabindex");
    });
    await page.keyboard.press("Tab");
    await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(main).toBeFocused();

    const results = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();
    expect(results.violations.map(({ id, impact }) => ({ id, impact }))).toEqual([]);
  });
}
