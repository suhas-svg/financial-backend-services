import { Link, NavLink, Outlet } from "react-router-dom";
import { Activity, CircleHelp, ClipboardList, FolderKanban, Gauge, Landmark, LogOut, Search, Shield, ShieldAlert } from "lucide-react";
import clsx from "clsx";
import { Button } from "./ui";
import { useAuth } from "../state/useAuth";

const adminItems = [
  { to: "/admin", label: "Overview", icon: Gauge, end: true },
  { to: "/admin/accounts", label: "Admin Accounts", icon: Shield },
  { to: "/admin/monitoring", label: "Monitoring", icon: Activity },
  { to: "/admin/transactions", label: "Ops Transactions", icon: Landmark },
  { to: "/admin/audit-log", label: "Audit Log", icon: ClipboardList },
  { to: "/admin/risk-alerts", label: "Risk Alerts", icon: ShieldAlert },
  { to: "/admin/risk-cases", label: "Risk Cases", icon: FolderKanban },
  { to: "/admin/disputes", label: "Disputes", icon: CircleHelp },
  { to: "/admin/investigations", label: "Investigations", icon: Search }
];

function NavigationLink({
  to,
  label,
  icon: Icon,
  end
}: {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  end?: boolean;
}) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        clsx(
          "flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium",
          isActive ? "bg-white text-slate-950" : "text-slate-300 hover:bg-slate-800 hover:text-white"
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
    <div className="min-h-screen bg-slate-100 text-ink">
      <aside className="fixed inset-y-0 left-0 hidden w-72 border-r border-slate-800 bg-slate-950 p-4 text-white lg:block">
        <Link to="/admin" className="flex h-12 items-center gap-3 text-lg font-semibold">
          <Shield className="h-5 w-5 text-teal-300" />
          Operations Console
        </Link>
        <nav className="mt-6 grid gap-1" aria-label="Admin navigation">
          {adminItems.map((item) => (
            <NavigationLink key={item.to} {...item} />
          ))}
        </nav>
      </aside>
      <div className="lg:pl-72">
        <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b border-line bg-white px-4">
          <div>
            <p className="text-sm font-semibold">{session?.username}</p>
            <p className="text-xs text-muted">{session?.roles.join(", ") || "ROLE_ADMIN"}</p>
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
