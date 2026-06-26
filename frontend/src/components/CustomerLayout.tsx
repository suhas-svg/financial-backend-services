import { Link, NavLink, Outlet } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Banknote, Bell, CircleHelp, FileText, Gauge, Landmark, LogOut, WalletCards } from "lucide-react";
import clsx from "clsx";
import { Button } from "./ui";
import { useAuth } from "../state/useAuth";
import { getNotificationSummary } from "../lib/queries";

const navItems = [
  { to: "/", label: "Dashboard", icon: Gauge },
  { to: "/accounts", label: "Accounts", icon: WalletCards },
  { to: "/move-money", label: "Move Money", icon: ArrowLeftRight },
  { to: "/transactions", label: "Transactions", icon: Banknote },
  { to: "/disputes", label: "Disputes", icon: CircleHelp },
  { to: "/statements", label: "Statements", icon: FileText },
  { to: "/notifications", label: "Notifications", icon: Bell }
];

function NavigationLink({ to, label, icon: Icon, badge }: { to: string; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }) {
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
      {badge ? <span aria-hidden="true" className="ml-auto rounded-full bg-danger px-2 py-0.5 text-xs font-semibold text-white">{badge}</span> : null}
    </NavLink>
  );
}

export function CustomerLayout() {
  const { session, logout } = useAuth();
  const summary = useQuery({ queryKey: ["notification-summary"], queryFn: getNotificationSummary });
  const unread = summary.data?.unread ?? 0;

  return (
    <div className="min-h-screen bg-slate-100 text-ink">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-line bg-white p-4 lg:block">
        <Link to="/" className="flex h-12 items-center gap-3 text-lg font-semibold">
          <Landmark className="h-5 w-5 text-brand" />
          Financial Console
        </Link>
        <nav className="mt-6 grid gap-1">
          {navItems.map((item) => (
            <NavigationLink key={item.to} {...item} badge={item.to === "/notifications" ? unread : undefined} />
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
