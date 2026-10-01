# Separate Customer And Admin UI Shells Design

## Summary

Split the authenticated frontend experience into two visibly different shells inside the existing React/Vite app:

- Customer routes keep the personal banking **Financial Console** experience.
- Admin routes use a separate **Operations Console** shell for back-office work.

Both shells continue to use the same backend auth endpoints, JWT session handling, API clients, and deployment build.

## Goals

- Make customer and admin areas feel clearly different.
- Keep the implementation small enough to verify in the current frontend.
- Preserve existing backend authorization and route protection.
- Avoid creating a second frontend app or deployment pipeline in this version.

## Non-Goals

- No separate admin backend or separate login system.
- No new roles beyond the existing `ROLE_USER` and `ROLE_ADMIN`.
- No redesign of every individual admin page in this slice.
- No customer/admin subdomain split.

## Current State

`App.tsx` routes both customer and admin pages through the same `AppLayout`. Admin links are conditionally shown when the JWT contains `ROLE_ADMIN`, but the visual shell, header, sidebar style, and page framing are the same.

## Proposed Architecture

Keep one frontend app and introduce two layout components:

- `CustomerLayout`
  - Used for `/`, `/accounts`, `/move-money`, and `/transactions`.
  - Shows only customer navigation.
  - Keeps the **Financial Console** brand.

- `AdminLayout`
  - Used for `/admin/*`.
  - Shows only operations navigation.
  - Uses the **Operations Console** brand.
  - Adds an admin-oriented header with role context.

Shared code remains shared:

- Auth provider and session storage.
- `RequireAuth` and `RequireAuth admin`.
- API clients and TanStack Query setup.
- Common UI primitives such as buttons, inputs, badges, panels, and tables.

## Route Shape

```tsx
<Route element={<RequireAuth />}>
  <Route element={<CustomerLayout />}>
    <Route index element={<DashboardPage />} />
    <Route path="accounts" element={<AccountsPage />} />
    <Route path="move-money" element={<MoveMoneyPage />} />
    <Route path="transactions" element={<TransactionsPage />} />
  </Route>

  <Route element={<RequireAuth admin />}>
    <Route path="admin" element={<AdminLayout />}>
      <Route index element={<AdminOverviewPage />} />
      <Route path="accounts" element={<AdminAccountsPage />} />
      <Route path="monitoring" element={<AdminMonitoringPage />} />
      <Route path="transactions" element={<AdminTransactionsPage />} />
      <Route path="audit-log" element={<AdminAuditLogPage />} />
      <Route path="risk-alerts" element={<AdminRiskAlertsPage />} />
      <Route path="risk-cases" element={<AdminRiskCasesPage />} />
      <Route path="investigations" element={<AdminInvestigationsPage />} />
    </Route>
  </Route>
</Route>
```

## Customer Experience

The customer shell should stay quiet and task-focused:

- Brand: **Financial Console**.
- Navigation: Dashboard, Accounts, Move Money, Transactions.
- Header: username, role text, logout.
- Page framing: personal account and money movement context.

Admin-only navigation must not appear in the customer sidebar.

## Admin Experience

The admin shell should feel like a back-office operating console:

- Brand: **Operations Console**.
- Navigation: Overview, Accounts, Monitoring, Transactions, Audit Log, Risk Alerts, Risk Cases, Investigations.
- Header: admin username, role badge, operational context, logout.
- Page framing: denser layout, operational labels, status-oriented language.

Customer-only actions such as Move Money must not appear in the admin sidebar.

## Admin Overview Page

Add a lightweight `/admin` landing page so admins do not land directly in customer dashboard context.

The first version should summarize links to:

- Account oversight.
- Monitoring.
- Transaction operations.
- Audit and investigation workflows.
- Risk alerts and cases.

This page can use existing data later. In the first slice, it can be a navigational overview with concise operational sections.

## Error Handling And Access

- Unauthenticated users still redirect to `/login`.
- Non-admin users who visit `/admin/*` still redirect to `/`.
- Backend remains authoritative for admin-only API access.
- Frontend route protection remains a usability guard, not a security boundary.

## Testing

Update frontend tests to cover:

- `ROLE_USER` sees the Financial Console customer shell.
- `ROLE_USER` does not see Operations Console navigation.
- `ROLE_ADMIN` can access `/admin`.
- `/admin` renders the Operations Console shell and overview page.
- Admin sidebar contains admin navigation only.
- Customer sidebar contains customer navigation only.

Run:

```powershell
cd frontend
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

## Implementation Notes

- Start by extracting the existing `AppLayout` customer behavior into `CustomerLayout`.
- Build `AdminLayout` as a separate component instead of overloading one layout with many conditionals.
- Keep shared navigation link styling helpers small and local unless duplication becomes meaningful.
- Avoid redesigning individual admin pages until the shell split is stable.
