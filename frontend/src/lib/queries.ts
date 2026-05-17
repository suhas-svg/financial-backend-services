import type { Account, AuditLogEntry, AuditSummary, InvestigationSummary, InvestigationTimelineItem, Limits, Page, RiskAlert, RiskCase, RiskCaseSummary, RiskSummary, Transaction, TransactionStats } from "../types";
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

export function searchAuditEvents(params: Record<string, string | number | undefined>) {
  return apiRequest<Page<AuditLogEntry>>("transaction", `/api/audit/events${toQuery({ size: 20, sort: "createdAt,desc", ...params })}`);
}

export function getAuditEvent(eventId: string) {
  return apiRequest<AuditLogEntry>("transaction", `/api/audit/events/${eventId}`);
}

export function getAuditSummary(params: Record<string, string | undefined> = {}) {
  return apiRequest<AuditSummary>("transaction", `/api/audit/summary${toQuery(params)}`);
}

export function searchRiskAlerts(params: Record<string, string | number | undefined>) {
  return apiRequest<Page<RiskAlert>>("transaction", `/api/risk/alerts${toQuery({ size: 20, sort: "createdAt,desc", ...params })}`);
}

export function getRiskAlert(alertId: string) {
  return apiRequest<RiskAlert>("transaction", `/api/risk/alerts/${alertId}`);
}

export function getRiskSummary(params: Record<string, string | undefined> = {}) {
  return apiRequest<RiskSummary>("transaction", `/api/risk/summary${toQuery(params)}`);
}

export function updateRiskAlertStatus(alertId: string, values: { status: string; resolutionNote?: string }) {
  return apiRequest<RiskAlert>("transaction", `/api/risk/alerts/${alertId}/status`, { method: "PATCH", body: values });
}

export function searchRiskCases(params: Record<string, string | number | undefined>) {
  return apiRequest<Page<RiskCase>>("transaction", `/api/risk/cases${toQuery({ size: 20, sort: "createdAt,desc", ...params })}`);
}

export function getRiskCase(caseId: string) {
  return apiRequest<RiskCase>("transaction", `/api/risk/cases/${caseId}`);
}

export function getRiskCaseSummary(params: Record<string, string | undefined> = {}) {
  return apiRequest<RiskCaseSummary>("transaction", `/api/risk/cases/summary${toQuery(params)}`);
}

export function createRiskCaseFromAlert(alertId: string, values: { title?: string; priority?: string; reason?: string }) {
  return apiRequest<RiskCase>("transaction", `/api/risk/cases/from-alert/${alertId}`, { method: "POST", body: values });
}

export function claimRiskCase(caseId: string) {
  return apiRequest<RiskCase>("transaction", `/api/risk/cases/${caseId}/claim`, { method: "PATCH" });
}

export function updateRiskCaseStatus(caseId: string, values: { status: string; resolutionNote?: string }) {
  return apiRequest<RiskCase>("transaction", `/api/risk/cases/${caseId}/status`, { method: "PATCH", body: values });
}

export function addRiskCaseNote(caseId: string, values: { note: string }) {
  return apiRequest<RiskCase>("transaction", `/api/risk/cases/${caseId}/notes`, { method: "POST", body: values });
}

export function getInvestigationTimeline(params: Record<string, string | number | undefined>) {
  return apiRequest<Page<InvestigationTimelineItem>>("transaction", `/api/investigations/timeline${toQuery({ size: 50, sort: "createdAt,desc", ...params })}`);
}

export function getInvestigationSummary(params: Record<string, string | undefined> = {}) {
  return apiRequest<InvestigationSummary>("transaction", `/api/investigations/summary${toQuery(params)}`);
}

export function exportInvestigationTimelineCsv(params: Record<string, string | undefined> = {}) {
  return apiRequest<string>("transaction", `/api/investigations/export${toQuery(params)}`);
}
