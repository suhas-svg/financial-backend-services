import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getAdminOutcomeGuardrailControl,
  listAdminOutcomeGuardrailControlEvents,
  listAdminOutcomeGuardrailPolicies,
  updateAdminOutcomeGuardrailControl
} from "../lib/queries";
import { createIdempotencyKey } from "../lib/idempotency";
import { Badge, Button, ErrorNotice, Field, Input, Panel, StatusNotice } from "./ui";
import { ProductionIntegrationStatusPanel } from "./ProductionIntegrationStatusPanel";

export function AdminGuardrailControlPanel() {
  const queryClient = useQueryClient();
  const control = useQuery({ queryKey: ["admin", "guardrail-control"], queryFn: getAdminOutcomeGuardrailControl });
  const policies = useQuery({ queryKey: ["admin", "guardrail-policies"], queryFn: listAdminOutcomeGuardrailPolicies });
  const events = useQuery({ queryKey: ["admin", "guardrail-control-events"], queryFn: listAdminOutcomeGuardrailControlEvents });
  const [reason, setReason] = useState("Operator reviewed emergency execution posture");
  const [confirmed, setConfirmed] = useState(false);
  const update = useMutation({
    mutationFn: (enabled: boolean) => updateAdminOutcomeGuardrailControl(enabled, reason, createIdempotencyKey("guardrail-control")),
    onSuccess: () => {
      setConfirmed(false);
      queryClient.invalidateQueries({ queryKey: ["admin", "guardrail-control"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "guardrail-control-events"] });
    }
  });
  const error = [control.error, policies.error, events.error, update.error].find(Boolean);
  const enabled = control.data?.executionEnabled ?? false;
  const policyRows = Array.isArray(policies.data) ? policies.data : [];
  const controlEvents = Array.isArray(events.data) ? events.data : [];
  const active = policyRows.filter((item) => item.policy.effectiveStatus === "ACTIVE").length;
  const suspended = policyRows.filter((item) => item.policy.effectiveStatus === "SUSPENDED").length;

  return <Panel title="Balance Shield execution control">
    <div className="grid gap-4">
      <ProductionIntegrationStatusPanel />
      <ErrorNotice message={error instanceof Error ? error.message : undefined} />
      {control.isLoading ? <StatusNotice pending message="Loading fail-closed execution control..." /> : null}
      {control.data ? <div className="grid gap-2 text-sm sm:grid-cols-2">
        <p><strong>Global state:</strong> <Badge tone={enabled ? "good" : "bad"}>{enabled ? "ENABLED" : "KILL SWITCH ACTIVE"}</Badge></p>
        <p><strong>Changed by:</strong> {control.data.changedBy}</p>
        <p className="sm:col-span-2"><strong>Reason:</strong> {control.data.reason}</p>
        <p><strong>Visible policies:</strong> {policyRows.length}</p>
        <p><strong>Effective states:</strong> {active} active · {suspended} suspended</p>
      </div> : null}
      <Field label="Required operator reason"><Input value={reason} onChange={(event) => setReason(event.target.value)} /></Field>
      <label className="flex items-start gap-2 text-sm"><input className="mt-1" type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /><span>I understand this global control immediately blocks or permits only customer-confirmed actions; it never initiates a transfer.</span></label>
      <div className="flex flex-wrap gap-2">
        <Button disabled={!confirmed || !reason.trim() || enabled || update.isPending} onClick={() => update.mutate(true)}>Enable explicit actions</Button>
        <Button variant="secondary" disabled={!confirmed || !reason.trim() || !enabled || update.isPending} onClick={() => update.mutate(false)}>Activate emergency kill switch</Button>
      </div>
      <div className="rounded-lg border border-line p-3 text-xs text-muted">
        <p className="font-semibold text-ink">Latest immutable control evidence</p>
        {controlEvents.slice(0, 5).map((event) => <p key={event.eventId} className="mt-2">{new Date(event.createdAt).toLocaleString()} · {event.executionEnabled ? "ENABLED" : "DISABLED"} · {event.actor} · {event.reason}</p>)}
        {!events.isLoading && !controlEvents.length ? <p className="mt-2">No operator change has been recorded; migration default remains disabled.</p> : null}
      </div>
    </div>
  </Panel>;
}
