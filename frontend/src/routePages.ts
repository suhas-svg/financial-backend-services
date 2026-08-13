import { lazy, type ComponentType } from "react";

const loadAccountsPage = () => import("./pages/AccountsPage").then((module) => ({ default: module.AccountsPage }));
const loadAdminAccountsPage = () => import("./pages/AdminAccountsPage").then((module) => ({ default: module.AdminAccountsPage }));
const loadAdminAuditLogPage = () => import("./pages/AdminAuditLogPage").then((module) => ({ default: module.AdminAuditLogPage }));
const loadAdminInvestigationsPage = () => import("./pages/AdminInvestigationsPage").then((module) => ({ default: module.AdminInvestigationsPage }));
const loadAdminMonitoringPage = () => import("./pages/AdminMonitoringPage").then((module) => ({ default: module.AdminMonitoringPage }));
const loadAdminOverviewPage = () => import("./pages/AdminOverviewPage").then((module) => ({ default: module.AdminOverviewPage }));
const loadAdminReconciliationPage = () => import("./pages/AdminReconciliationPage").then((module) => ({ default: module.AdminReconciliationPage }));
const loadAdminDisputesPage = () => import("./pages/AdminDisputesPage").then((module) => ({ default: module.AdminDisputesPage }));
const loadAdminRiskAlertsPage = () => import("./pages/AdminRiskAlertsPage").then((module) => ({ default: module.AdminRiskAlertsPage }));
const loadAdminRiskCasesPage = () => import("./pages/AdminRiskCasesPage").then((module) => ({ default: module.AdminRiskCasesPage }));
const loadAdminTransactionsPage = () => import("./pages/AdminTransactionsPage").then((module) => ({ default: module.AdminTransactionsPage }));
const loadBeneficiariesPage = () => import("./pages/BeneficiariesPage").then((module) => ({ default: module.BeneficiariesPage }));
const loadDisputesPage = () => import("./pages/DisputesPage").then((module) => ({ default: module.DisputesPage }));
const loadMoveMoneyPage = () => import("./pages/MoveMoneyPage").then((module) => ({ default: module.MoveMoneyPage }));
const loadNotificationsPage = () => import("./pages/NotificationsPage").then((module) => ({ default: module.NotificationsPage }));
const loadOutcomeProtectionPage = () => import("./pages/OutcomeProtectionPage").then((module) => ({ default: module.OutcomeProtectionPage }));
const loadScheduledTransfersPage = () => import("./pages/ScheduledTransfersPage").then((module) => ({ default: module.ScheduledTransfersPage }));
const loadSecurityPage = () => import("./pages/SecurityPage").then((module) => ({ default: module.SecurityPage }));
const loadStatementsPage = () => import("./pages/StatementsPage").then((module) => ({ default: module.StatementsPage }));

export const AccountsPage = lazy(loadAccountsPage);
export const AdminAccountsPage = lazy(loadAdminAccountsPage);
export const AdminAuditLogPage = lazy(loadAdminAuditLogPage);
export const AdminInvestigationsPage = lazy(loadAdminInvestigationsPage);
export const AdminMonitoringPage = lazy(loadAdminMonitoringPage);
export const AdminOverviewPage = lazy(loadAdminOverviewPage);
export const AdminReconciliationPage = lazy(loadAdminReconciliationPage);
export const AdminDisputesPage = lazy(loadAdminDisputesPage);
export const AdminRiskAlertsPage = lazy(loadAdminRiskAlertsPage);
export const AdminRiskCasesPage = lazy(loadAdminRiskCasesPage);
export const AdminTransactionsPage = lazy(loadAdminTransactionsPage);
export const BeneficiariesPage = lazy(loadBeneficiariesPage);
export const DisputesPage = lazy(loadDisputesPage);
export const MoveMoneyPage = lazy(loadMoveMoneyPage);
export const NotificationsPage = lazy(loadNotificationsPage);
export const OutcomeProtectionPage = lazy(loadOutcomeProtectionPage);
export const ScheduledTransfersPage = lazy(loadScheduledTransfersPage);
export const SecurityPage = lazy(loadSecurityPage);
export const StatementsPage = lazy(loadStatementsPage);
type RouteLoader = () => Promise<{ default: ComponentType }>;

const customerRouteLoaders: Record<string, RouteLoader> = {
  "/accounts": loadAccountsPage,
  "/beneficiaries": loadBeneficiariesPage,
  "/move-money": loadMoveMoneyPage,
  "/scheduled-transfers": loadScheduledTransfersPage,
  "/outcome-protection": loadOutcomeProtectionPage,
  "/disputes": loadDisputesPage,
  "/statements": loadStatementsPage,
  "/notifications": loadNotificationsPage,
  "/security": loadSecurityPage
};

const adminRouteLoaders: Record<string, RouteLoader> = {
  "/admin": loadAdminOverviewPage,
  "/admin/accounts": loadAdminAccountsPage,
  "/admin/monitoring": loadAdminMonitoringPage,
  "/admin/transactions": loadAdminTransactionsPage,
  "/admin/audit-log": loadAdminAuditLogPage,
  "/admin/reconciliation": loadAdminReconciliationPage,
  "/admin/risk-alerts": loadAdminRiskAlertsPage,
  "/admin/risk-cases": loadAdminRiskCasesPage,
  "/admin/disputes": loadAdminDisputesPage,
  "/admin/investigations": loadAdminInvestigationsPage
};

function preload(loaders: Record<string, RouteLoader>, path?: string) {
  const selected = path ? [loaders[path]].filter(Boolean) : Object.values(loaders);
  return Promise.allSettled(selected.map((loader) => loader()));
}

export function preloadCustomerRoute(path?: string) {
  return preload(customerRouteLoaders, path);
}

export function preloadAdminRoute(path?: string) {
  return preload(adminRouteLoaders, path);
}
