import { Suspense } from "react";
import { Route, Switch } from "wouter";
import { Navigate } from "./routing";
import { AdminLayout } from "./components/AdminLayout";
import { CustomerLayout } from "./components/CustomerLayout";
import { RequireAuth } from "./components/RequireAuth";
import { RouteAccessibility } from "./components/RouteAccessibility";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { TransactionsPage } from "./pages/TransactionsPage";
import {
  AccountsPage,
  AdminAccountsPage,
  AdminAuditLogPage,
  AdminDisputesPage,
  AdminInvestigationsPage,
  AdminMonitoringPage,
  AdminOverviewPage,
  AdminReconciliationPage,
  AdminRiskAlertsPage,
  AdminRiskCasesPage,
  AdminTransactionsPage,
  BeneficiariesPage,
  DisputesPage,
  MoveMoneyPage,
  NotificationsPage,
  OutcomeProtectionPage,
  ScheduledTransfersPage,
  SecurityPage,
  StatementsPage
} from "./routePages";

export function App() {
  const customerRoute = (page: React.ReactNode) => (
    <RequireAuth><CustomerLayout><Suspense fallback={<RouteLoading />}>{page}</Suspense></CustomerLayout></RequireAuth>
  );
  const adminRoute = (page: React.ReactNode) => (
    <RequireAuth admin><AdminLayout><Suspense fallback={<RouteLoading />}>{page}</Suspense></AdminLayout></RequireAuth>
  );

  return (
    <>
    <RouteAccessibility />
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
    </>
  );
}

function RouteLoading() {
  return (
    <section className="grid min-h-48 content-center justify-items-center gap-3 border border-line bg-panel p-8 text-center" role="status" aria-live="polite">
      <span className="h-8 w-8 animate-pulse rounded-full bg-emerald-200 dark:bg-emerald-900" aria-hidden="true" />
      <span className="text-sm font-medium text-muted">Loading workspace…</span>
    </section>
  );
}
