import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Dispatch, SetStateAction } from "react";
import { useState } from "react";
import { Badge, Button, EmptyState, Input, Panel, Select, Stat } from "../components/ui";
import { addRiskCaseNote, claimRiskCase, getRiskCaseSummary, searchRiskCases, updateRiskCaseStatus } from "../lib/queries";
import type { RiskCase, RiskCasePriority, RiskCaseStatus } from "../types";

const defaultFilters = {
  status: "",
  priority: "",
  assignedTo: "",
  userId: "",
  transactionId: "",
  alertId: "",
  from: "",
  to: ""
};

export function AdminRiskCasesPage() {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState(defaultFilters);
  const [selected, setSelected] = useState<RiskCase | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");
  const [internalNote, setInternalNote] = useState("");

  const cases = useQuery({ queryKey: ["risk-cases", filters], queryFn: () => searchRiskCases(filters) });
  const summary = useQuery({ queryKey: ["risk-case-summary", filters.from, filters.to], queryFn: () => getRiskCaseSummary({ from: filters.from, to: filters.to }) });

  const claimMutation = useMutation({
    mutationFn: (riskCase: RiskCase) => claimRiskCase(riskCase.caseId),
    onSuccess: (updated) => {
      setSelected(updated);
      queryClient.invalidateQueries({ queryKey: ["risk-cases"] });
      queryClient.invalidateQueries({ queryKey: ["risk-case-summary"] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: ({ riskCase, status }: { riskCase: RiskCase; status: RiskCaseStatus }) =>
      updateRiskCaseStatus(riskCase.caseId, { status, resolutionNote }),
    onSuccess: (updated) => {
      setSelected(updated);
      setResolutionNote(updated.resolutionNote || "");
      queryClient.invalidateQueries({ queryKey: ["risk-cases"] });
      queryClient.invalidateQueries({ queryKey: ["risk-case-summary"] });
    }
  });

  const noteMutation = useMutation({
    mutationFn: (riskCase: RiskCase) => addRiskCaseNote(riskCase.caseId, { note: internalNote }),
    onSuccess: (updated) => {
      setSelected(updated);
      setInternalNote("");
      queryClient.invalidateQueries({ queryKey: ["risk-cases"] });
    }
  });

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Risk Cases</h1>
        <p className="text-sm text-muted">Internal case workflow for risk alerts that need admin investigation.</p>
      </div>

      <div className="grid gap-3 md:grid-cols-5">
        <Stat label="Total cases" value={formatNumber(summary.data?.totalCases)} />
        <Stat label="Open" value={<Badge tone={summary.data?.openCases ? "warn" : "good"}>{formatNumber(summary.data?.openCases)}</Badge>} />
        <Stat label="In review" value={formatNumber(summary.data?.inReviewCases)} />
        <Stat label="Resolved" value={<Badge tone="good">{formatNumber(summary.data?.resolvedCases)}</Badge>} />
        <Stat label="Unassigned" value={<Badge tone={summary.data?.unassignedCases ? "warn" : "neutral"}>{formatNumber(summary.data?.unassignedCases)}</Badge>} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_430px]">
        <Panel
          title="Case queue"
          action={
            <div className="grid w-full max-w-6xl grid-cols-8 gap-2">
              <Select value={filters.status} onChange={(event) => updateFilter(setFilters, "status", event.target.value)}>
                <option value="">All status</option>
                <option value="OPEN">Open</option>
                <option value="IN_REVIEW">In review</option>
                <option value="RESOLVED">Resolved</option>
                <option value="CLOSED">Closed</option>
              </Select>
              <Select value={filters.priority} onChange={(event) => updateFilter(setFilters, "priority", event.target.value)}>
                <option value="">All priority</option>
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </Select>
              <Select value={filters.assignedTo} onChange={(event) => updateFilter(setFilters, "assignedTo", event.target.value)}>
                <option value="">All assignment</option>
                <option value="UNASSIGNED">Unassigned</option>
              </Select>
              <Input placeholder="User ID" value={filters.userId} onChange={(event) => updateFilter(setFilters, "userId", event.target.value)} />
              <Input placeholder="Transaction ID" value={filters.transactionId} onChange={(event) => updateFilter(setFilters, "transactionId", event.target.value)} />
              <Input placeholder="Alert ID" value={filters.alertId} onChange={(event) => updateFilter(setFilters, "alertId", event.target.value)} />
              <Input type="datetime-local" aria-label="From" value={filters.from} onChange={(event) => updateFilter(setFilters, "from", event.target.value)} />
              <Input type="datetime-local" aria-label="To" value={filters.to} onChange={(event) => updateFilter(setFilters, "to", event.target.value)} />
            </div>
          }
        >
          {cases.error instanceof Error ? <p className="text-sm text-danger">{cases.error.message}</p> : null}
          {cases.isLoading ? <p className="text-sm text-muted">Loading risk cases...</p> : null}
          {!cases.isLoading && !cases.data?.content.length ? <EmptyState title="No risk cases found" detail="Adjust filters to inspect another case queue window." /> : null}
          {cases.data?.content.length ? <RiskCaseTable cases={cases.data.content} onSelect={(riskCase) => {
            setSelected(riskCase);
            setResolutionNote(riskCase.resolutionNote || "");
            setInternalNote("");
          }} /> : null}
        </Panel>

        <Panel title="Case detail" action={selected ? <Button variant="secondary" onClick={() => setSelected(null)}>Clear</Button> : null}>
          {selected ? (
            <RiskCaseDetail
              riskCase={selected}
              resolutionNote={resolutionNote}
              internalNote={internalNote}
              onResolutionNoteChange={setResolutionNote}
              onInternalNoteChange={setInternalNote}
              onClaim={() => claimMutation.mutate(selected)}
              onStatusChange={(status) => statusMutation.mutate({ riskCase: selected, status })}
              onAddNote={() => noteMutation.mutate(selected)}
              isUpdating={claimMutation.isPending || statusMutation.isPending || noteMutation.isPending}
            />
          ) : (
            <EmptyState title="No case selected" detail="Select a risk case to inspect alert context, claim it, and add notes." />
          )}
        </Panel>
      </div>
    </div>
  );
}

function RiskCaseTable({ cases, onSelect }: { cases: RiskCase[]; onSelect: (riskCase: RiskCase) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Case</th>
            <th>Priority</th>
            <th>Status</th>
            <th>User</th>
            <th>Transaction</th>
            <th>Assigned</th>
            <th>Created</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {cases.map((riskCase) => (
            <tr key={riskCase.caseId} className="border-b border-line last:border-0">
              <td className="py-3">
                <p className="font-medium">{riskCase.caseNumber}</p>
                <p className="text-xs text-muted">{riskCase.title}</p>
              </td>
              <td><Badge tone={toneForPriority(riskCase.priority)}>{riskCase.priority}</Badge></td>
              <td><Badge tone={toneForStatus(riskCase.status)}>{riskCase.status}</Badge></td>
              <td>{riskCase.userId || "system"}</td>
              <td className="font-mono text-xs">{riskCase.transactionId || "n/a"}</td>
              <td>{riskCase.assignedTo || "Unassigned"}</td>
              <td>{formatDate(riskCase.createdAt)}</td>
              <td className="text-right">
                <Button variant="secondary" onClick={() => onSelect(riskCase)}>View {riskCase.caseNumber}</Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RiskCaseDetail({
  riskCase,
  resolutionNote,
  internalNote,
  onResolutionNoteChange,
  onInternalNoteChange,
  onClaim,
  onStatusChange,
  onAddNote,
  isUpdating
}: {
  riskCase: RiskCase;
  resolutionNote: string;
  internalNote: string;
  onResolutionNoteChange: (value: string) => void;
  onInternalNoteChange: (value: string) => void;
  onClaim: () => void;
  onStatusChange: (status: RiskCaseStatus) => void;
  onAddNote: () => void;
  isUpdating: boolean;
}) {
  const rows = [
    ["Case number", riskCase.caseNumber],
    ["Title", riskCase.title],
    ["Status", riskCase.status],
    ["Priority", riskCase.priority],
    ["User", riskCase.userId],
    ["Transaction", riskCase.transactionId],
    ["Primary alert", riskCase.primaryAlertId],
    ["Assigned to", riskCase.assignedTo || "Unassigned"],
    ["Created by", riskCase.createdBy],
    ["Created", formatDate(riskCase.createdAt)],
    ["Claimed", riskCase.claimedAt ? formatDate(riskCase.claimedAt) : undefined],
    ["Closed", riskCase.closedAt ? formatDate(riskCase.closedAt) : undefined]
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

      {riskCase.linkedAlerts?.length ? (
        <div className="rounded-md border border-line bg-white p-3">
          <p className="text-xs font-medium uppercase text-muted">Linked alert</p>
          {riskCase.linkedAlerts.map((alert) => (
            <div key={alert.alertId} className="mt-2 grid gap-1 text-sm">
              <p className="font-medium">{alert.alertType}</p>
              <p className="text-muted">{alert.reason}</p>
              <p className="font-mono text-xs text-muted">{alert.alertId}</p>
            </div>
          ))}
        </div>
      ) : null}

      {!riskCase.assignedTo ? <Button variant="secondary" disabled={isUpdating} onClick={onClaim}>Claim case</Button> : null}

      <label className="grid gap-1 text-sm">
        <span className="font-medium text-ink">Internal note</span>
        <textarea
          className="min-h-20 w-full rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
          placeholder="Internal note"
          value={internalNote}
          onChange={(event) => onInternalNoteChange(event.target.value)}
        />
      </label>
      <Button variant="secondary" disabled={isUpdating || !internalNote.trim()} onClick={onAddNote}>Add note</Button>

      <div className="grid gap-2">
        <p className="text-sm font-medium text-ink">Notes</p>
        {riskCase.notes?.length ? riskCase.notes.map((note) => (
          <div key={note.noteId} className="rounded-md border border-line bg-white p-3">
            <p className="text-sm">{note.note}</p>
            <p className="mt-1 text-xs text-muted">{note.author} - {formatDate(note.createdAt)}</p>
          </div>
        )) : <p className="text-sm text-muted">No internal notes yet.</p>}
      </div>

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
        <Button variant="secondary" disabled={isUpdating} onClick={() => onStatusChange("IN_REVIEW")}>Mark in review</Button>
        <Button variant="secondary" disabled={isUpdating} onClick={() => onStatusChange("RESOLVED")}>Resolve case</Button>
        <Button variant="danger" disabled={isUpdating} onClick={() => onStatusChange("CLOSED")}>Close case</Button>
      </div>
    </div>
  );
}

function updateFilter(setFilters: Dispatch<SetStateAction<typeof defaultFilters>>, key: keyof typeof defaultFilters, value: string) {
  setFilters((prev) => ({ ...prev, [key]: value }));
}

function toneForPriority(priority: RiskCasePriority): "neutral" | "good" | "warn" | "bad" | "info" {
  if (priority === "CRITICAL" || priority === "HIGH") return "bad";
  if (priority === "MEDIUM") return "warn";
  return "neutral";
}

function toneForStatus(status: RiskCaseStatus): "neutral" | "good" | "warn" | "bad" | "info" {
  if (status === "OPEN") return "warn";
  if (status === "IN_REVIEW") return "info";
  if (status === "RESOLVED") return "good";
  return "neutral";
}

function formatNumber(value: number | undefined) {
  return String(value ?? 0);
}

function formatDate(value: string | undefined) {
  return value ? new Date(value).toLocaleString() : "n/a";
}
