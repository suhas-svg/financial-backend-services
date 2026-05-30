import { useQuery } from "@tanstack/react-query";
import { Badge, EmptyState, Panel } from "../components/ui";
import { compactDate } from "../lib/format";
import { listDisputes } from "../lib/queries";
import type { DisputeStatus, TransactionDispute } from "../types";

export function DisputesPage() {
  const disputes = useQuery({ queryKey: ["disputes"], queryFn: () => listDisputes(0) });

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Disputes</h1>
        <p className="text-sm text-muted">Submitted transaction disputes and resolution outcomes.</p>
      </div>

      <Panel title="Submitted disputes">
        {disputes.error instanceof Error ? <p className="text-sm text-danger">{disputes.error.message}</p> : null}
        {disputes.isLoading ? <p className="text-sm text-muted">Loading disputes...</p> : null}
        {!disputes.isLoading && !disputes.data?.content.length ? <EmptyState title="No disputes found" detail="Eligible completed transactions can be disputed from transaction detail." /> : null}
        {disputes.data?.content.length ? <DisputeTable disputes={disputes.data.content} /> : null}
      </Panel>
    </div>
  );
}

function DisputeTable({ disputes }: { disputes: TransactionDispute[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Dispute</th>
            <th>Transaction</th>
            <th>Status</th>
            <th>Reason</th>
            <th>Created</th>
            <th>Resolution</th>
          </tr>
        </thead>
        <tbody>
          {disputes.map((dispute) => (
            <tr key={dispute.disputeId} className="border-b border-line last:border-0">
              <td className="py-3">
                <p className="font-medium">{dispute.disputeNumber}</p>
                <p className="text-xs text-muted">{dispute.description}</p>
              </td>
              <td className="font-mono text-xs">{dispute.transactionId}</td>
              <td><Badge tone={toneForStatus(dispute.status)}>{dispute.status}</Badge></td>
              <td>{dispute.reasonCode}</td>
              <td>{compactDate(dispute.createdAt)}</td>
              <td>{dispute.resolutionNote || "Pending review"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function toneForStatus(status: DisputeStatus): "neutral" | "good" | "warn" | "bad" | "info" {
  if (status === "OPEN") return "warn";
  if (status === "IN_REVIEW") return "info";
  if (status === "APPROVED") return "good";
  if (status === "DENIED") return "bad";
  return "neutral";
}
