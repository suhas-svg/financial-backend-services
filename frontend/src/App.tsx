import { Navigate, Route, Routes } from "react-router-dom";
import { AdminLayout } from "./components/AdminLayout";
import { CustomerLayout } from "./components/CustomerLayout";
import { RequireAuth } from "./components/RequireAuth";
import { AccountsPage } from "./pages/AccountsPage";
import { AdminAccountsPage } from "./pages/AdminAccountsPage";
import { AdminAuditLogPage } from "./pages/AdminAuditLogPage";
import { AdminInvestigationsPage } from "./pages/AdminInvestigationsPage";
import { AdminMonitoringPage } from "./pages/AdminMonitoringPage";
import { AdminOverviewPage } from "./pages/AdminOverviewPage";
import { AdminReconciliationPage } from "./pages/AdminReconciliationPage";
import { AdminDisputesPage } from "./pages/AdminDisputesPage";
import { AdminRiskAlertsPage } from "./pages/AdminRiskAlertsPage";
import { AdminRiskCasesPage } from "./pages/AdminRiskCasesPage";
import { AdminTransactionsPage } from "./pages/AdminTransactionsPage";
import { DisputesPage } from "./pages/DisputesPage";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { MoveMoneyPage } from "./pages/MoveMoneyPage";
import { NotificationsPage } from "./pages/NotificationsPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ScheduledTransfersPage } from "./pages/ScheduledTransfersPage";
import { StatementsPage } from "./pages/StatementsPage";
import { TransactionsPage } from "./pages/TransactionsPage";

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<CustomerLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="accounts" element={<AccountsPage />} />
          <Route path="move-money" element={<MoveMoneyPage />} />
          <Route path="scheduled-transfers" element={<ScheduledTransfersPage />} />
          <Route path="transactions" element={<TransactionsPage />} />
          <Route path="disputes" element={<DisputesPage />} />
          <Route path="statements" element={<StatementsPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
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
  );
}
