import { describe, expect, it } from "vitest";
import { accountSchema, beneficiarySchema, moneyMovementSchema, reversalSchema, scheduledTransferSchema } from "./schemas";

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

  it("validates scheduled transfer recurrence rules and accounts", () => {
    const base = {
      fromAccountId: "101",
      toAccountId: "202",
      amount: 50,
      currency: "USD",
      scheduleType: "RECURRING",
      firstRunAt: "2026-07-15T10:00"
    };

    expect(scheduledTransferSchema.safeParse(base).success).toBe(false);
    expect(scheduledTransferSchema.safeParse({ ...base, frequency: "MONTHLY" }).success).toBe(true);
    expect(scheduledTransferSchema.safeParse({ ...base, scheduleType: "ONE_TIME", frequency: "MONTHLY" }).success).toBe(false);
    expect(scheduledTransferSchema.safeParse({ ...base, fromAccountId: "101", toAccountId: "101", frequency: "MONTHLY" }).success).toBe(false);
    expect(scheduledTransferSchema.safeParse({ ...base, frequency: "MONTHLY", firstRunAt: "" }).success).toBe(false);
  });

  it("validates beneficiary display and destination fields", () => {
    expect(beneficiarySchema.safeParse({ displayName: "", destinationAccountId: "200", currency: "USD" }).success).toBe(false);
    expect(beneficiarySchema.safeParse({ displayName: "Rent", destinationAccountId: "", currency: "USD" }).success).toBe(false);
    expect(beneficiarySchema.safeParse({ displayName: "Rent", destinationAccountId: "200", currency: "INR" }).success).toBe(false);
    expect(beneficiarySchema.safeParse({ displayName: "Rent", destinationAccountId: "200", currency: "USD", nickname: "Home" }).success).toBe(true);
  });
});
