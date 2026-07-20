import { useQueries } from "@tanstack/react-query";
import { Activity, ArrowRight, CircleAlert, CircleCheck, Clock3, FolderKanban, RefreshCw, Scale, ShieldAlert, Users } from "lucide-react";
import { Link } from "react-router-dom";
import { getAccountHealth, getAuditSummary, getDisputeSummary, getRiskCaseSummary, getRiskSummary, getTransactionMonitoringStats, listAccounts, listReconciliationExceptions, searchAuditEvents } from "../lib/queries";
import type { AuditLogEntry } from "../types";
import { utcDateTime } from "../lib/format";
import { Badge, ErrorNotice, PageHeader, Panel, Skeleton } from "./ui";

export function AdminOperationsDashboard() {
  const results = useQueries({ queries: [
    { queryKey: ["dashboard", "health"], queryFn: getAccountHealth },
    { queryKey: ["dashboard", "accounts"], queryFn: () => listAccounts({ size: 1 }) },
    { queryKey: ["dashboard", "risk"], queryFn: () => getRiskSummary() },
    { queryKey: ["dashboard", "cases"], queryFn: () => getRiskCaseSummary() },
    { queryKey: ["dashboard", "disputes"], queryFn: () => getDisputeSummary() },
    { queryKey: ["dashboard", "reconciliation"], queryFn: () => listReconciliationExceptions({ status: "OPEN" }) },
    { queryKey: ["dashboard", "metrics"], queryFn: getTransactionMonitoringStats },
    { queryKey: ["dashboard", "activity"], queryFn: () => searchAuditEvents({ size: 6 }) },
    { queryKey: ["dashboard", "audit-summary"], queryFn: () => getAuditSummary() }
  ] });
  const [health, accounts, risk, cases, disputes, reconciliation, metrics, activity, audit] = results;
  const openAlerts = risk.data?.openAlerts ?? 0;
  const openCases = cases.data?.openCases ?? 0;
  const openDisputes = disputes.data?.openDisputes ?? 0;
  const openExceptions = reconciliation.data?.length ?? 0;
  const attention = [
    { to: "/admin/risk-alerts", label: "Open risk alerts", count: openAlerts, loading: risk.isLoading, unavailable: risk.isError, icon: ShieldAlert, tone: "bad" as const },
    { to: "/admin/risk-cases", label: "Open risk cases", count: openCases, loading: cases.isLoading, unavailable: cases.isError, icon: FolderKanban, tone: "warn" as const },
    { to: "/admin/disputes", label: "Customer disputes", count: openDisputes, loading: disputes.isLoading, unavailable: disputes.isError, icon: Scale, tone: "warn" as const },
    { to: "/admin/reconciliation", label: "Reconciliation exceptions", count: openExceptions, loading: reconciliation.isLoading, unavailable: reconciliation.isError, icon: RefreshCw, tone: "bad" as const }
  ];
  const anyError = results.some((result) => result.error);

  return (
    <div className="grid gap-6 lg:gap-8">
      <PageHeader eyebrow="Live operations" title="Operations overview" detail="A real-time view of service health, customer operations, risk, disputes, reconciliation, and privileged activity." action={<Link to="/admin/investigations" className="inline-flex h-10 items-center gap-2 rounded-xl bg-slate-950 px-4 text-sm font-semibold text-white hover:bg-slate-800">Start investigation <ArrowRight className="h-4 w-4" /></Link>} />
      {anyError ? <ErrorNotice message="Some operational sources are unavailable. Available dashboard data is still shown below." /> : null}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Service status" value={health.isLoading ? undefined : readStatus(health.data)} detail="Account service" icon={health.data ? CircleCheck : Activity} tone="cyan" />
        <MetricCard label="Managed accounts" value={accounts.isLoading ? undefined : String(accounts.data?.totalElements ?? 0)} detail="Across customer owners" icon={Users} tone="blue" />
        <MetricCard label="Daily volume" value={metrics.isLoading ? undefined : formatMetric(metrics.data, "dailyVolume")} detail={`${formatPercent(metrics.data, "successRate")} success rate`} icon={Activity} tone="violet" />
        <MetricCard label="Audit failures" value={audit.isLoading ? undefined : String(audit.data?.failureEvents ?? 0)} detail={`${audit.data?.totalEvents ?? 0} total events`} icon={CircleAlert} tone="amber" />
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(360px,.8fr)]">
        <Panel title="Needs attention" action={<span className="text-xs font-medium text-muted">Prioritized work queues</span>}>
          <div className="grid gap-3 sm:grid-cols-2">
            {attention.map(({ to, label, count, loading, unavailable, icon: Icon, tone }) => <Link key={to} to={to} className="group flex items-center gap-4 rounded-xl border border-slate-200 p-4 transition hover:border-cyan-300 hover:bg-cyan-50/40"><span className="grid h-11 w-11 place-items-center rounded-xl bg-slate-100 text-slate-600 group-hover:bg-white"><Icon className="h-5 w-5" /></span><span className="min-w-0 flex-1"><span className="block text-sm font-semibold text-slate-900">{label}</span><span className="mt-1 block text-xs text-muted">Open workflow queue</span></span>{loading ? <Skeleton className="h-5 w-10" /> : unavailable ? <Badge tone="neutral">Unavailable</Badge> : <Badge tone={count ? tone : "good"}>{count}</Badge>}<ArrowRight className="h-4 w-4 text-slate-400" /></Link>)}
          </div>
        </Panel>

        <Panel title="Operational posture">
          <div className="grid gap-3">
            <PostureRow label="High-severity alerts" value={risk.data?.highSeverityAlerts} loading={risk.isLoading} unavailable={risk.isError} />
            <PostureRow label="Cases in review" value={cases.data?.inReviewCases} loading={cases.isLoading} unavailable={cases.isError} />
            <PostureRow label="Unassigned disputes" value={disputes.data?.unassignedDisputes} loading={disputes.isLoading} unavailable={disputes.isError} />
            <PostureRow label="Reconciliation exceptions" value={openExceptions} loading={reconciliation.isLoading} unavailable={reconciliation.isError} />
          </div>
        </Panel>
      </div>

      <Panel title="Recent operational activity" action={<Link to="/admin/audit-log" className="text-sm font-semibold text-cyan-700">View full audit log</Link>}>
        {activity.isLoading ? <div className="grid gap-3">{[1,2,3].map((key) => <Skeleton key={key} className="h-14" />)}</div> : null}
        {!activity.isLoading && !activity.data?.content.length ? <div className="py-8 text-center"><Clock3 className="mx-auto h-6 w-6 text-slate-400" /><p className="mt-2 text-sm font-medium">No recent operational activity</p><p className="text-sm text-muted">New privileged events will appear here.</p></div> : null}
        {activity.data?.content.length ? <div className="divide-y divide-slate-100">{activity.data.content.map((event) => <ActivityRow key={event.eventId} event={event} />)}</div> : null}
      </Panel>
    </div>
  );
}

