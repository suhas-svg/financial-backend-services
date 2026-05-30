import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Dispatch, SetStateAction } from "react";
import { useState } from "react";
import { Badge, Button, EmptyState, Input, Panel, Select, Stat } from "../components/ui";
import { addDisputeNote, claimDispute, getDisputeSummary, searchAdminDisputes, updateDisputeStatus } from "../lib/queries";
import type { DisputeReasonCode, DisputeStatus, TransactionDispute } from "../types";

const defaultFilters = {
  status: "",
  reasonCode: "",
  assignedTo: "",
  userId: "",
  transactionId: "",
  from: "",
  to: ""
};

export function AdminDisputesPage() {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState(defaultFilters);
  const [selected, setSelected] = useState<TransactionDispute | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");
  const [internalNote, setInternalNote] = useState("");

  const disputes = useQuery({ queryKey: ["admin-disputes", filters], queryFn: () => searchAdminDisputes(filters) });
  const summary = useQuery({ queryKey: ["dispute-summary", filters.from, filters.to], queryFn: () => getDisputeSummary({ from: filters.from, to: filters.to }) });

  const claimMutation = useMutation({
    mutationFn: (dispute: TransactionDispute) => claimDispute(dispute.disputeId),
    onSuccess: (updated) => {
      setSelected(updated);
      queryClient.invalidateQueries({ queryKey: ["admin-disputes"] });
      queryClient.invalidateQueries({ queryKey: ["dispute-summary"] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: ({ dispute, status }: { dispute: TransactionDispute; status: DisputeStatus }) =>
      updateDisputeStatus(dispute.disputeId, { status, resolutionNote }),
    onSuccess: (updated) => {
      setSelected(updated);
      setResolutionNote(updated.resolutionNote || "");
      queryClient.invalidateQueries({ queryKey: ["admin-disputes"] });
      queryClient.invalidateQueries({ queryKey: ["dispute-summary"] });
    }
  });

  const noteMutation = useMutation({
    mutationFn: (dispute: TransactionDispute) => addDisputeNote(dispute.disputeId, { note: internalNote }),
    onSuccess: (updated) => {
      setSelected(updated);
      setInternalNote("");
      queryClient.invalidateQueries({ queryKey: ["admin-disputes"] });
    }
  });

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Disputes</h1>
        <p className="text-sm text-muted">Admin queue for customer transaction dispute review.</p>
      </div>

      <div className="grid gap-3 md:grid-cols-5">
        <Stat label="Total disputes" value={formatNumber(summary.data?.totalDisputes)} />
        <Stat label="Open" value={<Badge tone={summary.data?.openDisputes ? "warn" : "good"}>{formatNumber(summary.data?.openDisputes)}</Badge>} />
        <Stat label="In review" value={formatNumber(summary.data?.inReviewDisputes)} />
        <Stat label="Approved" value={<Badge tone="good">{formatNumber(summary.data?.approvedDisputes)}</Badge>} />
        <Stat label="Unassigned" value={<Badge tone={summary.data?.unassignedDisputes ? "warn" : "neutral"}>{formatNumber(summary.data?.unassignedDisputes)}</Badge>} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_430px]">
        <Panel
          title="Dispute queue"
          action={
            <div className="grid w-full max-w-5xl grid-cols-7 gap-2">
              <Select value={filters.status} onChange={(event) => updateFilter(setFilters, "status", event.target.value)}>
                <option value="">All status</option>
                <option value="OPEN">Open</option>
                <option value="IN_REVIEW">In review</option>
                <option value="APPROVED">Approved</option>
                <option value="DENIED">Denied</option>
                <option value="CLOSED">Closed</option>
              </Select>
              <Select value={filters.reasonCode} onChange={(event) => updateFilter(setFilters, "reasonCode", event.target.value)}>
                <option value="">All reasons</option>
                <option value="UNAUTHORIZED">Unauthorized</option>
                <option value="DUPLICATE">Duplicate</option>
                <option value="INCORRECT_AMOUNT">Incorrect amount</option>
                <option value="SERVICE_NOT_RECEIVED">Service not received</option>
                <option value="OTHER">Other</option>
              </Select>
              <Input placeholder="Assigned to" value={filters.assignedTo} onChange={(event) => updateFilter(setFilters, "assignedTo", event.target.value)} />
              <Input placeholder="User ID" value={filters.userId} onChange={(event) => updateFilter(setFilters, "userId", event.target.value)} />
              <Input placeholder="Transaction ID" value={filters.transactionId} onChange={(event) => updateFilter(setFilters, "transactionId", event.target.value)} />
              <Input type="datetime-local" aria-label="From" value={filters.from} onChange={(event) => updateFilter(setFilters, "from", event.target.value)} />
              <Input type="datetime-local" aria-label="To" value={filters.to} onChange={(event) => updateFilter(setFilters, "to", event.target.value)} />
            </div>
          }
        >
          {disputes.error instanceof Error ? <p className="text-sm text-danger">{disputes.error.message}</p> : null}
          {disputes.isLoading ? <p className="text-sm text-muted">Loading disputes...</p> : null}
          {!disputes.isLoading && !disputes.data?.content.length ? <EmptyState title="No disputes found" detail="Adjust filters to inspect another dispute queue window." /> : null}
          {disputes.data?.content.length ? <DisputeTable disputes={disputes.data.content} onSelect={(dispute) => {
            setSelected(dispute);
            setResolutionNote(dispute.resolutionNote || "");
            setInternalNote("");
          }} /> : null}
        </Panel>

        <Panel title="Dispute detail" action={selected ? <Button variant="secondary" onClick={() => setSelected(null)}>Clear</Button> : null}>
          {selected ? (
            <DisputeDetail
              dispute={selected}
              resolutionNote={resolutionNote}
              internalNote={internalNote}
              onResolutionNoteChange={setResolutionNote}
              onInternalNoteChange={setInternalNote}
              onClaim={() => claimMutation.mutate(selected)}
              onStatusChange={(status) => statusMutation.mutate({ dispute: selected, status })}
              onAddNote={() => noteMutation.mutate(selected)}
              isUpdating={claimMutation.isPending || statusMutation.isPending || noteMutation.isPending}
            />
          ) : (
            <EmptyState title="No dispute selected" detail="Select a dispute to inspect the transaction context, claim it, and add notes." />
          )}
        </Panel>
      </div>
    </div>
  );
}

function DisputeTable({ disputes, onSelect }: { disputes: TransactionDispute[]; onSelect: (dispute: TransactionDispute) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Dispute</th>
            <th>Status</th>
            <th>Reason</th>
            <th>User</th>
            <th>Transaction</th>
            <th>Assigned</th>
            <th>Created</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {disputes.map((dispute) => (
            <tr key={dispute.disputeId} className="border-b border-line last:border-0">
              <td className="py-3">
                <p className="font-medium">{dispute.disputeNumber}</p>
                <p className="text-xs text-muted">{dispute.description}</p>
              </td>
              <td><Badge tone={toneForStatus(dispute.status)}>{dispute.status}</Badge></td>
              <td>{labelForReason(dispute.reasonCode)}</td>
              <td>{dispute.userId}</td>
              <td className="font-mono text-xs">{dispute.transactionId}</td>
              <td>{dispute.assignedTo || "Unassigned"}</td>
              <td>{formatDate(dispute.createdAt)}</td>
              <td className="text-right">
                <Button variant="secondary" onClick={() => onSelect(dispute)}>View {dispute.disputeNumber}</Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DisputeDetail({
  dispute,
  resolutionNote,
  internalNote,
  onResolutionNoteChange,
  onInternalNoteChange,
  onClaim,
  onStatusChange,
  onAddNote,
  isUpdating
}: {
  dispute: TransactionDispute;
  resolutionNote: string;
  internalNote: string;
  onResolutionNoteChange: (value: string) => void;
  onInternalNoteChange: (value: string) => void;
  onClaim: () => void;
  onStatusChange: (status: DisputeStatus) => void;
  onAddNote: () => void;
  isUpdating: boolean;
}) {
  const rows = [
    ["Dispute number", dispute.disputeNumber],
    ["Status", dispute.status],
    ["Reason", labelForReason(dispute.reasonCode)],
    ["User", dispute.userId],
    ["Transaction", dispute.transactionId],
    ["Assigned to", dispute.assignedTo || "Unassigned"],
    ["Created by", dispute.createdBy],
    ["Created", formatDate(dispute.createdAt)],
    ["Claimed", dispute.claimedAt ? formatDate(dispute.claimedAt) : undefined],
    ["Closed", dispute.closedAt ? formatDate(dispute.closedAt) : undefined]
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

      <div className="rounded-md border border-line bg-white p-3">
        <p className="text-xs font-medium uppercase text-muted">Customer explanation</p>
        <p className="mt-1 text-sm">{dispute.description}</p>
      </div>

      {!dispute.assignedTo ? <Button variant="secondary" disabled={isUpdating} onClick={onClaim}>Claim dispute</Button> : null}

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
        {dispute.notes?.length ? dispute.notes.map((note) => (
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
      <div className="grid gap-2 sm:grid-cols-2">
        <Button variant="secondary" disabled={isUpdating} onClick={() => onStatusChange("IN_REVIEW")}>Mark in review</Button>
        <Button variant="secondary" disabled={isUpdating} onClick={() => onStatusChange("APPROVED")}>Approve dispute</Button>
        <Button variant="secondary" disabled={isUpdating} onClick={() => onStatusChange("DENIED")}>Deny dispute</Button>
        <Button variant="danger" disabled={isUpdating} onClick={() => onStatusChange("CLOSED")}>Close dispute</Button>
      </div>
    </div>
  );
}

function updateFilter(setFilters: Dispatch<SetStateAction<typeof defaultFilters>>, key: keyof typeof defaultFilters, value: string) {
  setFilters((prev) => ({ ...prev, [key]: value }));
}

function toneForStatus(status: DisputeStatus): "neutral" | "good" | "warn" | "bad" | "info" {
  if (status === "OPEN") return "warn";
  if (status === "IN_REVIEW") return "info";
  if (status === "APPROVED") return "good";
  if (status === "DENIED") return "bad";
  return "neutral";
}

function labelForReason(reason: DisputeReasonCode) {
  return reason.replace(/_/g, " ").toLowerCase().replace(/^\w|\s\w/g, (match: string) => match.toUpperCase());
}

function formatNumber(value: number | undefined) {
  return String(value ?? 0);
}

function formatDate(value: string | undefined) {
  return value ? new Date(value).toLocaleString() : "n/a";
}
