import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "../lib/api";
import { createIdempotencyKey } from "../lib/idempotency";
import { Badge, Button, ErrorNotice, Panel, StatusNotice } from "./ui";

type Governance = {
  versions?: Array<Record<string, unknown>>;
  evidence?: Array<Record<string, unknown>>;
  legalApprovalClaimed?: boolean;
};

export function ConsentGovernanceReadinessPanel() {
  const queryClient = useQueryClient();
  const governance = useQuery({
    queryKey: ["outcome-protection", "consent-governance"],
    queryFn: () => apiRequest<Governance>("transaction", "/api/outcome-protection/consent-governance")
  });
  const exportEvidence = useMutation({
    mutationFn: () => apiRequest<Record<string, unknown>>("transaction", "/api/outcome-protection/consent-governance/export", {
      method: "POST",
      body: { jurisdiction: Intl.DateTimeFormat().resolvedOptions().locale, detail: "Customer requested evidence export" },
      idempotencyKey: createIdempotencyKey("consent-evidence-export")
    }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["outcome-protection", "consent-governance"] })
  });
  const data = governance.data;
  const versions = Array.isArray(data?.versions) ? data.versions : [];
  const evidence = Array.isArray(data?.evidence) ? data.evidence : [];
  const error = governance.error ?? exportEvidence.error;
  return <Panel title="Consent and evidence governance">
    <div className="grid gap-3 text-sm">
      <ErrorNotice message={error instanceof Error ? error.message : undefined} />
      {governance.isLoading ? <StatusNotice pending message="Loading your consent-version evidence..." /> : null}
      {data ? <>
        <div className="flex flex-wrap gap-2">
          <Badge tone="info">{versions.length} VERSION RECORD(S)</Badge>
          <Badge tone={data.legalApprovalClaimed ? "bad" : "good"}>NO LEGAL APPROVAL CLAIM</Badge>
          <Badge tone="info">{evidence.length} EVIDENCE EVENT(S)</Badge>
        </div>
        <p className="text-muted">You can export the evidence held for this feature. Withdrawal and complaint events are immutable records; they do not erase or reverse completed transfers.</p>
        <Button variant="secondary" disabled={exportEvidence.isPending} onClick={() => exportEvidence.mutate()}>
          {exportEvidence.isPending ? "Recording export..." : "Record evidence export"}
        </Button>
      </> : null}
    </div>
  </Panel>;
}
