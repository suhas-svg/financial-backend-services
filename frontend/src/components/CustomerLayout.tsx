import { useEffect, useMemo, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Banknote, Bell, CalendarClock, ChevronLeft, ChevronRight, CircleHelp, Command, FileText, Gauge, Landmark, LockKeyhole, LogOut, Menu, Moon, Plus, Search, Sun, Users, WalletCards, X } from "lucide-react";
import clsx from "clsx";
import { Button } from "./ui";
import { useAuth } from "../state/useAuth";
import { getNotificationSummary } from "../lib/queries";

const navItems = [
  { to: "/", label: "Dashboard", keywords: "overview home balance", icon: Gauge, end: true },
  { to: "/accounts", label: "Accounts", keywords: "balances cards", icon: WalletCards },
  { to: "/beneficiaries", label: "Recipients", keywords: "beneficiaries payees", icon: Users },
  { to: "/move-money", label: "Move Money", keywords: "transfer deposit withdraw", icon: ArrowLeftRight },
  { to: "/scheduled-transfers", label: "Scheduled", keywords: "recurring future", icon: CalendarClock },
  { to: "/transactions", label: "Transactions", keywords: "activity history", icon: Banknote },
  { to: "/disputes", label: "Disputes", keywords: "claims help", icon: CircleHelp },
  { to: "/statements", label: "Statements", keywords: "documents export", icon: FileText },
  { to: "/notifications", label: "Notifications", keywords: "inbox alerts", icon: Bell },
  { to: "/security", label: "Security", keywords: "mfa limits password", icon: LockKeyhole }
];

function NavigationLink({ item, collapsed, unread, onNavigate }: { item: typeof navItems[number]; collapsed?: boolean; unread: number; onNavigate?: () => void }) {
  const Icon = item.icon;
  return <NavLink to={item.to} end={item.end} onClick={onNavigate} title={collapsed ? item.label : undefined} className={({ isActive }) => clsx("customer-nav-link", isActive && "customer-nav-link-active", collapsed && "justify-center px-0")}>
    <Icon className="h-[18px] w-[18px] shrink-0" />
    {collapsed ? <span className="sr-only">{item.label}</span> : <span>{item.label}</span>}
    {item.to === "/notifications" && unread > 0 ? <span className={clsx("customer-unread-badge", collapsed ? "absolute right-1 top-1" : "ml-auto")} aria-label={`${unread} unread notifications`}>{unread > 99 ? "99+" : unread}</span> : null}
  </NavLink>;
}

