import type { Account } from "../types";

export function ledgerBalance(account: Account) {
  return Number(account.ledgerBalance ?? account.balance);
}

export function availableBalance(account: Account) {
  return Number(account.availableBalance ?? account.balance);
}

export function canDebit(account: Account, amount = 0) {
  return account.status !== "FROZEN" && availableBalance(account) >= amount;
}
