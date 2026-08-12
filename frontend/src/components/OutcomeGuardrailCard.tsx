import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import type { LedgerAccountProjection, OutcomeGuardrail, OutcomeGuardrailExecution, OutcomeGuardrailPolicy } from "../types";
import {
  activateOutcomeGuardrail,
  authorizeOutcomeGuardrailExecution,
  cancelOutcomeGuardrailExecution,
  consentOutcomeGuardrail,
  executeOutcomeGuardrail,
  getOutcomeGuardrailControl,
  getOutcomeGuardrailTerms,
  resumeOutcomeGuardrail,
  revokeOutcomeGuardrail,
  suspendOutcomeGuardrail,
  verifyStepUpChallenge
} from "../lib/queries";
import { createIdempotencyKey } from "../lib/idempotency";
import { compactDate, money } from "../lib/format";
import { isScenarioDivergedError } from "../lib/outcomeFreshness";
import { Badge, Button, ErrorNotice, Field, Input, Select, StatusNotice } from "./ui";

type Props = {
  guardrail: OutcomeGuardrail;
  accounts: LedgerAccountProjection[];
  previewConfirmed: boolean;
  onPreviewConfirmed: (confirmed: boolean) => void;
  onAcceptPreview: () => void;
  acceptingPreview: boolean;
  onChanged: () => void;
};

const localInput = (value: string) => {
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
};

