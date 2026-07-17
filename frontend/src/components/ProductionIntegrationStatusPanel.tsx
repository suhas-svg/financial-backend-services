import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "../lib/api";
import { Badge, ErrorNotice, Panel, StatusNotice } from "./ui";

type BoundaryHealth = {
  provider?: string;
  mode?: string;
  configured?: boolean;
  healthy?: boolean;
  policyVersion?: string;
};
type Readiness = {
  productionReady?: boolean;
  riskProvider?: BoundaryHealth;
  fxProvider?: BoundaryHealth;
  operatorIam?: Record<string, boolean>;
  consentVersions?: Array<Record<string, unknown>>;
};

export function ProductionIntegrationStatusPanel() {
  const readiness = useQuery({
    queryKey: ["admin", "production-integration-readiness"],
    queryFn: () => apiRequest<Readiness>("transaction", "/api/admin/outcome-protection/integration-readiness")
  });
  const data = readiness.data;
  const iam = data?.operatorIam && typeof data.operatorIam === "object" ? data.operatorIam : {};
  const versions = Array.isArray(data?.consentVersions) ? data.consentVersions : [];
  const iamReady = Object.keys(iam).length > 0 && Object.values(iam).every(Boolean);
  return <Panel title="Production integration & regulatory readiness">
    <div className="grid gap-4 text-sm">
      <ErrorNotice message={readiness.error instanceof Error ? readiness.error.message : undefined} />
      {readiness.isLoading ? <StatusNotice pending message="Checking fail-closed provider and governance boundaries..." /> : null}
      {data ? <>
        <div className="flex flex-wrap gap-2">
          <Badge tone={data.productionReady ? "good" : "warn"}>{data.productionReady ? "PRODUCTION READY" : "EXTERNAL APPROVAL REQUIRED"}</Badge>
          <Badge tone={iamReady ? "good" : "bad"}>{iamReady ? "IAM EVIDENCE CONFIGURED" : "IAM FAIL CLOSED"}</Badge>
          <Badge tone="info">NO EXECUTABLE FX</Badge>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Boundary title="Fraud / risk" health={data.riskProvider} />
          <Boundary title="Licensed FX forecast" health={data.fxProvider} />
        </div>
        <p className="text-muted">{versions.length} consent version record(s). Legal/compliance approval, jurisdiction eligibility, retention, accessibility, withdrawal, and complaint ownership remain external.</p>
        <p className="text-xs text-muted">Provider ALLOW cannot bypass ownership, limits, MFA, transfer authorization, idempotency, or double-entry posting.</p>
      </> : null}
    </div>
  </Panel>;
}

function Boundary({ title, health }: { title: string; health?: BoundaryHealth }) {
  const ready = Boolean(health?.configured && health?.healthy);
  return <div className="rounded-xl border border-line p-3">
    <p className="font-semibold">{title}</p>
    <p className="mt-1"><Badge tone={ready ? "good" : "bad"}>{ready ? "HEALTHY" : "FAIL CLOSED"}</Badge></p>
    <p className="mt-2 text-xs text-muted">Adapter: {health?.provider ?? health?.mode ?? "unconfigured"}{health?.policyVersion ? ` · policy ${health.policyVersion}` : ""}</p>
  </div>;
}
