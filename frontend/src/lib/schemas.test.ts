import { describe, expect, it } from "vitest";
import { accountSchema, moneyMovementSchema, reversalSchema } from "./schemas";

describe("form schemas", () => {
  it("validates backend money movement limits and currencies", () => {
    expect(moneyMovementSchema.safeParse({ accountId: "1", amount: 0, currency: "USD" }).success).toBe(false);
    expect(moneyMovementSchema.safeParse({ accountId: "1", amount: 1000000, currency: "USD" }).success).toBe(false);
    expect(moneyMovementSchema.safeParse({ accountId: "1", amount: 10, currency: "INR" }).success).toBe(false);
    expect(moneyMovementSchema.safeParse({ accountId: "1", amount: 10, currency: "EUR" }).success).toBe(true);
  });

  it("requires credit card fields only for credit accounts", () => {
    expect(accountSchema.safeParse({ accountType: "CREDIT", balance: 0, creditLimit: 5000 }).success).toBe(false);
    expect(
      accountSchema.safeParse({
        accountType: "CREDIT",
        balance: 0,
        creditLimit: 5000,
        dueDate: "2099-01-01"
      }).success
    ).toBe(true);
  });

  it("requires reversal reason", () => {
    expect(reversalSchema.safeParse({ reason: "", reference: "" }).success).toBe(false);
    expect(reversalSchema.safeParse({ reason: "Customer request", reference: "" }).success).toBe(true);
  });
});
