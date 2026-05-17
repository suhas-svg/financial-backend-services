import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import type { Dispatch, SetStateAction } from "react";
import { useState } from "react";
import { Badge, Button, EmptyState, Input, Panel, Stat } from "../components/ui";
import { exportInvestigationTimelineCsv, getInvestigationSummary, getInvestigationTimeline } from "../lib/queries";
import type { InvestigationItemType, InvestigationTimelineItem } from "../types";

const defaultFilters = {
  userId: "",
  transactionId: "",
  accountId: "",
  alertId: "",
  caseId: "",
  from: "",
  to: ""
};

export function AdminInvestigationsPage() {
  const [filters, setFilters] = useState(defaultFilters);
  const [selected, setSelected] = useState<InvestigationTimelineItem | null>(null);
  const [exportState, setExportState] = useState<"idle" | "loading" | "error">("idle");

  const timeline = useQuery({ queryKey: ["investigation-timeline", filters], queryFn: () => getInvestigationTimeline(filters) });
  const summary = useQuery({ queryKey: ["investigation-summary", filters], queryFn: () => getInvestigationSummary(filters) });
  const timelineItems = timeline.data?.content || [];
  const groupedItems = groupTimeline(timeline.data?.content || []);
  const activeFilters = activeFilterEntries(filters);
  const exportCsv = async () => {
    setExportState("loading");
    try {
      const csv = await exportInvestigationTimelineCsv(filters);
      downloadCsv(csv, "investigation-export.csv");
      setExportState("idle");
    } catch {
      setExportState("error");
    }
  };

  return (
    <div className="investigation-screen grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Investigations</h1>
        <p className="text-sm text-muted">Read-only timeline for transaction, audit, risk alert, and case context.</p>
      </div>

      <Panel
        title="Search context"
        action={(
          <div className="print-hidden flex flex-wrap gap-2">
            <Button variant="secondary" onClick={exportCsv} disabled={exportState === "loading"}>{exportState === "loading" ? "Exporting..." : "Export CSV"}</Button>
            <Button variant="secondary" onClick={() => window.print()}>Print report</Button>
          </div>
        )}
      >
        <div className="grid gap-2 md:grid-cols-4 xl:grid-cols-7">
          <Input placeholder="User ID" value={filters.userId} onChange={(event) => updateFilter(setFilters, "userId", event.target.value)} />
          <Input placeholder="Transaction ID" value={filters.transactionId} onChange={(event) => updateFilter(setFilters, "transactionId", event.target.value)} />
          <Input placeholder="Account ID" value={filters.accountId} onChange={(event) => updateFilter(setFilters, "accountId", event.target.value)} />
          <Input placeholder="Alert ID" value={filters.alertId} onChange={(event) => updateFilter(setFilters, "alertId", event.target.value)} />
          <Input placeholder="Case ID" value={filters.caseId} onChange={(event) => updateFilter(setFilters, "caseId", event.target.value)} />
          <Input type="datetime-local" aria-label="From" value={filters.from} onChange={(event) => updateFilter(setFilters, "from", event.target.value)} />
          <Input type="datetime-local" aria-label="To" value={filters.to} onChange={(event) => updateFilter(setFilters, "to", event.target.value)} />
        </div>
        {exportState === "error" ? <p className="mt-3 text-sm text-danger">Could not export investigation CSV.</p> : null}
      </Panel>

      <div className="grid gap-3 md:grid-cols-4 xl:grid-cols-7">
        <Stat label="Transactions" value={formatNumber(summary.data?.transactions)} />
        <Stat label="Audit events" value={formatNumber(summary.data?.auditEvents)} />
        <Stat label="Risk alerts" value={formatNumber(summary.data?.riskAlerts)} />
        <Stat label="Risk cases" value={formatNumber(summary.data?.riskCases)} />
        <Stat label="Failures" value={<Badge tone={summary.data?.failures ? "bad" : "good"}>{formatNumber(summary.data?.failures)}</Badge>} />
        <Stat label="Reversals" value={formatNumber(summary.data?.reversals)} />
        <Stat label="High severity" value={<Badge tone={summary.data?.highSeverityItems ? "bad" : "neutral"}>{formatNumber(summary.data?.highSeverityItems)}</Badge>} />
      </div>

      <Panel title="Investigation report" className="investigation-report">
        <div className="grid gap-5">
          <div className="grid gap-3 md:grid-cols-3">
            <ReportBlock title="Report scope" value={activeFilters.length ? activeFilters.map((entry) => `${entry.label}: ${entry.value}`).join(" | ") : "All investigation records"} />
            <ReportBlock title="Key finding" value={summary.data?.highSeverityItems ? "High-risk items require review" : "No high-risk items in scope"} tone={summary.data?.highSeverityItems ? "bad" : "good"} />
            <ReportBlock title="Included timeline items" value={`${timelineItems.length} item${timelineItems.length === 1 ? "" : "s"}`} />
          </div>
          <div className="grid gap-2">
            <p className="text-xs font-semibold uppercase text-muted">Report timeline preview</p>
            {timelineItems.length ? timelineItems.slice(0, 6).map((item) => (
              <div key={`report-${item.itemType}-${item.itemId}`} className="grid gap-1 rounded-md border border-line bg-white p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={toneForType(item.itemType)}>{item.itemType}</Badge>
                  {item.severity ? <Badge tone={toneForSeverity(item.severity)}>{item.severity}</Badge> : null}
                  {item.status ? <Badge tone="neutral">{item.status}</Badge> : null}
                </div>
                <p className="text-sm font-medium text-ink">Report item: {item.title}</p>
                <p className="font-mono text-xs font-semibold text-ink">{item.itemId}</p>
                <p className="font-mono text-xs text-muted">{[item.itemId, item.userId, item.transactionId, item.accountId, item.alertId, item.caseId].filter(Boolean).join(" / ")}</p>
              </div>
            )) : <EmptyState title="No report items" detail="Use filters with matching investigation activity to populate the report preview." />}
          </div>
        </div>
      </Panel>

      <div className="grid gap-6 xl:grid-cols-[1fr_430px]">
        <Panel title="Timeline">
          {timeline.error instanceof Error ? <p className="text-sm text-danger">{timeline.error.message}</p> : null}
          {timeline.isLoading ? <p className="text-sm text-muted">Loading investigation timeline...</p> : null}
          {!timeline.isLoading && !timeline.data?.content.length ? (
            <EmptyState title="No timeline items found" detail="Search by user, transaction, account, alert, or case identifiers." />
          ) : null}
          <div className="grid gap-5">
            {groupedItems.map((group) => (
              <div key={group.label} className="grid gap-2">
                <p className="text-xs font-semibold uppercase text-muted">{group.label}</p>
                {group.items.map((item) => (
                  <TimelineRow key={`${item.itemType}-${item.itemId}`} item={item} onSelect={() => setSelected(item)} />
                ))}
              </div>
            ))}
          </div>
        </Panel>

        <Panel title="Item detail" action={selected ? <Button variant="secondary" onClick={() => setSelected(null)}>Clear</Button> : null}>
          {selected ? <TimelineDetail item={selected} /> : <EmptyState title="No item selected" detail="Select a timeline item to inspect linked identifiers and metadata." />}
        </Panel>
      </div>
    </div>
  );
}

