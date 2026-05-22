# Separate Customer And Admin UI Shells Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build visibly separate customer and admin UI shells inside the existing React app while keeping one auth flow and one deployment.

**Architecture:** Extract the current authenticated layout into a customer-focused shell, add a separate admin operations shell for `/admin/*`, and route customer/admin pages through separate layout trees. Shared auth, API clients, UI primitives, and backend role enforcement remain unchanged.

**Tech Stack:** React 18, React Router, TypeScript, Tailwind CSS, lucide-react, Vitest, Testing Library.

---

## File Structure

- Create `frontend/src/components/CustomerLayout.tsx`
  - Owns customer navigation, Financial Console branding, customer header, and `<Outlet />`.
- Create `frontend/src/components/AdminLayout.tsx`
  - Owns operations navigation, Operations Console branding, admin header, and `<Outlet />`.
- Create `frontend/src/pages/AdminOverviewPage.tsx`
  - Landing page for `/admin`, using existing UI primitives and links to admin workflows.
- Modify `frontend/src/App.tsx`
  - Replace the single `AppLayout` route tree with separate `CustomerLayout` and `AdminLayout` route trees.
- Modify `frontend/src/test/app.component.test.tsx`
  - Update existing admin navigation tests and add role-specific shell tests.
- Optionally delete `frontend/src/components/AppLayout.tsx`
  - Remove after both new layouts compile and no imports reference it.

## Task 1: Customer Shell Extraction