export function CustomerLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const searchRef = useRef<HTMLInputElement>(null);
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem("customer-nav-collapsed") === "true");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [darkMode, setDarkMode] = useState(() => {
    const stored = localStorage.getItem("customer-theme");
    return stored ? stored === "dark" : window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
  });
  const summary = useQuery({ queryKey: ["notification-summary"], queryFn: getNotificationSummary });
  const unread = summary.data?.unread ?? 0;
  const results = useMemo(() => {
    const term = search.trim().toLowerCase();
    return term ? navItems.filter((item) => `${item.label} ${item.keywords}`.toLowerCase().includes(term)).slice(0, 5) : [];
  }, [search]);

  useEffect(() => { setDrawerOpen(false); setSearch(""); }, [location.pathname]);
  useEffect(() => { localStorage.setItem("customer-nav-collapsed", String(collapsed)); }, [collapsed]);
  useEffect(() => {
    document.documentElement.classList.toggle("dark", darkMode);
    localStorage.setItem("customer-theme", darkMode ? "dark" : "light");
    return () => document.documentElement.classList.remove("dark");
  }, [darkMode]);
  useEffect(() => {
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === "Escape") { setDrawerOpen(false); setSearch(""); }
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") { event.preventDefault(); searchRef.current?.focus(); }
    };
    window.addEventListener("keydown", keyboard);
    return () => window.removeEventListener("keydown", keyboard);
  }, []);

  const goTo = (to: string) => { setSearch(""); navigate(to); };
  const initials = session?.username?.slice(0, 2).toUpperCase() || "FC";

  return <div className="customer-console min-h-screen bg-[#f4f7f9] text-ink transition-colors dark:bg-[#07110f] dark:text-slate-100">
    <aside className={clsx("customer-sidebar fixed inset-y-0 left-0 z-30 hidden border-r border-emerald-950/10 bg-[#0b2924] p-3 text-white lg:flex lg:flex-col", collapsed ? "w-20" : "w-64")}>
      <Link to="/" className={clsx("flex h-16 items-center gap-3 rounded-2xl px-2 font-semibold", collapsed && "justify-center")} aria-label="Financial Console">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-emerald-300 text-emerald-950 shadow-lg shadow-black/10"><Landmark className="h-5 w-5" /></span>
        {!collapsed ? <span><span className="block tracking-tight">Financial Console</span><span className="block text-xs font-normal text-emerald-100/60">Personal banking</span></span> : null}
      </Link>
      <nav className="mt-4 grid flex-1 content-start gap-1" aria-label="Customer navigation">{navItems.map((item) => <NavigationLink key={item.to} item={item} collapsed={collapsed} unread={unread} />)}</nav>
      <button type="button" className="customer-nav-link" onClick={() => setCollapsed((value) => !value)} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}>{collapsed ? <ChevronRight className="h-4 w-4" /> : <><ChevronLeft className="h-4 w-4" /><span>Collapse</span></>}</button>
    </aside>
    {drawerOpen ? <button className="fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm lg:hidden" aria-label="Close navigation" onClick={() => setDrawerOpen(false)} /> : null}
    <aside className={clsx("fixed inset-y-0 left-0 z-50 flex w-[min(88vw,320px)] flex-col bg-[#0b2924] p-4 text-white shadow-2xl transition-transform lg:hidden", drawerOpen ? "translate-x-0" : "-translate-x-full")} aria-label="Mobile customer navigation" aria-hidden={!drawerOpen}>
      <div className="mb-5 flex items-center justify-between"><Link to="/" className="flex items-center gap-3 font-semibold"><Landmark className="h-5 w-5 text-emerald-300" />Financial Console</Link><button className="rounded-xl p-2 hover:bg-white/10" onClick={() => setDrawerOpen(false)} aria-label="Close menu"><X className="h-5 w-5" /></button></div>
      <nav className="grid gap-1" aria-label="Mobile customer links">{navItems.map((item) => <NavigationLink key={item.to} item={item} unread={unread} onNavigate={() => setDrawerOpen(false)} />)}</nav>
    </aside>
    <div className={clsx("transition-[padding] duration-200", collapsed ? "lg:pl-20" : "lg:pl-64")}>
      <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-emerald-950/10 bg-white/90 px-4 backdrop-blur-xl dark:border-white/10 dark:bg-[#0a1714]/90 sm:px-6">
        <button type="button" className="customer-icon-button lg:hidden" onClick={() => setDrawerOpen(true)} aria-label="Open navigation"><Menu className="h-5 w-5" /></button>
        <div className="relative min-w-0 flex-1 sm:max-w-xl"><Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input ref={searchRef} className="customer-search" placeholder="Search your console" aria-label="Search customer console" value={search} onChange={(event) => setSearch(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter" && results[0]) goTo(results[0].to); }} /><span className="pointer-events-none absolute right-3 top-1/2 hidden -translate-y-1/2 items-center gap-1 rounded-md border border-line px-1.5 py-0.5 text-[10px] text-muted sm:flex"><Command className="h-3 w-3" />K</span>{results.length ? <div className="absolute left-0 right-0 top-12 overflow-hidden rounded-2xl border border-line bg-white p-1.5 shadow-2xl dark:border-slate-700 dark:bg-slate-900" aria-label="Customer search results">{results.map((item) => <button key={item.to} className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm hover:bg-emerald-50 dark:hover:bg-slate-800" onClick={() => goTo(item.to)}><item.icon className="h-4 w-4 text-brand" />{item.label}</button>)}</div> : null}</div>
        <Button className="hidden sm:inline-flex" onClick={() => navigate("/move-money")}><Plus className="h-4 w-4" />Move money</Button>
        <button type="button" className="customer-icon-button relative" onClick={() => navigate("/notifications")} aria-label={`Notifications, ${unread} unread`}><Bell className="h-5 w-5" />{unread ? <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-rose-500 ring-2 ring-white dark:ring-slate-900" /> : null}</button>
        <button type="button" className="customer-icon-button" onClick={() => setDarkMode((value) => !value)} aria-label={darkMode ? "Use light mode" : "Use dark mode"}>{darkMode ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}</button>
        <div className="hidden items-center gap-3 border-l border-line pl-4 md:flex dark:border-slate-700"><span className="grid h-9 w-9 place-items-center rounded-full bg-emerald-100 text-xs font-bold text-emerald-900 dark:bg-emerald-900 dark:text-emerald-100">{initials}</span><span><span className="block max-w-32 truncate text-sm font-semibold">{session?.username}</span><span className="block text-xs text-muted">Customer</span><span className="sr-only">{session?.roles.join(", ") || "ROLE_USER"}</span></span></div>
        <Button variant="ghost" onClick={logout} aria-label="Logout"><LogOut className="h-4 w-4" /><span className="hidden xl:inline">Logout</span></Button>
      </header>
      <main className="customer-page mx-auto max-w-[1500px] p-4 sm:p-6 lg:p-8"><Outlet /></main>
    </div>
  </div>;
}