export function OutcomeGuardrailCard({
  guardrail, accounts, previewConfirmed, onPreviewConfirmed, onAcceptPreview, acceptingPreview, onChanged
}: Props) {
  const terms = useQuery({ queryKey: ["outcome-guardrail-terms"], queryFn: getOutcomeGuardrailTerms });
  const control = useQuery({ queryKey: ["outcome-guardrail-control"], queryFn: getOutcomeGuardrailControl });
  const fundingOptions = accounts.filter((account) => account.currency === guardrail.currency && !guardrail.accountIds.includes(account.externalAccountId));
  const protectedOptions = accounts.filter((account) => account.currency === guardrail.currency && guardrail.accountIds.includes(account.externalAccountId));
  const [fundingAccountId, setFundingAccountId] = useState("");
  const [protectedAccountId, setProtectedAccountId] = useState(protectedOptions[0]?.externalAccountId ?? "");
  const [maxActionAmount, setMaxActionAmount] = useState(String(guardrail.thresholdAmount));
  const [totalLimit, setTotalLimit] = useState(String(guardrail.thresholdAmount));
  const [maxExecutions, setMaxExecutions] = useState("1");
  const [expiresAt, setExpiresAt] = useState(localInput(guardrail.expiresAt));
  const [consentConfirmed, setConsentConfirmed] = useState(false);
  const [activationCredential, setActivationCredential] = useState("");
  const [executionAmount, setExecutionAmount] = useState("");
  const [executionConfirmed, setExecutionConfirmed] = useState(false);
  const [executionCredential, setExecutionCredential] = useState("");
  const [lifecycleReason, setLifecycleReason] = useState("Customer requested this change");
  const [policyOverride, setPolicyOverride] = useState<OutcomeGuardrailPolicy>();
  const [execution, setExecution] = useState<OutcomeGuardrailExecution>();
  const policy = policyOverride ?? guardrail.policy;

  const consent = useMutation({
    mutationFn: () => {
      if (!terms.data) throw new Error("Current terms are unavailable");
      return consentOutcomeGuardrail(guardrail.guardrailId, {
        confirmed: true,
        termsVersion: terms.data.version,
        termsHash: terms.data.hash,
        fundingAccountId,
        protectedAccountId,
        maxActionAmount: Number(maxActionAmount),
        totalLimit: Number(totalLimit),
        maxExecutions: Number(maxExecutions),
        expiresAt: new Date(expiresAt).toISOString()
      }, createIdempotencyKey("guardrail-consent"));
    },
    onSuccess: (result) => { setPolicyOverride(result); onChanged(); }
  });
  const activate = useMutation({
    mutationFn: async () => {
      if (!policy?.activationChallengeId) throw new Error("Activation challenge is missing");
      const verified = await verifyStepUpChallenge(policy.activationChallengeId, activationCredential);
      return activateOutcomeGuardrail(guardrail.guardrailId, verified.proof);
    },
    onSuccess: (result) => { setPolicyOverride(result); setActivationCredential(""); onChanged(); }
  });
  const execute = useMutation({
    mutationFn: () => executeOutcomeGuardrail(guardrail.guardrailId, Number(executionAmount), createIdempotencyKey("guardrail-execution")),
    onSuccess: (result) => { setExecution(result); setExecutionConfirmed(false); onChanged(); }
  });
  const authorize = useMutation({
    mutationFn: async () => {
      if (!execution?.authorizationChallengeId) throw new Error("Transfer authorization challenge is missing");
      const verified = await verifyStepUpChallenge(execution.authorizationChallengeId, executionCredential);
      return authorizeOutcomeGuardrailExecution(execution.executionId, verified.proof, createIdempotencyKey("guardrail-execution-authorize"));
    },
    onSuccess: (result) => { setExecution(result); setExecutionCredential(""); onChanged(); }
  });
  const cancelExecution = useMutation({
    mutationFn: () => cancelOutcomeGuardrailExecution(execution!.executionId, createIdempotencyKey("guardrail-execution-cancel")),
    onSuccess: (result) => { setExecution(result); onChanged(); }
  });
  const suspend = useMutation({
    mutationFn: () => suspendOutcomeGuardrail(guardrail.guardrailId, lifecycleReason, createIdempotencyKey("guardrail-suspend")),
    onSuccess: (result) => { setPolicyOverride(result); onChanged(); }
  });
  const resume = useMutation({
    mutationFn: () => resumeOutcomeGuardrail(guardrail.guardrailId, lifecycleReason, createIdempotencyKey("guardrail-resume")),
    onSuccess: (result) => { setPolicyOverride(result); onChanged(); }
  });
  const revoke = useMutation({
    mutationFn: () => revokeOutcomeGuardrail(guardrail.guardrailId, lifecycleReason, createIdempotencyKey("guardrail-revoke")),
    onSuccess: (result) => { setPolicyOverride(result); onChanged(); }
  });

  const error = [terms.error, control.error, consent.error, activate.error, execute.error, authorize.error,
    cancelExecution.error, suspend.error, resume.error, revoke.error]
    .find(Boolean);
  const scenarioDiverged = isScenarioDivergedError(error);
  const state = policy?.effectiveStatus ?? guardrail.status;
  const stateTone = state === "ACTIVE" ? "good" : state === "REVOKED" || state === "EXPIRED" ? "bad" : state === "SUSPENDED" ? "warn" : "info";

  return <div className="rounded-xl border border-line p-4">
    <div className="flex flex-wrap items-start justify-between gap-2">
      <div>
        <p className="font-semibold">{guardrail.type.split("_").join(" ")}</p>
        <p className="mt-1 text-xs text-muted">Scope: accounts {guardrail.accountIds.join(", ")} · Expires {compactDate(guardrail.expiresAt)} · Threshold {money(guardrail.thresholdAmount, guardrail.currency)}</p>
      </div>
      <Badge tone={stateTone}>{state}</Badge>
    </div>
    <p className="mt-3 text-sm leading-6">{guardrail.previewText}</p>
    {scenarioDiverged
      ? <StatusNotice message="Authoritative state changed. Refresh or re-run the scenario, then select and consent to a fresh repair." />
      : <ErrorNotice message={error instanceof Error ? error.message : undefined} />}

    {guardrail.type !== "RESERVE_BUFFER" && guardrail.status === "DRAFT" ? <div className="mt-4 grid gap-3">
      <label className="flex items-start gap-2 text-sm"><input className="mt-1" type="checkbox" checked={previewConfirmed} onChange={(event) => onPreviewConfirmed(event.target.checked)} /><span>I accept this read-only preview. It cannot hold, schedule, or move money.</span></label>
      <Button variant="secondary" disabled={!previewConfirmed || acceptingPreview} onClick={onAcceptPreview}>Accept preview draft</Button>
    </div> : null}

    {guardrail.type === "RESERVE_BUFFER" && !policy ? <div className="mt-4 grid gap-4 rounded-xl border border-blue-200 bg-blue-50 p-4 dark:border-blue-900 dark:bg-blue-950/40">
      <div><p className="font-semibold">Create an executable top-up policy</p><p className="mt-1 text-sm">This records consent and requests MFA activation. It does not move money, and no background execution exists.</p></div>
      {terms.data ? <div className="rounded-lg border border-line bg-white p-3 text-sm dark:bg-slate-950"><p className="font-semibold">{terms.data.title} · version {terms.data.version}</p><p className="mt-2 text-muted">{terms.data.summary}</p><ul className="mt-2 list-disc space-y-1 pl-5">{terms.data.confirmations.map((item) => <li key={item}>{item}</li>)}</ul></div> : <StatusNotice pending message="Loading current consent terms..." />}
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Funding account (outside protected scope)"><Select value={fundingAccountId} onChange={(event) => setFundingAccountId(event.target.value)}><option value="">Select funding account</option>{fundingOptions.map((account) => <option key={account.externalAccountId} value={account.externalAccountId}>{account.externalAccountId} · available {money(account.availableBalance, account.currency)}</option>)}</Select></Field>
        <Field label="Protected account"><Select value={protectedAccountId} onChange={(event) => setProtectedAccountId(event.target.value)}><option value="">Select protected account</option>{protectedOptions.map((account) => <option key={account.externalAccountId} value={account.externalAccountId}>{account.externalAccountId} · available {money(account.availableBalance, account.currency)}</option>)}</Select></Field>
        <Field label="Maximum per action"><Input type="number" min="0.01" step="0.01" value={maxActionAmount} onChange={(event) => setMaxActionAmount(event.target.value)} /></Field>
        <Field label="Total policy limit"><Input type="number" min="0.01" step="0.01" value={totalLimit} onChange={(event) => setTotalLimit(event.target.value)} /></Field>
        <Field label="Maximum executions"><Input type="number" min="1" max="100" value={maxExecutions} onChange={(event) => setMaxExecutions(event.target.value)} /></Field>
        <Field label="Policy expiry"><Input type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} /></Field>
      </div>
      <label className="flex items-start gap-2 text-sm"><input className="mt-1" type="checkbox" checked={consentConfirmed} onChange={(event) => setConsentConfirmed(event.target.checked)} /><span>I reviewed the exact terms, accounts, limits, expiry, and understand that every transfer still requires my explicit action.</span></label>
      <Button disabled={!terms.data || !fundingAccountId || !protectedAccountId || !consentConfirmed || consent.isPending} onClick={() => consent.mutate()}>{consent.isPending ? "Recording consent..." : "Record consent and request MFA"}</Button>
      {control.data && !control.data.executionEnabled ? <p className="text-xs text-amber-800 dark:text-amber-300">Operator kill switch is currently off: {control.data.reason}. Consent may be recorded, but execution remains suspended.</p> : null}
    </div> : null}

    {policy ? <div className="mt-4 grid gap-4 rounded-xl border border-line p-4">
      <div className="grid gap-2 text-sm sm:grid-cols-2"><p><strong>Funding:</strong> {policy.fundingAccountId}</p><p><strong>Protected:</strong> {policy.protectedAccountId}</p><p><strong>Per action:</strong> {money(policy.maxActionAmount, policy.currency)}</p><p><strong>Remaining total:</strong> {money(policy.totalLimit - policy.totalExecuted - policy.totalReserved, policy.currency)}</p><p><strong>Completed:</strong> {policy.executionCount}/{policy.maxExecutions}</p><p><strong>Terms:</strong> {policy.termsVersion}</p></div>
      {policy.notificationDelivery ? <p className="text-xs text-muted">Notification evidence {policy.notificationDelivery.deliveryId}: {policy.notificationDelivery.state} after {policy.notificationDelivery.attemptCount} attempt(s).</p> : null}
      {policy.requiresReconsent ? <StatusNotice message="Terms changed. This policy is suspended and cannot execute without new informed consent." /> : null}
      {policy.status === "CONSENT_PENDING" ? <div className="grid gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 dark:border-amber-900 dark:bg-amber-950/40"><p className="text-sm">Consent is recorded, but activation is pending. No money has moved.</p><Field label="Authenticator or recovery code"><Input autoComplete="one-time-code" value={activationCredential} onChange={(event) => setActivationCredential(event.target.value)} /></Field><Button disabled={!activationCredential || activate.isPending} onClick={() => activate.mutate()}>{activate.isPending ? "Verifying..." : "Verify MFA and activate"}</Button></div> : null}
      {policy.status === "ACTIVE" && policy.effectiveStatus === "SUSPENDED" ? <StatusNotice message={"Execution is suspended: " + policy.executionControlReason} /> : null}
      {policy.status === "ACTIVE" && policy.effectiveStatus === "ACTIVE" ? <div className="grid gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 dark:border-emerald-900 dark:bg-emerald-950/40"><p className="text-sm">Enter an amount no larger than the current threshold deficit. The existing transfer service will recheck ownership, available balance, limits, risk, and ledger state.</p><Field label="Top-up amount"><Input type="number" min="0.01" step="0.01" value={executionAmount} onChange={(event) => setExecutionAmount(event.target.value)} /></Field><label className="flex items-start gap-2 text-sm"><input className="mt-1" type="checkbox" checked={executionConfirmed} onChange={(event) => setExecutionConfirmed(event.target.checked)} /><span>I explicitly authorize this one top-up request. This is not background or recurring authorization.</span></label><Button disabled={!executionConfirmed || Number(executionAmount) <= 0 || execute.isPending} onClick={() => execute.mutate()}>{execute.isPending ? "Submitting through transfer controls..." : "Confirm top-up"}</Button></div> : null}
      {execution?.status === "AWAITING_AUTHORIZATION" ? <div className="grid gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 dark:border-amber-900 dark:bg-amber-950/40"><p className="text-sm">Risk-based authorization is pending. No money has moved; policy capacity is reserved against concurrent requests.</p><Field label="Authenticator or recovery code"><Input autoComplete="one-time-code" value={executionCredential} onChange={(event) => setExecutionCredential(event.target.value)} /></Field><div className="flex gap-2"><Button disabled={!executionCredential || authorize.isPending} onClick={() => authorize.mutate()}>Verify and complete top-up</Button><Button variant="secondary" disabled={cancelExecution.isPending} onClick={() => cancelExecution.mutate()}>Cancel pending action</Button></div></div> : null}
      {execution?.status === "COMPLETED" ? <StatusNotice message={"Top-up completed as transaction " + execution.transactionId + ". Notification evidence is " + (execution.notificationDelivery?.state ?? "pending") + "."} /> : null}
      {execution?.status === "FAILED" || execution?.status === "CANCELLED" ? <StatusNotice message={"Action " + execution.status.toLowerCase() + ". " + (execution.lastError ?? "No transfer completed.")} /> : null}
      {policy.status !== "REVOKED" && policy.status !== "EXPIRED" && policy.status !== "CONSENT_PENDING" ? <div className="grid gap-3"><Field label="Reason for lifecycle change"><Input value={lifecycleReason} onChange={(event) => setLifecycleReason(event.target.value)} /></Field><div className="flex flex-wrap gap-2">{policy.status === "ACTIVE" ? <Button variant="secondary" disabled={suspend.isPending} onClick={() => suspend.mutate()}>Suspend</Button> : null}{policy.status === "SUSPENDED" ? <Button variant="secondary" disabled={resume.isPending} onClick={() => resume.mutate()}>Resume</Button> : null}<Button variant="secondary" disabled={revoke.isPending} onClick={() => revoke.mutate()}>Revoke permanently</Button></div></div> : null}
      {policy.status === "REVOKED" ? <p className="text-sm">Revoked {policy.revokedAt ? compactDate(policy.revokedAt) : ""}: {policy.revocationReason}. Future actions are blocked; completed transfers were not reversed.</p> : null}
    </div> : null}
  </div>;
}