**Files:**
- Create: `frontend/src/components/CustomerLayout.tsx`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/test/app.component.test.tsx`

- [ ] **Step 1: Write failing customer shell assertions**

In `frontend/src/test/app.component.test.tsx`, update the admin navigation test block so the customer case asserts the customer shell brand and customer-only nav:

```tsx
describe("role-specific shells", () => {
  it("renders the customer shell for ROLE_USER users", async () => {
    mockFetch();
    renderApp("/", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByRole("link", { name: "Financial Console" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Accounts" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Move Money" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Transactions" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Operations Console" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Admin Accounts" })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test and verify it still uses the old layout**

Run:

```powershell
cd frontend
npm.cmd test -- src/test/app.component.test.tsx
```

Expected: this may pass for the customer brand but the later admin shell tests in Task 2 will fail until `AdminLayout` exists.

- [ ] **Step 3: Create `CustomerLayout` from current `AppLayout` customer behavior**

Create `frontend/src/components/CustomerLayout.tsx`:

```tsx
import { Link, NavLink, Outlet } from "react-router-dom";
import { ArrowLeftRight, Banknote, Gauge, Landmark, LogOut, WalletCards } from "lucide-react";
import clsx from "clsx";
import { Button } from "./ui";
import { useAuth } from "../state/useAuth";

const customerNavItems = [
  { to: "/", label: "Dashboard", icon: Gauge },
  { to: "/accounts", label: "Accounts", icon: WalletCards },
  { to: "/move-money", label: "Move Money", icon: ArrowLeftRight },
  { to: "/transactions", label: "Transactions", icon: Banknote }
];

function CustomerNavLink({ to, label, icon: Icon }: { to: string; label: string; icon: React.ComponentType<{ className?: string }> }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        clsx(
          "flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium",
          isActive ? "bg-teal-50 text-brand" : "text-slate-600 hover:bg-slate-100 hover:text-ink"
        )
      }
    >
      <Icon className="h-4 w-4" />
      <span>{label}</span>
    </NavLink>
  );
}

export function CustomerLayout() {
  const { session, logout } = useAuth();

  return (
    <div className="min-h-screen bg-slate-100 text-ink">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-line bg-white p-4 lg:block">
        <Link to="/" className="flex h-12 items-center gap-3 text-lg font-semibold">
          <Landmark className="h-5 w-5 text-brand" />
          Financial Console
        </Link>
        <nav className="mt-6 grid gap-1">
          {customerNavItems.map((item) => (
            <CustomerNavLink key={item.to} {...item} />
          ))}
        </nav>
      </aside>
      <div className="lg:pl-64">
        <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b border-line bg-white px-4">
          <div>
            <p className="text-sm font-semibold">{session?.username}</p>
            <p className="text-xs text-muted">{session?.roles.join(", ") || "ROLE_USER"}</p>
          </div>
          <Button variant="ghost" onClick={logout}>
            <LogOut className="h-4 w-4" />
            Logout
          </Button>
        </header>
        <main className="mx-auto max-w-7xl p-4 lg:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Update `App.tsx` to use `CustomerLayout` for customer routes**

In `frontend/src/App.tsx`, replace the `AppLayout` import with `CustomerLayout`:

```tsx
import { CustomerLayout } from "./components/CustomerLayout";
```

Then use it for the customer route group:

```tsx
<Route element={<RequireAuth />}>
  <Route element={<CustomerLayout />}>
    <Route index element={<DashboardPage />} />
    <Route path="accounts" element={<AccountsPage />} />
    <Route path="move-money" element={<MoveMoneyPage />} />
    <Route path="transactions" element={<TransactionsPage />} />
    <Route element={<RequireAuth admin />}>
      <Route path="admin/accounts" element={<AdminAccountsPage />} />
      <Route path="admin/monitoring" element={<AdminMonitoringPage />} />
      <Route path="admin/transactions" element={<AdminTransactionsPage />} />
      <Route path="admin/audit-log" element={<AdminAuditLogPage />} />
      <Route path="admin/risk-alerts" element={<AdminRiskAlertsPage />} />
      <Route path="admin/risk-cases" element={<AdminRiskCasesPage />} />
      <Route path="admin/investigations" element={<AdminInvestigationsPage />} />
    </Route>
  </Route>
</Route>
```

This intermediate state compiles while Task 2 creates the admin shell.

- [ ] **Step 5: Run the focused frontend test**

Run:

```powershell
cd frontend
npm.cmd test -- src/test/app.component.test.tsx
```

Expected: customer layout assertions pass; old admin route tests still pass through the temporary nested admin route.

- [ ] **Step 6: Commit customer shell extraction**

```powershell
git add frontend/src/components/CustomerLayout.tsx frontend/src/App.tsx frontend/src/test/app.component.test.tsx
git commit -m "Extract customer frontend shell"
```

## Task 2: Admin Shell And Overview Route

**Files:**
- Create: `frontend/src/components/AdminLayout.tsx`
- Create: `frontend/src/pages/AdminOverviewPage.tsx`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/test/app.component.test.tsx`

- [ ] **Step 1: Add failing admin shell tests**

In `frontend/src/test/app.component.test.tsx`, extend `describe("role-specific shells", ...)`:

```tsx
it("renders the operations shell for ROLE_ADMIN users on admin routes", async () => {
  mockFetch();
  renderApp("/admin", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

  expect(await screen.findByRole("link", { name: "Operations Console" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Operations overview" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Overview" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Admin Accounts" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Monitoring" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Ops Transactions" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Audit Log" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Risk Alerts" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Risk Cases" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Investigations" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Move Money" })).not.toBeInTheDocument();
});
```

Update the non-admin redirect test to use `/admin`:

```tsx
it("redirects non-admin users away from admin routes", async () => {
  mockFetch();
  renderApp("/admin", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

  expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Operations overview" })).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run test and verify admin shell is missing**

Run:

```powershell
cd frontend
npm.cmd test -- src/test/app.component.test.tsx
```

Expected: FAIL because `/admin` currently redirects to `/` or does not render `Operations Console`.

- [ ] **Step 3: Create `AdminLayout`**

Create `frontend/src/components/AdminLayout.tsx`:

```tsx
import { Link, NavLink, Outlet } from "react-router-dom";
import { Activity, ClipboardList, FolderKanban, Gauge, Landmark, LogOut, Search, Shield, ShieldAlert } from "lucide-react";
import clsx from "clsx";
import { Badge, Button } from "./ui";
import { useAuth } from "../state/useAuth";

const adminNavItems = [
  { to: "/admin", label: "Overview", icon: Gauge },
  { to: "/admin/accounts", label: "Admin Accounts", icon: Shield },
  { to: "/admin/monitoring", label: "Monitoring", icon: Activity },
  { to: "/admin/transactions", label: "Ops Transactions", icon: Landmark },
  { to: "/admin/audit-log", label: "Audit Log", icon: ClipboardList },
  { to: "/admin/risk-alerts", label: "Risk Alerts", icon: ShieldAlert },
  { to: "/admin/risk-cases", label: "Risk Cases", icon: FolderKanban },
  { to: "/admin/investigations", label: "Investigations", icon: Search }
];

function AdminNavLink({ to, label, icon: Icon }: { to: string; label: string; icon: React.ComponentType<{ className?: string }> }) {
  return (
    <NavLink
      to={to}
      end={to === "/admin"}
      className={({ isActive }) =>
        clsx(
          "flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium",
          isActive ? "bg-slate-900 text-white" : "text-slate-300 hover:bg-slate-800 hover:text-white"
        )
      }
    >
      <Icon className="h-4 w-4" />
      <span>{label}</span>
    </NavLink>
  );
}

export function AdminLayout() {
  const { session, logout } = useAuth();

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <aside className="fixed inset-y-0 left-0 hidden w-72 border-r border-slate-800 bg-slate-950 p-4 lg:block">
        <Link to="/admin" className="flex h-12 items-center gap-3 text-lg font-semibold">
          <Shield className="h-5 w-5 text-teal-300" />
          Operations Console
        </Link>
        <p className="mt-2 px-1 text-xs text-slate-400">Back-office risk, audit, and service operations.</p>
        <nav className="mt-6 grid gap-1">
          {adminNavItems.map((item) => (
            <AdminNavLink key={item.to} {...item} />
          ))}
        </nav>
      </aside>
      <div className="lg:pl-72">
        <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b border-slate-800 bg-slate-900 px-4">
          <div>
            <p className="text-sm font-semibold text-white">{session?.username}</p>
            <div className="mt-1 flex items-center gap-2">
              <Badge tone="info">ROLE_ADMIN</Badge>
              <span className="text-xs text-slate-400">Operational access</span>
            </div>
          </div>
          <Button variant="secondary" onClick={logout} className="border-slate-700 bg-slate-950 text-slate-100 hover:bg-slate-800">
            <LogOut className="h-4 w-4" />
            Logout
          </Button>
        </header>
        <main className="mx-auto max-w-7xl p-4 lg:p-6">
          <div className="rounded-md bg-slate-100 p-4 text-ink lg:p-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Create `AdminOverviewPage`**

Create `frontend/src/pages/AdminOverviewPage.tsx`:

```tsx
import { Link } from "react-router-dom";
import { Activity, ClipboardList, FolderKanban, Landmark, Search, Shield, ShieldAlert } from "lucide-react";
import { Panel } from "../components/ui";

const overviewItems = [
  { to: "/admin/accounts", title: "Account oversight", detail: "Review owners, balances, account status, and freeze controls.", icon: Shield },
  { to: "/admin/monitoring", title: "Monitoring", detail: "Check service health, deployment state, alerts, and metrics.", icon: Activity },
  { to: "/admin/transactions", title: "Transaction operations", detail: "Search operational transaction records and reversal context.", icon: Landmark },
  { to: "/admin/audit-log", title: "Audit log", detail: "Inspect high-value transaction and security audit events.", icon: ClipboardList },
  { to: "/admin/risk-alerts", title: "Risk alerts", detail: "Review, dismiss, or escalate generated risk alerts.", icon: ShieldAlert },
  { to: "/admin/risk-cases", title: "Risk cases", detail: "Claim cases, update status, and add internal notes.", icon: FolderKanban },
  { to: "/admin/investigations", title: "Investigations", detail: "Reconstruct timelines across transaction, audit, risk, and case records.", icon: Search }
];

export function AdminOverviewPage() {
  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Operations overview</h1>
        <p className="text-sm text-muted">Back-office entry point for account control, monitoring, audit, risk, and investigation workflows.</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {overviewItems.map(({ to, title, detail, icon: Icon }) => (
          <Link key={to} to={to} className="group block">
            <Panel className="h-full transition group-hover:border-slate-400" title={title}>
              <div className="flex gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-900 text-white">
                  <Icon className="h-4 w-4" />
                </div>
                <p className="text-sm text-muted">{detail}</p>
              </div>
            </Panel>
          </Link>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Update `App.tsx` route tree**

Final `frontend/src/App.tsx` should import both layouts and the overview page:

```tsx
import { AdminLayout } from "./components/AdminLayout";
import { CustomerLayout } from "./components/CustomerLayout";
import { AdminOverviewPage } from "./pages/AdminOverviewPage";
```

Then structure routes:

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

- [ ] **Step 6: Run focused test and verify pass**

Run:

```powershell
cd frontend
npm.cmd test -- src/test/app.component.test.tsx
```

Expected: PASS for role-specific shell tests and existing admin page tests.

- [ ] **Step 7: Commit admin shell**

```powershell
git add frontend/src/components/AdminLayout.tsx frontend/src/pages/AdminOverviewPage.tsx frontend/src/App.tsx frontend/src/test/app.component.test.tsx
git commit -m "Add separate admin operations shell"
```

## Task 3: Remove Old Shared Layout And Run Full Frontend Verification

**Files:**
- Delete: `frontend/src/components/AppLayout.tsx`
- Modify: any imports if still present
- Test: frontend test/lint/build commands

- [ ] **Step 1: Confirm `AppLayout` is unused**

Run:

```powershell
rg -n "AppLayout" frontend/src
```

Expected: no matches except the file itself if it still exists.

- [ ] **Step 2: Delete old layout file**

Delete `frontend/src/components/AppLayout.tsx`.

- [ ] **Step 3: Run TypeScript/build-oriented checks**

Run:

```powershell
cd frontend
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Expected:

- Tests pass.
- Lint passes.
- Build passes. A Vite chunk-size warning is acceptable if it matches the existing warning and does not fail the command.

- [ ] **Step 4: Inspect git status**

Run:

```powershell
git status -sb
```

Expected: only intentional frontend shell changes are present.

- [ ] **Step 5: Commit cleanup and verification**

```powershell
git add frontend/src/components/AppLayout.tsx frontend/src/components/CustomerLayout.tsx frontend/src/components/AdminLayout.tsx frontend/src/pages/AdminOverviewPage.tsx frontend/src/App.tsx frontend/src/test/app.component.test.tsx
git commit -m "Verify separate customer and admin shells"
```

## Task 4: Manual Browser Smoke Check

**Files:**
- No source files expected unless a defect is found.

- [ ] **Step 1: Start local frontend if needed**

Run:

```powershell
cd frontend
npm.cmd run dev -- --host 127.0.0.1
```

Expected: Vite serves on `http://127.0.0.1:5173`.

- [ ] **Step 2: Check customer shell manually**

Open `http://127.0.0.1:5173/` while logged in as a `ROLE_USER`.

Expected visible state:

- Sidebar brand says `Financial Console`.
- Customer nav shows Dashboard, Accounts, Move Money, Transactions.
- No `Operations Console` brand.
- No admin navigation.

- [ ] **Step 3: Check admin shell manually**

Open `http://127.0.0.1:5173/admin` while logged in as a `ROLE_ADMIN`.

Expected visible state:

- Sidebar brand says `Operations Console`.
- Overview page heading says `Operations overview`.
- Admin nav shows Overview, Admin Accounts, Monitoring, Ops Transactions, Audit Log, Risk Alerts, Risk Cases, Investigations.
- No `Move Money` navigation in the admin sidebar.

- [ ] **Step 4: Final status**

Run:

```powershell
git status -sb
```

Expected: clean tracked working tree after commits, except ignored build artifacts if local dev server/build was run.
