import { useQuery } from "@tanstack/react-query";
import type { Dispatch, SetStateAction } from "react";
import { useState } from "react";
import { Badge, Button, EmptyState, Input, Panel, Select, Stat } from "../components/ui";
import { getAuditSummary, searchAuditEvents } from "../lib/queries";
import type { AuditLogEntry } from "../types";

const defaultFilters = {
  action: "",
  outcome: "",
  userId: "",
  transactionId: "",
  from: "",
  to: ""
};

export function AdminAuditLogPage() {
  const [filters, setFilters] = useState(defaultFilters);
  const [selected, setSelected] = useState<AuditLogEntry | null>(null);
  const events = useQuery({ queryKey: ["audit-events", filters], queryFn: () => searchAuditEvents(filters) });
  const summary = useQuery({ queryKey: ["audit-summary", filters.from, filters.to], queryFn: () => getAuditSummary({ from: filters.from, to: filters.to }) });

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Audit Log</h1>
        <p className="text-sm text-muted">Privileged transaction and security event history.</p>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <Stat label="Total events" value={formatNumber(summary.data?.totalEvents)} />
        <Stat label="Failures" value={<Badge tone={summary.data?.failureEvents ? "bad" : "good"}>{formatNumber(summary.data?.failureEvents)}</Badge>} />
        <Stat label="Reversals" value={formatNumber(summary.data?.reversalEvents)} />
        <Stat label="Security" value={<Badge tone={summary.data?.securityEvents ? "warn" : "neutral"}>{formatNumber(summary.data?.securityEvents)}</Badge>} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
        <Panel
          title="Audit events"
          action={
            <div className="grid w-full max-w-5xl grid-cols-6 gap-2">
              <Select value={filters.action} onChange={(event) => updateFilter(setFilters, "action", event.target.value)}>
                <option value="">All actions</option>
                <option value="TRANSACTION_INITIATED">Transaction initiated</option>
                <option value="TRANSACTION_COMPLETED">Transaction completed</option>
                <option value="TRANSACTION_FAILED">Transaction failed</option>
                <option value="TRANSACTION_REVERSED">Transaction reversed</option>
                <option value="SECURITY_EVENT">Security event</option>
              </Select>
              <Select value={filters.outcome} onChange={(event) => updateFilter(setFilters, "outcome", event.target.value)}>
                <option value="">All outcomes</option>
                <option value="SUCCESS">Success</option>
                <option value="FAILURE">Failure</option>
                <option value="INITIATED">Initiated</option>
                <option value="ACCESS_DENIED">Access denied</option>
              </Select>
              <Input placeholder="User ID" value={filters.userId} onChange={(event) => updateFilter(setFilters, "userId", event.target.value)} />
              <Input placeholder="Transaction ID" value={filters.transactionId} onChange={(event) => updateFilter(setFilters, "transactionId", event.target.value)} />
              <Input type="datetime-local" aria-label="From" value={filters.from} onChange={(event) => updateFilter(setFilters, "from", event.target.value)} />
              <Input type="datetime-local" aria-label="To" value={filters.to} onChange={(event) => updateFilter(setFilters, "to", event.target.value)} />
            </div>
          }
        >
          {events.error instanceof Error ? <p className="text-sm text-danger">{events.error.message}</p> : null}
          {events.isLoading ? <p className="text-sm text-muted">Loading audit events...</p> : null}
          {!events.isLoading && !events.data?.content.length ? <EmptyState title="No audit events found" detail="Adjust filters to inspect another audit window." /> : null}
          {events.data?.content.length ? <AuditTable events={events.data.content} onSelect={setSelected} /> : null}
        </Panel>

        <Panel
          title="Event detail"
          action={selected ? <Button variant="secondary" onClick={() => setSelected(null)}>Clear</Button> : null}
        >
          {selected ? <AuditDetail event={selected} /> : <EmptyState title="No event selected" detail="Select an audit event to inspect details." />}
        </Panel>
      </div>
    </div>
  );
}

function AuditTable({ events, onSelect }: { events: AuditLogEntry[]; onSelect: (event: AuditLogEntry) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Action</th>
            <th>Outcome</th>
            <th>User</th>
            <th>Transaction</th>
            <th>Amount</th>
            <th>Created</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <tr key={event.eventId} className="border-b border-line last:border-0">
              <td className="py-3 font-medium">{event.action}</td>
              <td><Badge tone={toneForOutcome(event.outcome)}>{event.outcome}</Badge></td>
              <td>{event.userId || "system"}</td>
              <td className="font-mono text-xs">{event.transactionId || "n/a"}</td>
              <td>{event.amount === undefined ? "n/a" : `${event.amount} ${event.currency || ""}`}</td>
              <td>{formatDate(event.createdAt)}</td>
              <td className="text-right">
                <Button variant="secondary" onClick={() => onSelect(event)}>View {event.eventId}</Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AuditDetail({ event }: { event: AuditLogEntry }) {
  const rows = [
    ["Event ID", event.eventId],
    ["Type", event.eventType],
    ["Action", event.action],
    ["Outcome", event.outcome],
    ["User", event.userId],
    ["Transaction", event.transactionId],
    ["From account", event.fromAccountId],
    ["To account", event.toAccountId],
    ["Amount", event.amount === undefined ? undefined : `${event.amount} ${event.currency || ""}`],
    ["IP address", event.ipAddress],
    ["Details", event.details],
    ["Error", event.errorMessage || event.errorCode],
    ["Metadata", event.metadata],
    ["Created", formatDate(event.createdAt)]
  ];

  return (
    <dl className="grid gap-3">
      {rows.map(([label, value]) => (
        <div key={label} className="rounded-md border border-line bg-white p-3">
          <dt className="text-xs font-medium uppercase text-muted">{label}</dt>
          <dd className="mt-1 break-words text-sm font-medium text-ink">{value || "n/a"}</dd>
        </div>
      ))}
    </dl>
  );
}

function updateFilter(setFilters: Dispatch<SetStateAction<typeof defaultFilters>>, key: keyof typeof defaultFilters, value: string) {
  setFilters((prev) => ({ ...prev, [key]: value }));
}

function toneForOutcome(outcome: string): "neutral" | "good" | "warn" | "bad" | "info" {
  if (outcome === "SUCCESS") return "good";
  if (outcome === "FAILURE" || outcome === "ACCESS_DENIED") return "bad";
  if (outcome === "INITIATED") return "info";
  return "neutral";
}

function formatNumber(value: number | undefined) {
  return String(value ?? 0);
}

function formatDate(value: string) {
  return value ? new Date(value).toLocaleString() : "n/a";
}
