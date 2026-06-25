import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Badge, Button, EmptyState, ErrorNotice, Input, Panel, Select, Stat } from "../components/ui";
import { addReconciliationExceptionNote, assignReconciliationException, listReconciliationExceptions, listReconciliationRuns, runReconciliation, updateReconciliationExceptionStatus } from "../lib/queries";
import type { ReconciliationException, ReconciliationExceptionStatus, ReconciliationRun, ReconciliationSeverity } from "../types";

const emptyFilters: { status: ReconciliationExceptionStatus | ""; severity: ReconciliationSeverity | "" } = {
  status: "",
  severity: ""
};

export function AdminReconciliationPage() {
  const queryClient = useQueryClient();
  const [businessDate, setBusinessDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [filters, setFilters] = useState<{ status: ReconciliationExceptionStatus | ""; severity: ReconciliationSeverity | "" }>(emptyFilters);
  const [selected, setSelected] = useState<ReconciliationException | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");
  const [assignee, setAssignee] = useState("");
  const [internalNote, setInternalNote] = useState("");

  const runs = useQuery({ queryKey: ["reconciliation-runs"], queryFn: listReconciliationRuns });
  const exceptions = useQuery({
    queryKey: ["reconciliation-exceptions", filters],
    queryFn: () => listReconciliationExceptions(filters)
  });

  const runMutation = useMutation({
    mutationFn: () => runReconciliation({ businessDate }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reconciliation-runs"] });
      queryClient.invalidateQueries({ queryKey: ["reconciliation-exceptions"] });
    }
  });

  const resolveMutation = useMutation({
    mutationFn: (exception: ReconciliationException) =>
      updateReconciliationExceptionStatus(exception.exceptionId, {
        status: "RESOLVED",
        note: resolutionNote,
        expectedVersion: exception.version
      }),
    onSuccess: (updated) => {
      setSelected(updated);
      setResolutionNote("");
      queryClient.invalidateQueries({ queryKey: ["reconciliation-exceptions"] });
      queryClient.invalidateQueries({ queryKey: ["reconciliation-runs"] });
    }
  });

  const assignMutation = useMutation({
    mutationFn: (exception: ReconciliationException) =>
      assignReconciliationException(exception.exceptionId, {
        assignedTo: assignee,
        expectedVersion: exception.version
      }),
    onSuccess: (updated) => {
      setSelected(updated);
      setAssignee("");
      queryClient.invalidateQueries({ queryKey: ["reconciliation-exceptions"] });
      queryClient.invalidateQueries({ queryKey: ["reconciliation-runs"] });
    }
  });

  const noteMutation = useMutation({
    mutationFn: (exception: ReconciliationException) =>
      addReconciliationExceptionNote(exception.exceptionId, {
        note: internalNote
      }),
    onSuccess: (updated) => {
      setSelected(updated);
      setInternalNote("");
      queryClient.invalidateQueries({ queryKey: ["reconciliation-exceptions"] });
    }
  });

  const latestRun = runs.data?.[0];
  const openExceptions = exceptions.data?.filter((item) => !["RESOLVED", "WAIVED"].includes(item.status)).length ?? 0;
  const criticalExceptions = exceptions.data?.filter((item) => item.severity === "CRITICAL").length ?? 0;

  return (
    <div className="grid gap-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Reconciliation</h1>
          <p className="text-sm text-muted">Daily ledger controls, immutable exception handling, and operator resolution workflow.</p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
          <label className="grid gap-1 text-sm">
            <span className="font-medium text-ink">Business date</span>
            <Input type="date" value={businessDate} onChange={(event) => setBusinessDate(event.target.value)} />
          </label>
          <Button onClick={() => runMutation.mutate()} disabled={runMutation.isPending}>
            Run daily reconciliation
          </Button>
        </div>
      </div>

      <ErrorNotice message={errorMessage(runs.error) || errorMessage(exceptions.error) || errorMessage(runMutation.error) || errorMessage(resolveMutation.error) || errorMessage(assignMutation.error) || errorMessage(noteMutation.error)} />

      <div className="grid gap-3 md:grid-cols-4">
        <Stat label="Latest run" value={latestRun ? latestRun.status : "No runs"} />
        <Stat label="Business date" value={latestRun?.businessDate || businessDate} />
        <Stat label="Open exceptions" value={<Badge tone={openExceptions ? "warn" : "good"}>{openExceptions}</Badge>} />
        <Stat label="Critical exceptions" value={<Badge tone={criticalExceptions ? "bad" : "good"}>{criticalExceptions} critical</Badge>} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
        <Panel
          title="Exception queue"
          action={
            <div className="grid w-full max-w-md grid-cols-2 gap-2">
              <label className="sr-only" htmlFor="reconciliation-exception-status">Exception status</label>
              <Select
                id="reconciliation-exception-status"
                aria-label="Exception status"
                value={filters.status}
                onChange={(event) => setFilters((prev) => ({ ...prev, status: event.target.value as ReconciliationExceptionStatus | "" }))}
              >
                <option value="">All status</option>
                <option value="OPEN">Open</option>
                <option value="ACKNOWLEDGED">Acknowledged</option>
                <option value="IN_PROGRESS">In progress</option>
                <option value="RESOLVED">Resolved</option>
                <option value="WAIVED">Waived</option>
              </Select>
              <label className="sr-only" htmlFor="reconciliation-severity">Severity</label>
              <Select
                id="reconciliation-severity"
                aria-label="Severity"
                value={filters.severity}
                onChange={(event) => setFilters((prev) => ({ ...prev, severity: event.target.value as ReconciliationSeverity | "" }))}
              >
                <option value="">All severity</option>
                <option value="INFO">Info</option>
                <option value="WARNING">Warning</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </Select>
            </div>
          }
        >
          {exceptions.isLoading ? <p className="text-sm text-muted">Loading reconciliation exceptions...</p> : null}
          {!exceptions.isLoading && !exceptions.data?.length ? (
            <EmptyState title="No reconciliation exceptions" detail="Daily controls have not detected ledger drift for this filter set." />
          ) : null}
          {exceptions.data?.length ? <ExceptionTable exceptions={exceptions.data} onSelect={setSelected} /> : null}
        </Panel>

        <Panel title="Exception detail" action={selected ? <Button variant="secondary" onClick={() => setSelected(null)}>Clear</Button> : null}>
          {selected ? (
            <ExceptionDetail
              exception={selected}
              resolutionNote={resolutionNote}
              assignee={assignee}
              internalNote={internalNote}
              onResolutionNoteChange={setResolutionNote}
              onAssigneeChange={setAssignee}
              onInternalNoteChange={setInternalNote}
              onAssign={() => assignMutation.mutate(selected)}
              onAddNote={() => noteMutation.mutate(selected)}
              onResolve={() => resolveMutation.mutate(selected)}
              assigning={assignMutation.isPending}
              addingNote={noteMutation.isPending}
              resolving={resolveMutation.isPending}
            />
          ) : (
            <EmptyState title="No exception selected" detail="Select an exception to inspect ledger impact and record resolution." />
          )}
        </Panel>
      </div>

      <Panel title="Recent reconciliation runs">
        {runs.isLoading ? <p className="text-sm text-muted">Loading reconciliation runs...</p> : null}
        {!runs.isLoading && !runs.data?.length ? <EmptyState title="No reconciliation runs" detail="Run daily reconciliation to create the first control record." /> : null}
        {runs.data?.length ? <RunTable runs={runs.data} /> : null}
      </Panel>
    </div>
  );
}

function ExceptionTable({ exceptions, onSelect }: { exceptions: ReconciliationException[]; onSelect: (exception: ReconciliationException) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Exception</th>
            <th>Severity</th>
            <th>Status</th>
            <th>Account</th>
            <th>Delta</th>
            <th>Detected</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {exceptions.map((exception) => (
            <tr key={exception.exceptionId} className="border-b border-line last:border-0">
              <td className="py-3">
                <p className="font-medium">{titleFor(exception)}</p>
                <p className="font-mono text-xs text-muted">{exception.checkCode}</p>
              </td>
              <td><Badge tone={toneForSeverity(exception.severity)}>{exception.severity}</Badge></td>
              <td><Badge tone={toneForStatus(exception.status)}>{exception.status}</Badge></td>
              <td className="font-mono text-xs">{exception.externalAccountId || exception.ledgerAccountId || "n/a"}</td>
              <td>{formatAmount(exception.deltaAmount, exception.currency)}</td>
              <td>{formatDate(exception.detectedAt)}</td>
              <td className="text-right">
                <Button variant="secondary" onClick={() => onSelect(exception)}>View {exception.exceptionId}</Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ExceptionDetail({
  exception,
  resolutionNote,
  assignee,
  internalNote,
  onResolutionNoteChange,
  onAssigneeChange,
  onInternalNoteChange,
  onAssign,
  onAddNote,
  onResolve,
  assigning,
  addingNote,
  resolving
}: {
  exception: ReconciliationException;
  resolutionNote: string;
  assignee: string;
  internalNote: string;
  onResolutionNoteChange: (value: string) => void;
  onAssigneeChange: (value: string) => void;
  onInternalNoteChange: (value: string) => void;
  onAssign: () => void;
  onAddNote: () => void;
  onResolve: () => void;
  assigning: boolean;
  addingNote: boolean;
  resolving: boolean;
}) {
  const rows = [
    ["Exception ID", exception.exceptionId],
    ["Run ID", exception.runId],
    ["Fingerprint", exception.fingerprint],
    ["Description", exception.description],
    ["Journal", exception.journalId],
    ["Ledger account", exception.ledgerAccountId],
    ["External account", exception.externalAccountId],
    ["Assigned to", exception.assignedTo],
    ["Currency", exception.currency],
    ["Expected", formatAmount(exception.expectedAmount, exception.currency)],
    ["Actual", formatAmount(exception.actualAmount, exception.currency)],
    ["Delta", formatAmount(exception.deltaAmount, exception.currency)],
    ["Detected", formatDate(exception.detectedAt)],
    ["Resolution note", exception.resolutionNote]
  ];

  return (
    <div className="grid gap-4">
      <div>
        <h3 className="text-base font-semibold">{titleFor(exception)}</h3>
        <div className="mt-2 flex flex-wrap gap-2">
          <Badge tone={toneForSeverity(exception.severity)}>{exception.severity}</Badge>
          <Badge tone={toneForStatus(exception.status)}>{exception.status}</Badge>
        </div>
      </div>
      <dl className="grid gap-2">
        {rows.map(([label, value]) => (
          <div key={label} className="rounded-md border border-line bg-white p-3">
            <dt className="text-xs font-medium uppercase text-muted">{label}</dt>
            <dd className="mt-1 break-words text-sm font-medium text-ink">{value || "n/a"}</dd>
          </div>
        ))}
      </dl>
      {!["RESOLVED", "WAIVED"].includes(exception.status) ? (
        <div className="grid gap-4">
          <div className="grid gap-2">
            <div className="flex gap-2">
              <Input placeholder="Assign to" value={assignee} onChange={(event) => onAssigneeChange(event.target.value)} />
              <Button variant="secondary" onClick={onAssign} disabled={assigning || !assignee.trim()}>
                Assign exception
              </Button>
            </div>
          </div>
          <div className="grid gap-2">
            <textarea
              className="min-h-20 rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
              placeholder="Internal note"
              value={internalNote}
              onChange={(event) => onInternalNoteChange(event.target.value)}
            />
            <Button variant="secondary" onClick={onAddNote} disabled={addingNote || !internalNote.trim()}>
              Add note
            </Button>
          </div>
          {exception.notes?.length ? (
            <div className="grid gap-2">
              {exception.notes.map((note) => (
                <div key={note.noteId} className="rounded-md border border-line bg-white p-3">
                  <p className="text-xs font-medium uppercase text-muted">{note.author}</p>
                  <p className="mt-1 text-sm font-medium">{note.note}</p>
                </div>
              ))}
            </div>
          ) : null}
          <div className="grid gap-2">
            <textarea
              className="min-h-24 rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
              placeholder="Resolution note"
              value={resolutionNote}
              onChange={(event) => onResolutionNoteChange(event.target.value)}
            />
            <Button onClick={onResolve} disabled={resolving || !resolutionNote.trim()}>
              Resolve exception
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function RunTable({ runs }: { runs: ReconciliationRun[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Business date</th>
            <th>Status</th>
            <th>Requested by</th>
            <th>Exceptions</th>
            <th>Started</th>
            <th>Completed</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr key={run.runId} className="border-b border-line last:border-0">
              <td className="py-3 font-medium">{run.businessDate}</td>
              <td><Badge tone={toneForRunStatus(run.status)}>{run.status}</Badge></td>
              <td>{run.requestedBy || "system"}</td>
              <td>{run.totalExceptions} total / {run.criticalExceptions} critical</td>
              <td>{formatDate(run.startedAt)}</td>
              <td>{formatDate(run.completedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function toneForSeverity(severity: string): "neutral" | "good" | "warn" | "bad" | "info" {
  if (severity === "CRITICAL") return "bad";
  if (severity === "HIGH" || severity === "WARNING") return "warn";
  if (severity === "INFO") return "info";
  return "neutral";
}

function toneForStatus(status: string): "neutral" | "good" | "warn" | "bad" | "info" {
  if (status === "RESOLVED" || status === "WAIVED") return "good";
  if (status === "IN_PROGRESS" || status === "ACKNOWLEDGED") return "info";
  if (status === "OPEN") return "warn";
  return "neutral";
}

function toneForRunStatus(status: string): "neutral" | "good" | "warn" | "bad" | "info" {
  if (status === "COMPLETED") return "good";
  if (status === "COMPLETED_WITH_EXCEPTIONS") return "warn";
  if (status === "FAILED") return "bad";
  if (status === "RUNNING") return "info";
  return "neutral";
}

function formatAmount(value: number | undefined, currency?: string) {
  if (value === undefined || value === null) {
    return "n/a";
  }
  return `${value} ${currency || ""}`.trim();
}

function formatDate(value: string | undefined) {
  return value ? new Date(value).toLocaleString() : "n/a";
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : undefined;
}

function titleFor(exception: ReconciliationException) {
  return exception.title || exception.summary || exception.checkCode;
}
