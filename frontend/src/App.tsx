import { lazy, Suspense } from "react";
import { Route, Switch } from "wouter";
import { Navigate } from "./routing";
import { AdminLayout } from "./components/AdminLayout";
import { CustomerLayout } from "./components/CustomerLayout";
import { RequireAuth } from "./components/RequireAuth";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { TransactionsPage } from "./pages/TransactionsPage";

const AccountsPage = lazy(() => import("./pages/AccountsPage").then((module) => ({ default: module.AccountsPage })));
const AdminAccountsPage = lazy(() => import("./pages/AdminAccountsPage").then((module) => ({ default: module.AdminAccountsPage })));
const AdminAuditLogPage = lazy(() => import("./pages/AdminAuditLogPage").then((module) => ({ default: module.AdminAuditLogPage })));
const AdminInvestigationsPage = lazy(() => import("./pages/AdminInvestigationsPage").then((module) => ({ default: module.AdminInvestigationsPage })));
const AdminMonitoringPage = lazy(() => import("./pages/AdminMonitoringPage").then((module) => ({ default: module.AdminMonitoringPage })));
const AdminOverviewPage = lazy(() => import("./pages/AdminOverviewPage").then((module) => ({ default: module.AdminOverviewPage })));
const AdminReconciliationPage = lazy(() => import("./pages/AdminReconciliationPage").then((module) => ({ default: module.AdminReconciliationPage })));
const AdminDisputesPage = lazy(() => import("./pages/AdminDisputesPage").then((module) => ({ default: module.AdminDisputesPage })));
const AdminRiskAlertsPage = lazy(() => import("./pages/AdminRiskAlertsPage").then((module) => ({ default: module.AdminRiskAlertsPage })));
const AdminRiskCasesPage = lazy(() => import("./pages/AdminRiskCasesPage").then((module) => ({ default: module.AdminRiskCasesPage })));
const AdminTransactionsPage = lazy(() => import("./pages/AdminTransactionsPage").then((module) => ({ default: module.AdminTransactionsPage })));
const BeneficiariesPage = lazy(() => import("./pages/BeneficiariesPage").then((module) => ({ default: module.BeneficiariesPage })));
const DisputesPage = lazy(() => import("./pages/DisputesPage").then((module) => ({ default: module.DisputesPage })));
const MoveMoneyPage = lazy(() => import("./pages/MoveMoneyPage").then((module) => ({ default: module.MoveMoneyPage })));
const NotificationsPage = lazy(() => import("./pages/NotificationsPage").then((module) => ({ default: module.NotificationsPage })));
const OutcomeProtectionPage = lazy(() => import("./pages/OutcomeProtectionPage").then((module) => ({ default: module.OutcomeProtectionPage })));
const ScheduledTransfersPage = lazy(() => import("./pages/ScheduledTransfersPage").then((module) => ({ default: module.ScheduledTransfersPage })));
const SecurityPage = lazy(() => import("./pages/SecurityPage").then((module) => ({ default: module.SecurityPage })));
const StatementsPage = lazy(() => import("./pages/StatementsPage").then((module) => ({ default: module.StatementsPage })));

export function App() {
  const customerRoute = (page: React.ReactNode) => (
    <RequireAuth><CustomerLayout>{page}</CustomerLayout></RequireAuth>
  );
  const adminRoute = (page: React.ReactNode) => (
    <RequireAuth admin><AdminLayout>{page}</AdminLayout></RequireAuth>
  );

  return (
    <Suspense fallback={<main className="p-6 text-sm text-muted">Loading...</main>}>
      <Switch>
        <Route path="/login"><LoginPage /></Route>
        <Route path="/register"><RegisterPage /></Route>
        <Route path="/admin/accounts">{adminRoute(<AdminAccountsPage />)}</Route>
        <Route path="/admin/monitoring">{adminRoute(<AdminMonitoringPage />)}</Route>
        <Route path="/admin/transactions">{adminRoute(<AdminTransactionsPage />)}</Route>
        <Route path="/admin/audit-log">{adminRoute(<AdminAuditLogPage />)}</Route>
        <Route path="/admin/reconciliation">{adminRoute(<AdminReconciliationPage />)}</Route>
        <Route path="/admin/risk-alerts">{adminRoute(<AdminRiskAlertsPage />)}</Route>
        <Route path="/admin/risk-cases">{adminRoute(<AdminRiskCasesPage />)}</Route>
        <Route path="/admin/disputes">{adminRoute(<AdminDisputesPage />)}</Route>
        <Route path="/admin/investigations">{adminRoute(<AdminInvestigationsPage />)}</Route>
        <Route path="/admin">{adminRoute(<AdminOverviewPage />)}</Route>
        <Route path="/accounts">{customerRoute(<AccountsPage />)}</Route>
        <Route path="/beneficiaries">{customerRoute(<BeneficiariesPage />)}</Route>
        <Route path="/move-money">{customerRoute(<MoveMoneyPage />)}</Route>
        <Route path="/scheduled-transfers">{customerRoute(<ScheduledTransfersPage />)}</Route>
        <Route path="/outcome-protection">{customerRoute(<OutcomeProtectionPage />)}</Route>
        <Route path="/transactions">{customerRoute(<TransactionsPage />)}</Route>
        <Route path="/disputes">{customerRoute(<DisputesPage />)}</Route>
        <Route path="/statements">{customerRoute(<StatementsPage />)}</Route>
        <Route path="/notifications">{customerRoute(<NotificationsPage />)}</Route>
        <Route path="/security">{customerRoute(<SecurityPage />)}</Route>
        <Route path="/">{customerRoute(<DashboardPage />)}</Route>
        <Route><Navigate to="/" replace /></Route>
      </Switch>
    </Suspense>
  );
}
