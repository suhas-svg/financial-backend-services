import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/AppLayout";
import { RequireAuth } from "./components/RequireAuth";
import { AccountsPage } from "./pages/AccountsPage";
import { AdminAccountsPage } from "./pages/AdminAccountsPage";
import { AdminMonitoringPage } from "./pages/AdminMonitoringPage";
import { AdminTransactionsPage } from "./pages/AdminTransactionsPage";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { MoveMoneyPage } from "./pages/MoveMoneyPage";
import { RegisterPage } from "./pages/RegisterPage";
import { TransactionsPage } from "./pages/TransactionsPage";

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="accounts" element={<AccountsPage />} />
          <Route path="move-money" element={<MoveMoneyPage />} />
          <Route path="transactions" element={<TransactionsPage />} />
          <Route element={<RequireAuth admin />}>
            <Route path="admin/accounts" element={<AdminAccountsPage />} />
            <Route path="admin/monitoring" element={<AdminMonitoringPage />} />
            <Route path="admin/transactions" element={<AdminTransactionsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
