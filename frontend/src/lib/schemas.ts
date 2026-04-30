import { z } from "zod";

const amount = z.coerce.number().min(0.01, "Amount must be greater than 0").max(999999.99, "Amount exceeds maximum limit");
const currency = z.enum(["USD", "EUR", "GBP"]);

export const loginSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z.string().min(1, "Password is required")
});

export const registerSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z.string().min(6, "Password must be at least 6 characters")
});

export const accountSchema = z
  .object({
    accountType: z.enum(["CHECKING", "SAVINGS", "CREDIT"]),
    balance: z.coerce.number().min(0, "Balance must be zero or positive"),
    ownerId: z.string().optional(),
    interestRate: z.coerce.number().min(0).optional(),
    creditLimit: z.coerce.number().min(0).optional(),
    dueDate: z.string().optional()
  })
  .superRefine((value, ctx) => {
    if (value.accountType === "CREDIT") {
      if (value.creditLimit === undefined || Number.isNaN(value.creditLimit)) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["creditLimit"], message: "Credit limit is required" });
      }
      if (!value.dueDate) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["dueDate"], message: "Due date is required" });
      }
    }
  });

export const moneyMovementSchema = z.object({
  accountId: z.string().min(1, "Account is required"),
  amount,
  description: z.string().max(500).optional(),
  reference: z.string().max(100).optional(),
  currency
});

export const transferSchema = z.object({
  fromAccountId: z.string().min(1, "Source account is required"),
  toAccountId: z.string().min(1, "Destination account is required"),
  amount,
  description: z.string().max(500).optional(),
  reference: z.string().max(100).optional(),
  currency
});

export const reversalSchema = z.object({
  reason: z.string().min(1, "Reason is required").max(500),
  reference: z.string().max(100).optional()
});

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;
export type AccountValues = z.infer<typeof accountSchema>;
export type MoneyMovementValues = z.infer<typeof moneyMovementSchema>;
export type TransferValues = z.infer<typeof transferSchema>;
export type ReversalValues = z.infer<typeof reversalSchema>;