function ReportBlock({ title, value, tone = "neutral" }: { title: string; value: string; tone?: "neutral" | "good" | "bad" }) {
  return (
    <div className="rounded-md border border-line bg-white p-3">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{title}</p>
      <p className={tone === "bad" ? "mt-1 text-sm font-semibold text-danger" : tone === "good" ? "mt-1 text-sm font-semibold text-brand" : "mt-1 text-sm font-semibold text-ink"}>{value}</p>
    </div>
  );
}

function TimelineRow({ item, onSelect }: { item: InvestigationTimelineItem; onSelect: () => void }) {
  return (
    <div className="grid gap-3 border-b border-line pb-3 last:border-0 md:grid-cols-[160px_1fr_auto]">
      <div className="text-xs text-muted">{formatDate(item.createdAt)}</div>
      <div className="grid gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={toneForType(item.itemType)}>{item.itemType}</Badge>
          {item.severity ? <Badge tone={toneForSeverity(item.severity)}>{item.severity}</Badge> : null}
          {item.status ? <Badge tone="neutral">{item.status}</Badge> : null}
        </div>
        <p className="text-sm font-medium text-ink">{item.title}</p>
        {item.description ? <p className="text-sm text-muted">{item.description}</p> : null}
        <p className="font-mono text-xs text-muted">{[item.userId, item.transactionId, item.accountId].filter(Boolean).join(" / ") || item.itemId}</p>
      </div>
      <div className="text-right">
        <Button variant="secondary" onClick={onSelect}>View {item.itemId}</Button>
      </div>
    </div>
  );
}

