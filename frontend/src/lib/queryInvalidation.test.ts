import { describe, expect, it, vi } from "vitest";
import type { QueryClient } from "@tanstack/react-query";
import { invalidateInBackground, invalidateMoneyMovementQueries } from "./queryInvalidation";

describe("invalidateInBackground", () => {
  it("does not keep a mutation pending on the projection refresh promise", () => {
    const invalidateQueries = vi.fn(() => Promise.resolve());
    const queryClient = { invalidateQueries } as unknown as QueryClient;

    expect(invalidateInBackground(queryClient, ["accounts"])).toBeUndefined();
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["accounts"] });
  });
});

describe("invalidateMoneyMovementQueries", () => {
  it("refreshes authoritative, mirrored, and activity views together", () => {
    const invalidateQueries = vi.fn((options: { queryKey: unknown[] }) => {
      void options;
      return Promise.resolve();
    });
    const queryClient = { invalidateQueries } as unknown as QueryClient;

    invalidateMoneyMovementQueries(queryClient);

    expect(invalidateQueries.mock.calls.map(([options]) => options)).toEqual([
      { queryKey: ["accounts"] },
      { queryKey: ["ledger", "accounts"] },
      { queryKey: ["ledger-accounts"] },
      { queryKey: ["admin-accounts"] },
      { queryKey: ["dashboard"] },
      { queryKey: ["transactions"] },
      { queryKey: ["stats"] },
      { queryKey: ["notification-summary"] },
      { queryKey: ["statements"] }
    ]);
  });
});
