import type { Account, LedgerAccountProjection } from "../types";

export function ledgerBalance(account: Account, projection?: LedgerAccountProjection) {
  return Number(projection?.postedBalance ?? account.ledgerBalance ?? account.balance);
}

export function availableBalance(account: Account, projection?: LedgerAccountProjection) {
  return Number(projection?.availableBalance ?? account.availableBalance ?? account.balance);
}

export function pendingBalance(projection?: LedgerAccountProjection) {
  return Number(projection?.pendingBalance ?? 0);
}

export function projectionMap(projections?: LedgerAccountProjection[]) {
  return new Map((projections ?? []).map((projection) => [projection.externalAccountId, projection]));
}

export function projectionFor(account: Account, projections?: Map<string, LedgerAccountProjection>) {
  return projections?.get(String(account.id));
}

export function canDebit(account: Account, amount = 0) {
  return account.status !== "FROZEN" && availableBalance(account) >= amount;
}
