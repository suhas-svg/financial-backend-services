import type { Account, Limits, Page, Transaction, TransactionStats } from "../types";
import { apiRequest, toQuery } from "./api";
import type { AccountValues, LoginValues, MoneyMovementValues, RegisterValues, ReversalValues, TransferValues } from "./schemas";
import { getSession } from "./session";

export function login(values: LoginValues) {
  return apiRequest<{ token?: string; accessToken?: string }>("account", "/api/auth/login", { method: "POST", body: values });
}

export function register(values: RegisterValues) {
  return apiRequest<{ username: string; roles: string[] }>("account", "/api/auth/register", { method: "POST", body: values });
}

export function listAccounts(params: { ownerId?: string; accountType?: string; page?: number; size?: number } = {}) {
  return apiRequest<Page<Account>>("account", `/api/accounts${toQuery({ size: 20, ...params })}`);
}

export function createAccount(values: AccountValues) {
  const session = getSession();
  const payload: Record<string, unknown> = {
    accountType: values.accountType,
    balance: values.balance,
    ownerId: values.ownerId || session?.username
  };
  if (values.accountType === "SAVINGS") {
    payload.interestRate = values.interestRate ?? 0;
  }
  if (values.accountType === "CREDIT") {
    payload.creditLimit = values.creditLimit;
    payload.dueDate = values.dueDate;
  }
  return apiRequest<Account>("account", "/api/accounts", { method: "POST", body: payload });
}

export function updateAccount(id: number, values: AccountValues) {
  return apiRequest<Account>("account", `/api/accounts/${id}`, { method: "PUT", body: values });
}

export function deleteAccount(id: number) {
  return apiRequest<void>("account", `/api/accounts/${id}`, { method: "DELETE" });
}

export function getTransactions(page = 0) {
  return apiRequest<Page<Transaction>>("transaction", `/api/transactions${toQuery({ page, size: 20, sort: "createdAt,desc" })}`);
}

export function searchTransactions(params: Record<string, string | number | undefined>) {
  return apiRequest<Page<Transaction>>("transaction", `/api/transactions/search${toQuery({ size: 20, sort: "createdAt,desc", ...params })}`);
}

export function getTransaction(transactionId: string) {
  return apiRequest<Transaction>("transaction", `/api/transactions/${transactionId}`);
}

export function getUserStats() {
  return apiRequest<TransactionStats>("transaction", "/api/transactions/user/stats");
}

export function getAccountStats(accountId: number | string) {
  return apiRequest<TransactionStats>("transaction", `/api/transactions/account/${accountId}/stats`);
}

export function getLimits() {
  return apiRequest<Limits>("transaction", "/api/transactions/limits");
}

export function deposit(values: MoneyMovementValues, idempotencyKey: string) {
  return apiRequest<Transaction>("transaction", "/api/transactions/deposit", { method: "POST", body: values, idempotencyKey });
}

export function withdraw(values: MoneyMovementValues, idempotencyKey: string) {
  return apiRequest<Transaction>("transaction", "/api/transactions/withdraw", { method: "POST", body: values, idempotencyKey });
}

export function transfer(values: TransferValues, idempotencyKey: string) {
  return apiRequest<Transaction>("transaction", "/api/transactions/transfer", { method: "POST", body: values, idempotencyKey });
}

export function reverseTransaction(transactionId: string, values: ReversalValues, idempotencyKey: string) {
  return apiRequest<Transaction>("transaction", `/api/transactions/${transactionId}/reverse`, {
    method: "POST",
    body: values,
    idempotencyKey
  });
}

export function getReversalStatus(transactionId: string) {
  return apiRequest<{ transactionId: string; isReversed: boolean }>("transaction", `/api/transactions/${transactionId}/reversed`);
}

export function getReversals(transactionId: string) {
  return apiRequest<Transaction[]>("transaction", `/api/transactions/${transactionId}/reversals`);
}

export function getAccountHealth() {
  return apiRequest<Record<string, unknown>>("account", "/api/health/status");
}

export function getAccountMetrics() {
  return apiRequest<Record<string, unknown>>("account", "/api/health/metrics");
}

export function triggerAccountHealthCheck() {
  return apiRequest<Record<string, unknown>>("account", "/api/health/check", { method: "POST" });
}

export function getDeploymentInfo() {
  return apiRequest<Record<string, unknown>>("account", "/api/health/deployment");
}

export function getDetailedHealth() {
  return apiRequest<Record<string, unknown>>("transaction", "/api/monitoring/health/detailed");
}

export function getTransactionMonitoringStats() {
  return apiRequest<Record<string, unknown>>("transaction", "/api/monitoring/stats/transactions");
}

export function getSystemStats() {
  return apiRequest<Record<string, unknown>>("transaction", "/api/monitoring/stats/system");
}

export function getAlertStatus() {
  return apiRequest<Record<string, unknown>>("transaction", "/api/monitoring/alerts/status");
}

export function getAvailableMetrics() {
  return apiRequest<Record<string, unknown>>("transaction", "/api/monitoring/metrics/available");
}
