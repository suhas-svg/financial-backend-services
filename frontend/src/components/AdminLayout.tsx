import { useEffect, useMemo, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { Activity, Bell, ChevronLeft, ChevronRight, CircleHelp, ClipboardList, FolderKanban, Gauge, Landmark, LogOut, Menu, Moon, RefreshCw, Search, Shield, ShieldAlert, Sun, X } from "lucide-react";
import clsx from "clsx";
import { Button } from "./ui";
import { useAuth } from "../state/useAuth";

const adminItems = [
  { to: "/admin", label: "Overview", icon: Gauge, end: true },
  { to: "/admin/accounts", label: "Accounts", icon: Shield },
  { to: "/admin/monitoring", label: "Service health", icon: Activity },
  { to: "/admin/transactions", label: "Transactions", icon: Landmark },
  { to: "/admin/audit-log", label: "Audit Log", icon: ClipboardList },
  { to: "/admin/reconciliation", label: "Reconciliation", icon: RefreshCw },
  { to: "/admin/risk-alerts", label: "Risk Alerts", icon: ShieldAlert },
  { to: "/admin/risk-cases", label: "Risk Cases", icon: FolderKanban },
  { to: "/admin/disputes", label: "Disputes", icon: CircleHelp },
  { to: "/admin/investigations", label: "Investigations", icon: Search }
];

function NavigationLink({
  to,
  label,
  icon: Icon,
  end,
  collapsed = false,
  onNavigate
}: {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  end?: boolean;
  collapsed?: boolean;
  onNavigate?: () => void;
}) {
  return (
    <NavLink
      to={to}
      end={end}
      onClick={onNavigate}
      title={collapsed ? label : undefined}
      className={({ isActive }) =>
        clsx(
          "admin-nav-link",
          isActive && "admin-nav-link-active",
          collapsed && "justify-center px-0"
        )
      }
    >
      <Icon className="h-4 w-4" />
      {collapsed ? <span className="sr-only">{label}</span> : <span>{label}</span>}
    </NavLink>
  );
}

export function AdminLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [darkMode, setDarkMode] = useState(() => {
    if (typeof window === "undefined") return false;
    const stored = window.localStorage.getItem("operations-theme");
    return stored ? stored === "dark" : window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
  });
  const searchResults = useMemo(() => {
    const term = search.trim().toLowerCase();
    return term ? adminItems.filter((item) => item.label.toLowerCase().includes(term)).slice(0, 5) : [];
  }, [search]);

  useEffect(() => setDrawerOpen(false), [location.pathname]);
  useEffect(() => {
    document.documentElement.classList.toggle("dark", darkMode);
    window.localStorage.setItem("operations-theme", darkMode ? "dark" : "light");
    return () => document.documentElement.classList.remove("dark");
  }, [darkMode]);
  useEffect(() => {
    if (!drawerOpen) return;
    const close = (event: KeyboardEvent) => event.key === "Escape" && setDrawerOpen(false);
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [drawerOpen]);

  const goTo = (to: string) => {
    setSearch("");
    navigate(to);
  };

  return (
    <div className="admin-console min-h-screen bg-slate-50 text-ink transition-colors dark:bg-slate-950 dark:text-slate-100">
      <aside className={clsx("admin-sidebar fixed inset-y-0 left-0 z-30 hidden border-r border-slate-800 bg-slate-950 p-3 text-white lg:flex lg:flex-col", collapsed ? "w-20" : "w-64")}>
        <Link to="/admin" className={clsx("flex h-16 items-center gap-3 px-2 font-semibold", collapsed && "justify-center")} aria-label="Operations Console">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-cyan-400/10"><Shield className="h-5 w-5 text-cyan-300" /></span>
          {!collapsed ? <span><span className="block">Operations</span><span className="block text-xs font-normal text-slate-400">Command console</span></span> : null}
        </Link>
        <nav className="mt-4 grid flex-1 gap-1" aria-label="Admin navigation">
          {adminItems.map((item) => (
            <NavigationLink key={item.to} {...item} collapsed={collapsed} />
          ))}
        </nav>
        <button type="button" className="admin-nav-link" onClick={() => setCollapsed((value) => !value)} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}>
          {collapsed ? <ChevronRight className="h-4 w-4" /> : <><ChevronLeft className="h-4 w-4" /><span>Collapse</span></>}
        </button>
      </aside>

      {drawerOpen ? <button className="fixed inset-0 z-40 bg-slate-950/60 lg:hidden" aria-label="Close navigation" onClick={() => setDrawerOpen(false)} /> : null}
      {drawerOpen ? <aside className="fixed inset-y-0 left-0 z-50 flex w-[min(88vw,320px)] flex-col bg-slate-950 p-4 text-white shadow-2xl lg:hidden" aria-label="Mobile admin navigation">
        <div className="mb-5 flex items-center justify-between"><Link to="/admin" className="flex items-center gap-3 font-semibold"><Shield className="h-5 w-5 text-cyan-300" />Operations Console</Link><button className="rounded-lg p-2" onClick={() => setDrawerOpen(false)} aria-label="Close menu"><X className="h-5 w-5" /></button></div>
        <nav className="grid gap-1" aria-label="Mobile admin links">{adminItems.map((item) => <NavigationLink key={item.to} {...item} onNavigate={() => setDrawerOpen(false)} />)}</nav>
      </aside> : null}

      <div className={clsx("transition-[padding]", collapsed ? "lg:pl-20" : "lg:pl-64")}>
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-950/90 sm:px-6">
          <button type="button" className="rounded-lg border border-slate-200 p-2 lg:hidden" onClick={() => setDrawerOpen(true)} aria-label="Open navigation"><Menu className="h-5 w-5" /></button>
          <div className="relative min-w-0 flex-1 sm:max-w-xl">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm outline-none focus:border-cyan-600 focus:bg-white" placeholder="Search operational workflows" aria-label="Global operational search" value={search} onChange={(event) => setSearch(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter" && searchResults[0]) goTo(searchResults[0].to); }} />
            {searchResults.length ? <div className="absolute left-0 right-0 top-12 rounded-xl border border-slate-200 bg-white p-1 shadow-xl" aria-label="Operational search results">{searchResults.map((item) => <button key={item.to} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm hover:bg-slate-50" onClick={() => goTo(item.to)}><item.icon className="h-4 w-4" />{item.label}</button>)}</div> : null}
          </div>
          <Button variant="secondary" className="hidden sm:inline-flex" onClick={() => navigate("/admin/risk-alerts")}><Bell className="h-4 w-4" />Review alerts</Button>
          <Button variant="ghost" onClick={() => setDarkMode((value) => !value)} aria-label={darkMode ? "Use light mode" : "Use dark mode"} title={darkMode ? "Use light mode" : "Use dark mode"}>
            {darkMode ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>
          <div className="hidden border-l border-slate-200 pl-4 md:block"><p className="text-sm font-semibold">{session?.username}</p><p className="text-xs text-muted">Operations admin</p></div>
          <Button variant="ghost" onClick={logout} aria-label="Logout"><LogOut className="h-4 w-4" /><span className="hidden xl:inline">Logout</span></Button>
        </header>
        <main className="mx-auto max-w-[1600px] p-4 sm:p-6 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
