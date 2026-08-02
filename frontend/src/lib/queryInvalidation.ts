import type { QueryClient, QueryKey } from "@tanstack/react-query";

export const MONEY_STATE_REFRESH_INTERVAL_MS = 2000;

/**
 * Refreshes projections in the background without extending a mutation's
 * pending lifecycle. The mutation response is already the authoritative
 * result; query refetches must not make a completed action look pending.
 */
export function invalidateInBackground(queryClient: QueryClient, queryKey: QueryKey): void {
  void queryClient.invalidateQueries({ queryKey });
}

/**
 * Refresh every customer/operator view that can display the result of a
 * completed money movement. Ledger projections remain authoritative; the
 * account-service values are a deliberately eventual operator mirror.
 */
export function invalidateMoneyMovementQueries(queryClient: QueryClient): void {
  [
    ["accounts"],
    ["ledger", "accounts"],
    ["ledger-accounts"],
    ["admin-accounts"],
    ["dashboard"],
    ["transactions"],
    ["stats"],
    ["notification-summary"],
    ["statements"]
  ].forEach((queryKey) => invalidateInBackground(queryClient, queryKey));
}
