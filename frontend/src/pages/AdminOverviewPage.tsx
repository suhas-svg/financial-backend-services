import { Link } from "react-router-dom";
import { Activity, ClipboardList, FolderKanban, Search, Shield, ShieldAlert } from "lucide-react";
import { Panel } from "../components/ui";

const workflowCards = [
  {
    to: "/admin/accounts",
    title: "Account operations",
    detail: "Review customer accounts, balances, freezes, and status reasons.",
    action: "Open admin accounts",
    icon: Shield
  },
  {
    to: "/admin/monitoring",
    title: "Service monitoring",
    detail: "Check health, metrics, deployment status, and operational alerts.",
    action: "Open monitoring",
    icon: Activity
  },
  {
    to: "/admin/audit-log",
    title: "Audit trail",
    detail: "Trace transaction, security, reversal, and failure events.",
    action: "Open audit log",
    icon: ClipboardList
  },
  {
    to: "/admin/risk-alerts",
    title: "Risk alerts",
    detail: "Review generated alerts, escalate findings, and create cases.",
    action: "Open risk alerts",
    icon: ShieldAlert
  },
  {
    to: "/admin/risk-cases",
    title: "Risk cases",
    detail: "Claim investigations, add notes, and resolve case work.",
    action: "Open risk cases",
    icon: FolderKanban
  },
  {
    to: "/admin/investigations",
    title: "Investigations",
    detail: "Search timeline evidence and export investigation reports.",
    action: "Open investigations",
    icon: Search
  }
];

export function AdminOverviewPage() {
  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-medium uppercase tracking-wide text-muted">Admin workspace</p>
        <h1 className="text-2xl font-semibold">Operations overview</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted">
          Start from the operational workflow that matches the review, monitoring, or investigation work in progress.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {workflowCards.map(({ to, title, detail, action, icon: Icon }) => (
          <Panel key={to} className="h-full">
            <div className="flex h-full flex-col gap-4">
              <div className="flex items-start gap-3">
                <span className="rounded-md bg-teal-50 p-2 text-brand">
                  <Icon className="h-5 w-5" />
                </span>
                <div>
                  <h2 className="text-base font-semibold">{title}</h2>
                  <p className="mt-1 text-sm text-muted">{detail}</p>
                </div>
              </div>
              <Link className="mt-auto text-sm font-medium text-brand hover:text-teal-800" to={to}>
                {action}
              </Link>
            </div>
          </Panel>
        ))}
      </div>
    </div>
  );
}
