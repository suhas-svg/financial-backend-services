import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Dispatch, SetStateAction } from "react";
import { useState } from "react";
import { Badge, Button, EmptyState, Input, Panel, Select, Stat } from "../components/ui";
import { getRiskSummary, searchRiskAlerts, updateRiskAlertStatus } from "../lib/queries";
import type { RiskAlert, RiskAlertStatus } from "../types";

const defaultFilters = {
  status: "",
  severity: "",
  alertType: "",
  userId: "",
  transactionId: "",
  from: "",
  to: ""
};

export function AdminRiskAlertsPage() {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState(defaultFilters);
  const [selected, setSelected] = useState<RiskAlert | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");

  const alerts = useQuery({ queryKey: ["risk-alerts", filters], queryFn: () => searchRiskAlerts(filters) });
  const summary = useQuery({ queryKey: ["risk-summary", filters.from, filters.to], queryFn: () => getRiskSummary({ from: filters.from, to: filters.to }) });
  const statusMutation = useMutation({
    mutationFn: ({ alert, status }: { alert: RiskAlert; status: Exclude<RiskAlertStatus, "OPEN"> }) =>
      updateRiskAlertStatus(alert.alertId, { status, resolutionNote }),
    onSuccess: (updated) => {
      setSelected(updated);
      setResolutionNote(updated.resolutionNote || "");
      queryClient.invalidateQueries({ queryKey: ["risk-alerts"] });
      queryClient.invalidateQueries({ queryKey: ["risk-summary"] });
    }
  });

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Risk Alerts</h1>
        <p className="text-sm text-muted">Operational review queue for conservative transaction risk rules.</p>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <Stat label="Total alerts" value={formatNumber(summary.data?.totalAlerts)} />
        <Stat label="Open" value={<Badge tone={summary.data?.openAlerts ? "warn" : "good"}>{formatNumber(summary.data?.openAlerts)}</Badge>} />
        <Stat label="High severity" value={<Badge tone={summary.data?.highSeverityAlerts ? "bad" : "neutral"}>{formatNumber(summary.data?.highSeverityAlerts)}</Badge>} />
        <Stat label="Escalated" value={formatNumber(summary.data?.escalatedAlerts)} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
        <Panel
          title="Review queue"
          action={
            <div className="grid w-full max-w-6xl grid-cols-7 gap-2">
              <Select value={filters.status} onChange={(event) => updateFilter(setFilters, "status", event.target.value)}>
                <option value="">All status</option>
                <option value="OPEN">Open</option>
                <option value="REVIEWED">Reviewed</option>
                <option value="DISMISSED">Dismissed</option>
                <option value="ESCALATED">Escalated</option>
              </Select>
              <Select value={filters.severity} onChange={(event) => updateFilter(setFilters, "severity", event.target.value)}>
                <option value="">All severity</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
              </Select>
              <Select value={filters.alertType} onChange={(event) => updateFilter(setFilters, "alertType", event.target.value)}>
                <option value="">All rules</option>
                <option value="HIGH_VALUE_TRANSFER">High value</option>
                <option value="REPEATED_FAILURES">Repeated failures</option>
                <option value="RAPID_TRANSFERS">Rapid transfers</option>
                <option value="REVERSAL_HEAVY_ACTIVITY">Reversal-heavy</option>
              </Select>
              <Input placeholder="User ID" value={filters.userId} onChange={(event) => updateFilter(setFilters, "userId", event.target.value)} />
              <Input placeholder="Transaction ID" value={filters.transactionId} onChange={(event) => updateFilter(setFilters, "transactionId", event.target.value)} />
              <Input type="datetime-local" aria-label="From" value={filters.from} onChange={(event) => updateFilter(setFilters, "from", event.target.value)} />
              <Input type="datetime-local" aria-label="To" value={filters.to} onChange={(event) => updateFilter(setFilters, "to", event.target.value)} />
            </div>
          }
        >
          {alerts.error instanceof Error ? <p className="text-sm text-danger">{alerts.error.message}</p> : null}
          {alerts.isLoading ? <p className="text-sm text-muted">Loading risk alerts...</p> : null}
          {!alerts.isLoading && !alerts.data?.content.length ? <EmptyState title="No risk alerts found" detail="Adjust filters to inspect another review window." /> : null}
          {alerts.data?.content.length ? <RiskAlertTable alerts={alerts.data.content} onSelect={(alert) => {
            setSelected(alert);
            setResolutionNote(alert.resolutionNote || "");
          }} /> : null}
        </Panel>

        <Panel
          title="Alert detail"
          action={selected ? <Button variant="secondary" onClick={() => setSelected(null)}>Clear</Button> : null}
        >
          {selected ? (
            <RiskAlertDetail
              alert={selected}
              resolutionNote={resolutionNote}
              onResolutionNoteChange={setResolutionNote}
              onStatusChange={(status) => statusMutation.mutate({ alert: selected, status })}
              isUpdating={statusMutation.isPending}
            />
          ) : (
            <EmptyState title="No alert selected" detail="Select a risk alert to inspect and resolve it." />
          )}
        </Panel>
      </div>
    </div>
  );
}