function TimelineDetail({ item }: { item: InvestigationTimelineItem }) {
  const rows = [
    ["Type", item.itemType],
    ["Title", item.title],
    ["Status", item.status],
    ["Severity", item.severity],
    ["User", item.userId],
    ["Transaction", item.transactionId],
    ["Account", item.accountId],
    ["Alert", item.alertId],
    ["Case", item.caseId],
    ["Amount", item.amount && item.currency ? `${item.amount} ${item.currency}` : item.amount],
    ["Created", formatDate(item.createdAt)]
  ];

  return (
    <div className="grid gap-4">
      <dl className="grid gap-3">
        {rows.map(([label, value]) => (
          <div key={label} className="rounded-md border border-line bg-white p-3">
            <dt className="text-xs font-medium uppercase text-muted">{label}</dt>
            <dd className="mt-1 break-words text-sm font-medium text-ink">{value || "n/a"}</dd>
          </div>
        ))}
      </dl>
      {item.description ? <p className="text-sm text-muted">{item.description}</p> : null}
      <div className="flex flex-wrap gap-2">
        {item.alertId ? <Link className="inline-flex h-9 items-center rounded-md border border-line bg-white px-3 text-sm font-medium text-ink hover:bg-slate-50" to="/admin/risk-alerts">Open alert</Link> : null}
        {item.caseId ? <Link className="inline-flex h-9 items-center rounded-md border border-line bg-white px-3 text-sm font-medium text-ink hover:bg-slate-50" to="/admin/risk-cases">Open case</Link> : null}
      </div>
      <div className="rounded-md border border-line bg-white p-3">
        <p className="text-xs font-medium uppercase text-muted">Metadata</p>
        <dl className="mt-2 grid gap-2 text-sm">
          {Object.entries(item.metadata || {}).length ? Object.entries(item.metadata || {}).map(([key, value]) => (
            <div key={key} className="grid gap-1">
              <dt className="font-medium text-ink">{key}</dt>
              <dd className="break-words text-muted">{String(value)}</dd>
            </div>
          )) : <p className="text-sm text-muted">No metadata</p>}
        </dl>
      </div>
    </div>
  );
}

function groupTimeline(items: InvestigationTimelineItem[]) {
  const groups = new Map<string, InvestigationTimelineItem[]>();
  items.forEach((item) => {
    const label = item.createdAt ? new Date(item.createdAt).toLocaleDateString() : "Unknown date";
    groups.set(label, [...(groups.get(label) || []), item]);
  });
  return Array.from(groups.entries()).map(([label, groupItems]) => ({ label, items: groupItems }));
}

function updateFilter(setFilters: Dispatch<SetStateAction<typeof defaultFilters>>, key: keyof typeof defaultFilters, value: string) {
  setFilters((prev) => ({ ...prev, [key]: value }));
}

function activeFilterEntries(filters: typeof defaultFilters) {
  const labels: Record<keyof typeof defaultFilters, string> = {
    userId: "User",
    transactionId: "Transaction",
    accountId: "Account",
    alertId: "Alert",
    caseId: "Case",
    from: "From",
    to: "To"
  };

  return Object.entries(filters)
    .filter(([, value]) => value)
    .map(([key, value]) => ({ label: labels[key as keyof typeof defaultFilters], value }));
}

function downloadCsv(csv: string, filename: string) {
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function toneForType(type: InvestigationItemType): "neutral" | "good" | "warn" | "bad" | "info" {
  if (type === "RISK_ALERT") return "bad";
  if (type === "RISK_CASE") return "warn";
  if (type === "CASE_NOTE") return "info";
  if (type === "AUDIT_EVENT") return "neutral";
  return "good";
}

function toneForSeverity(severity: string): "neutral" | "good" | "warn" | "bad" | "info" {
  if (severity === "HIGH" || severity === "CRITICAL") return "bad";
  if (severity === "MEDIUM") return "warn";
  return "neutral";
}

function formatNumber(value: number | undefined) {
  return String(value ?? 0);
}

function formatDate(value: string | undefined) {
  return value ? new Date(value).toLocaleString() : "n/a";
}
