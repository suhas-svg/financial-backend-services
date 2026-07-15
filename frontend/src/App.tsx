import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
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
  return (
    <Suspense fallback={<main className="p-6 text-sm text-muted">Loading...</main>}>
      <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<CustomerLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="accounts" element={<AccountsPage />} />
          <Route path="beneficiaries" element={<BeneficiariesPage />} />
          <Route path="move-money" element={<MoveMoneyPage />} />
          <Route path="scheduled-transfers" element={<ScheduledTransfersPage />} />
          <Route path="outcome-protection" element={<OutcomeProtectionPage />} />
          <Route path="transactions" element={<TransactionsPage />} />
          <Route path="disputes" element={<DisputesPage />} />
          <Route path="statements" element={<StatementsPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="security" element={<SecurityPage />} />
        </Route>
        <Route element={<RequireAuth admin />}>
          <Route path="admin" element={<AdminLayout />}>
            <Route index element={<AdminOverviewPage />} />
            <Route path="accounts" element={<AdminAccountsPage />} />
            <Route path="monitoring" element={<AdminMonitoringPage />} />
            <Route path="transactions" element={<AdminTransactionsPage />} />
            <Route path="audit-log" element={<AdminAuditLogPage />} />
            <Route path="reconciliation" element={<AdminReconciliationPage />} />
            <Route path="risk-alerts" element={<AdminRiskAlertsPage />} />
            <Route path="risk-cases" element={<AdminRiskCasesPage />} />
            <Route path="disputes" element={<AdminDisputesPage />} />
            <Route path="investigations" element={<AdminInvestigationsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