function MetricCard({ label, value, detail, icon: Icon, tone }: { label: string; value?: string; detail: string; icon: React.ComponentType<{ className?: string }>; tone: "cyan" | "blue" | "violet" | "amber" }) {
  const colors = { cyan: "bg-cyan-50 text-cyan-700", blue: "bg-blue-50 text-blue-700", violet: "bg-violet-50 text-violet-700", amber: "bg-amber-50 text-amber-700" };
  return <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-subtle"><div className="flex items-start justify-between"><div><p className="text-xs font-bold uppercase tracking-wider text-muted">{label}</p>{value === undefined ? <Skeleton className="mt-3 h-8 w-20" /> : <p className="mt-2 text-2xl font-bold tracking-tight text-slate-950">{value}</p>}</div><span className={`grid h-11 w-11 place-items-center rounded-xl ${colors[tone]}`}><Icon className="h-5 w-5" /></span></div><p className="mt-3 text-xs text-muted">{detail}</p></div>;
}

function PostureRow({ label, value, loading, unavailable }: { label: string; value?: number; loading: boolean; unavailable: boolean }) {
  return <div className="flex items-center justify-between rounded-xl bg-slate-50 px-4 py-3"><span className="text-sm font-medium text-slate-700">{label}</span>{loading ? <Skeleton className="h-5 w-16" /> : unavailable ? <Badge tone="neutral">Unavailable</Badge> : <Badge tone={value ? "warn" : "good"}>{value ? `${value} active` : "Clear"}</Badge>}</div>;
}

function ActivityRow({ event }: { event: AuditLogEntry }) {
  return <div className="flex items-center gap-4 py-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-slate-100"><Clock3 className="h-4 w-4 text-slate-500" /></span><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-slate-900">{event.action}</p><p className="truncate text-xs text-muted">{event.userId || "system"}<span aria-hidden="true"> / </span>{event.transactionId || event.eventType}</p></div><Badge tone={event.outcome === "SUCCESS" ? "good" : event.outcome === "FAILURE" ? "bad" : "neutral"}>{event.outcome}</Badge><time className="hidden text-xs text-muted sm:block">{utcDateTime(event.createdAt)}</time></div>;
}

function readStatus(data: unknown) {
  if (!data || typeof data !== "object") return "Unknown";
  return String((data as Record<string, unknown>).status ?? "Available");
}

function formatMetric(data: unknown, key: string) {
  if (!data || typeof data !== "object") return "n/a";
  const value = (data as Record<string, unknown>)[key];
  if (typeof value === "number") return Number.isInteger(value) ? String(value) : value.toFixed(1);
  return value === undefined ? "n/a" : String(value);
}

function formatPercent(data: unknown, key: string) {
  if (!data || typeof data !== "object") return "n/a";
  const value = (data as Record<string, unknown>)[key];
  return typeof value === "number" ? `${(value * 100).toFixed(value === 1 || value === 0 ? 0 : 1)}%` : "n/a";
}