function RiskAlertTable({ alerts, onSelect }: { alerts: RiskAlert[]; onSelect: (alert: RiskAlert) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Rule</th>
            <th>Severity</th>
            <th>Status</th>
            <th>User</th>
            <th>Transaction</th>
            <th>Amount</th>
            <th>Created</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {alerts.map((alert) => (
            <tr key={alert.alertId} className="border-b border-line last:border-0">
              <td className="py-3 font-medium">{alert.alertType}</td>
              <td><Badge tone={alert.severity === "HIGH" ? "bad" : "warn"}>{alert.severity}</Badge></td>
              <td><Badge tone={toneForStatus(alert.status)}>{alert.status}</Badge></td>
              <td>{alert.userId || "system"}</td>
              <td className="font-mono text-xs">{alert.transactionId || "n/a"}</td>
              <td>{alert.amount === undefined ? "n/a" : `${alert.amount} ${alert.currency || ""}`}</td>
              <td>{formatDate(alert.createdAt)}</td>
              <td className="text-right">
                <Button variant="secondary" onClick={() => onSelect(alert)}>View {alert.alertId}</Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RiskAlertDetail({
  alert,
  resolutionNote,
  onResolutionNoteChange,
  onStatusChange,
  isUpdating
}: {
  alert: RiskAlert;
  resolutionNote: string;
  onResolutionNoteChange: (value: string) => void;
  onStatusChange: (status: Exclude<RiskAlertStatus, "OPEN">) => void;
  isUpdating: boolean;
}) {
  const rows = [
    ["Alert ID", alert.alertId],
    ["Rule", alert.alertType],
    ["Severity", alert.severity],
    ["Status", alert.status],
    ["User", alert.userId],
    ["Transaction", alert.transactionId],
    ["From account", alert.fromAccountId],
    ["To account", alert.toAccountId],
    ["Amount", alert.amount === undefined ? undefined : `${alert.amount} ${alert.currency || ""}`],
    ["Reason", alert.reason],
    ["Recommendation", alert.recommendation],
    ["Metadata", alert.metadata],
    ["Reviewed by", alert.reviewedBy],
    ["Reviewed at", alert.reviewedAt ? formatDate(alert.reviewedAt) : undefined],
    ["Created", formatDate(alert.createdAt)]
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
      <label className="grid gap-1 text-sm">
        <span className="font-medium text-ink">Resolution note</span>
        <textarea
          className="min-h-20 w-full rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
          placeholder="Resolution note"
          value={resolutionNote}
          onChange={(event) => onResolutionNoteChange(event.target.value)}
        />
      </label>
      <div className="grid gap-2 sm:grid-cols-3">
        <Button variant="secondary" disabled={isUpdating} onClick={() => onStatusChange("REVIEWED")}>Mark reviewed</Button>
        <Button variant="secondary" disabled={isUpdating} onClick={() => onStatusChange("DISMISSED")}>Dismiss alert</Button>
        <Button variant="danger" disabled={isUpdating} onClick={() => onStatusChange("ESCALATED")}>Escalate alert</Button>
      </div>
    </div>
  );
}

function updateFilter(setFilters: Dispatch<SetStateAction<typeof defaultFilters>>, key: keyof typeof defaultFilters, value: string) {
  setFilters((prev) => ({ ...prev, [key]: value }));
}

function toneForStatus(status: RiskAlertStatus): "neutral" | "good" | "warn" | "bad" | "info" {
  if (status === "OPEN") return "warn";
  if (status === "ESCALATED") return "bad";
  if (status === "REVIEWED") return "good";
  if (status === "DISMISSED") return "neutral";
  return "info";
}

function formatNumber(value: number | undefined) {
  return String(value ?? 0);
}

function formatDate(value: string) {
  return value ? new Date(value).toLocaleString() : "n/a";
}
