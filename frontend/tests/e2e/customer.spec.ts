import { expect, test } from "@playwright/test";

const password = "password123";

function panel(page: import("@playwright/test").Page, name: string) {
  return page.locator("section").filter({ has: page.getByRole("heading", { name }) });
}

async function submitTransaction(
  page: import("@playwright/test").Page,
  button: import("@playwright/test").Locator,
  operation: "deposit" | "withdraw" | "transfer"
) {
  const responsePromise = page.waitForResponse((response) =>
    response.request().method() === "POST"
      && response.url().includes(`/transaction-api/api/transactions/${operation}`)
  );
  await button.click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
}

test("customer can register, login, create accounts, move money, and view transactions", async ({ page }) => {
  const username = `e2e_customer_${Date.now()}`;

  await page.goto("/register");
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole("button", { name: "Register" }).click();
  await expect(page.getByText(`Registered ${username}. You can sign in now.`)).toBeVisible();

  await page.getByRole("link", { name: "Sign in" }).click();
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByText(username)).toBeVisible();

  await page.getByRole("link", { name: "Accounts" }).click();
  await expect(page.getByRole("heading", { name: "Create account" })).toBeVisible();
  await page.getByLabel("Opening balance").fill("200");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("row").filter({ hasText: "$200.00" })).toBeVisible();

  await page.getByLabel("Type").selectOption("SAVINGS");
  await page.getByLabel("Opening balance").fill("50");
  await page.getByLabel("Interest rate").fill("1.25");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("row").filter({ hasText: "$50.00" })).toBeVisible();

  await page.getByRole("link", { name: "Move Money" }).click();
  const deposit = panel(page, "Deposit");
  await deposit.getByLabel("Account").selectOption({ index: 1 });
  await deposit.getByLabel("Amount").fill("25");
  await deposit.getByLabel("Reference").fill("e2e-deposit");
  await submitTransaction(page, deposit.getByRole("button", { name: "Deposit" }), "deposit");

  const withdraw = panel(page, "Withdraw");
  await withdraw.getByLabel("Account").selectOption({ index: 1 });
  await withdraw.getByLabel("Amount").fill("10");
  await withdraw.getByLabel("Reference").fill("e2e-withdraw");
  await submitTransaction(page, withdraw.getByRole("button", { name: "Withdraw" }), "withdraw");

  const transfer = panel(page, "Transfer");
  await transfer.getByLabel("From account").selectOption({ index: 1 });
  await transfer.getByLabel("To account").selectOption({ index: 2 });
  await transfer.getByLabel("Amount").fill("15");
  await transfer.getByLabel("Reference").fill("e2e-transfer");
  await submitTransaction(page, transfer.getByRole("button", { name: "Transfer" }), "transfer");

  await page.getByRole("link", { name: "Transactions" }).click();
  await expect(page.getByRole("heading", { name: "Transaction history" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "DEPOSIT" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "WITHDRAWAL" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "TRANSFER" })).toBeVisible();

  await page.getByRole("link", { name: "Dashboard" }).click();
  await expect(page.getByText("Available balance")).toBeVisible();
  await expect(page.getByText("Success rate")).toBeVisible();
});
