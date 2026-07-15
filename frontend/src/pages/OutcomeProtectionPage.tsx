import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, FlaskConical, Plus, RefreshCw, ShieldCheck, Trash2 } from "lucide-react";
import type { OutcomeAssumptionType, OutcomeScenario, OutcomeScenarioRequest, OutcomeShockType } from "../types";
import { acceptOutcomeGuardrail, createOutcomeScenario, getOutcomeScenario, listLedgerAccounts, listOutcomeScenarios, refreshOutcomeScenario } from "../lib/queries";
import { createIdempotencyKey } from "../lib/idempotency";
import { compactDate, money } from "../lib/format";
import { Badge, Button, EmptyState, ErrorNotice, Field, Input, PageHeader, Panel, Select, StatusNotice } from "../components/ui";

type CashflowDraft = {
  id: string;
  type: Exclude<OutcomeAssumptionType, "OTHER">;
  label: string;
  date: string;
  amount: string;
  flexible: boolean;
  critical: boolean;
  shockType: OutcomeShockType;
  shockValue: string;
};

const localDate = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
};

const addDays = (value: string, days: number) => {
  const date = new Date(`${value}T12:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
};

const newCashflow = (start: string, type: CashflowDraft["type"] = "EXPENSE"): CashflowDraft => ({
  id: crypto.randomUUID(),
  type,
  label: "",
  date: addDays(start, type === "INCOME" ? 3 : 7),
  amount: "",
  flexible: type === "EXPENSE",
  critical: type === "INCOME",
  shockType: type === "INCOME" ? "INCOME_DELAY" : "EXPENSE_SPIKE",
  shockValue: type === "INCOME" ? "5" : "100"
});

export function OutcomeProtectionPage() {
  const queryClient = useQueryClient();
  const [name, setName] = useState("30-day Balance Shield");
  const [accountIds, setAccountIds] = useState<string[]>([]);
  const [currency, setCurrency] = useState("USD");
  const [horizonStart, setHorizonStart] = useState(localDate());
  const [horizonDays, setHorizonDays] = useState("30");
  const [protectedMinimum, setProtectedMinimum] = useState("10000");
  const [cashflows, setCashflows] = useState<CashflowDraft[]>(() => [newCashflow(localDate(), "INCOME"), newCashflow(localDate(), "EXPENSE")]);
  const [selectedScenarioId, setSelectedScenarioId] = useState<string>();
  const [confirmedGuardrails, setConfirmedGuardrails] = useState<Record<string, boolean>>({});
  const [formError, setFormError] = useState<string>();

  const ledger = useQuery({ queryKey: ["ledger-accounts"], queryFn: listLedgerAccounts });
  const scenarios = useQuery({ queryKey: ["outcome-scenarios"], queryFn: listOutcomeScenarios });
  const detail = useQuery({
    queryKey: ["outcome-scenario", selectedScenarioId],
    queryFn: () => getOutcomeScenario(selectedScenarioId!),
    enabled: Boolean(selectedScenarioId)
  });

  useEffect(() => {
    if (!selectedScenarioId && scenarios.data?.[0]) setSelectedScenarioId(scenarios.data[0].scenarioId);
  }, [scenarios.data, selectedScenarioId]);

  const create = useMutation({
    mutationFn: (request: OutcomeScenarioRequest) => createOutcomeScenario(request, createIdempotencyKey("outcome-scenario")),
    onSuccess: (scenario) => {
      setSelectedScenarioId(scenario.scenarioId);
      queryClient.setQueryData(["outcome-scenario", scenario.scenarioId], scenario);
      queryClient.invalidateQueries({ queryKey: ["outcome-scenarios"] });
    }
  });
  const refresh = useMutation({
    mutationFn: refreshOutcomeScenario,
    onSuccess: (_result, scenarioId) => queryClient.invalidateQueries({ queryKey: ["outcome-scenario", scenarioId] })
  });
  const accept = useMutation({
    mutationFn: (guardrailId: string) => acceptOutcomeGuardrail(guardrailId, createIdempotencyKey("guardrail-accept")),
    onSuccess: (_guardrail) => queryClient.invalidateQueries({ queryKey: ["outcome-scenario", selectedScenarioId] })
  });

  const selectedCurrency = useMemo(() => {
    const selected = ledger.data?.find((account) => accountIds.includes(account.externalAccountId));
    return selected?.currency ?? currency;
  }, [ledger.data, accountIds, currency]);

  const updateCashflow = (id: string, patch: Partial<CashflowDraft>) => {
    setCashflows((current) => current.map((item) => item.id === id ? { ...item, ...patch } : item));
  };

  const toggleAccount = (accountId: string, accountCurrency: string) => {
    setAccountIds((current) => current.includes(accountId)
      ? current.filter((id) => id !== accountId)
      : [...current, accountId]);
    if (!accountIds.length) setCurrency(accountCurrency);
  };

  const submit = () => {
    setFormError(undefined);
    const days = Number(horizonDays);
    const minimum = Number(protectedMinimum);
    if (!name.trim()) return setFormError("Name the protected outcome.");
    if (!accountIds.length) return setFormError("Select at least one authoritative ledger account.");
    if (!Number.isFinite(days) || days < 1 || days > 90) return setFormError("Horizon must be between 1 and 90 days.");
    if (!Number.isFinite(minimum) || minimum < 0) return setFormError("Protected minimum must be zero or positive.");
    if (cashflows.some((item) => !item.label.trim() || !item.date || Number(item.amount) <= 0 || Number(item.shockValue) <= 0)) {
      return setFormError("Every assumption needs a label, date, positive amount, and positive shock bound.");
    }
    const end = addDays(horizonStart, days - 1);
    if (cashflows.some((item) => item.date < horizonStart || item.date > end)) return setFormError("Assumption dates must be inside the horizon.");

    const request: OutcomeScenarioRequest = {
      name: name.trim(),
      accountIds,
      currency: selectedCurrency,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
      horizonStart,
      horizonDays: days,
      protectedMinimum: minimum,
      assumptions: cashflows.map((item) => ({
        id: item.id,
        date: item.date,
        amount: item.type === "EXPENSE" ? -Number(item.amount) : Number(item.amount),
        type: item.type,
        label: item.label.trim(),
        flexible: item.flexible,
        critical: item.critical
      })),
      shocks: cashflows.map((item) => ({
        id: `shock-${item.id}`,
        type: item.shockType,
        targetAssumptionId: item.id,
        days: item.shockType === "INCOME_DELAY" || item.shockType === "PAYMENT_TIMING_SHIFT" ? Number(item.shockValue) : undefined,
        amount: item.shockType === "EXPENSE_SPIKE" ? Number(item.shockValue) : undefined,
        percentage: item.shockType === "INCOME_REDUCTION" ? Number(item.shockValue) : undefined,
        label: shockLabel(item)
      }))
    };
    create.mutate(request);
  };

  const scenario = detail.data;
  const error = formError || errorMessage(create.error) || errorMessage(detail.error) || errorMessage(accept.error) || errorMessage(refresh.error);

  return <div className="grid gap-6">
    <PageHeader eyebrow="Outcome Protection" title="Balance Shield lab" detail="Protect a minimum available balance, reverse-stress what could break it, and compile preview-only guardrails with exact causal proof." />
    <ErrorNotice message={error} />
    {create.isPending ? <StatusNotice pending message="Snapshotting authoritative balances and searching bounded shock combinations..." /> : null}
    {refresh.data ? <StatusNotice message={refresh.data.protectionAtRisk ? "Fresh ledger and schedule state puts this outcome at risk." : "Fresh ledger and schedule state still protects this outcome."} /> : null}

    <div className="grid gap-6 xl:grid-cols-[420px_1fr]">
      <div className="grid content-start gap-6">
        <Panel title="1. Define the protected outcome">
          <div className="grid gap-4">
            <Field label="Outcome name"><Input value={name} onChange={(event) => setName(event.target.value)} /></Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Keep at least"><Input type="number" min="0" step="0.01" value={protectedMinimum} onChange={(event) => setProtectedMinimum(event.target.value)} /></Field>
              <Field label="Horizon days"><Input type="number" min="1" max="90" value={horizonDays} onChange={(event) => setHorizonDays(event.target.value)} /></Field>
            </div>
            <Field label="Start date"><Input type="date" value={horizonStart} onChange={(event) => setHorizonStart(event.target.value)} /></Field>
            <div className="grid gap-2">
              <p className="text-sm font-medium text-ink">Protected accounts</p>
              {ledger.isLoading ? <p className="text-sm text-muted">Loading authoritative balances...</p> : null}
              {ledger.data?.map((account) => {
                const incompatible = accountIds.length > 0 && account.currency !== selectedCurrency && !accountIds.includes(account.externalAccountId);
                return <label key={account.externalAccountId} className="flex items-center justify-between rounded-xl border border-line p-3 text-sm">
                  <span className="flex items-center gap-2"><input type="checkbox" checked={accountIds.includes(account.externalAccountId)} disabled={incompatible} onChange={() => toggleAccount(account.externalAccountId, account.currency)} />Account {account.externalAccountId}</span>
                  <span className="font-semibold">{money(account.availableBalance, account.currency)}</span>
                </label>;
              })}
              {!ledger.isLoading && !ledger.data?.length ? <EmptyState title="No authoritative ledger accounts" detail="Create and fund an account before protecting an outcome." /> : null}
              <p className="text-xs text-muted">Accounts must share one currency. Starting availability is read from transaction-service ledger projections.</p>
            </div>
          </div>
        </Panel>

        <Panel title="2. Add assumptions and plausible shocks">
          <div className="grid gap-4">
            {cashflows.map((item, index) => <div key={item.id} className="grid gap-3 rounded-xl border border-line p-3">
              <div className="flex items-center justify-between"><span className="text-xs font-bold uppercase tracking-wide text-muted">Assumption {index + 1}</span><Button variant="ghost" onClick={() => setCashflows((current) => current.filter((flow) => flow.id !== item.id))} aria-label={`Remove assumption ${index + 1}`}><Trash2 className="h-4 w-4" /></Button></div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Type"><Select value={item.type} onChange={(event) => {
                  const type = event.target.value as CashflowDraft["type"];
                  updateCashflow(item.id, { type, shockType: type === "INCOME" ? "INCOME_DELAY" : "EXPENSE_SPIKE", flexible: type === "EXPENSE", critical: type === "INCOME" });
                }}><option value="INCOME">Income</option><option value="EXPENSE">Expense</option></Select></Field>
                <Field label="Date"><Input type="date" value={item.date} onChange={(event) => updateCashflow(item.id, { date: event.target.value })} /></Field>
              </div>
              <Field label="Label"><Input placeholder={item.type === "INCOME" ? "Salary" : "Rent or critical bill"} value={item.label} onChange={(event) => updateCashflow(item.id, { label: event.target.value })} /></Field>
              <Field label={`Amount (${selectedCurrency})`}><Input type="number" min="0.01" step="0.01" value={item.amount} onChange={(event) => updateCashflow(item.id, { amount: event.target.value })} /></Field>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Shock"><Select value={item.shockType} onChange={(event) => updateCashflow(item.id, { shockType: event.target.value as OutcomeShockType })}>
                  {item.type === "INCOME" ? <><option value="INCOME_DELAY">Income delay</option><option value="INCOME_REDUCTION">Income reduction</option></> : <><option value="EXPENSE_SPIKE">Expense spike</option><option value="PAYMENT_TIMING_SHIFT">Payment earlier</option></>}
                </Select></Field>
                <Field label={shockUnit(item.shockType)}><Input type="number" min="0.01" step="0.01" value={item.shockValue} onChange={(event) => updateCashflow(item.id, { shockValue: event.target.value })} /></Field>
              </div>
              <div className="flex flex-wrap gap-4 text-xs"><label className="flex items-center gap-2"><input type="checkbox" checked={item.flexible} onChange={(event) => updateCashflow(item.id, { flexible: event.target.checked })} />Flexible</label><label className="flex items-center gap-2"><input type="checkbox" checked={item.critical} onChange={(event) => updateCashflow(item.id, { critical: event.target.checked })} />Critical</label></div>
            </div>)}
            <div className="flex flex-wrap gap-2"><Button variant="secondary" onClick={() => setCashflows((current) => [...current, newCashflow(horizonStart, "INCOME")])}><Plus className="h-4 w-4" />Income</Button><Button variant="secondary" onClick={() => setCashflows((current) => [...current, newCashflow(horizonStart, "EXPENSE")])}><Plus className="h-4 w-4" />Expense</Button></div>
            <Button onClick={submit} disabled={create.isPending || !ledger.data?.length}><FlaskConical className="h-4 w-4" />Run reverse-stress lab</Button>
            <p className="text-xs text-muted">This is a deterministic preview. It cannot move funds, change schedules, or contact external payment rails.</p>
          </div>
        </Panel>

        <Panel title="Saved outcomes">
          <div className="grid gap-2">
            {scenarios.data?.map((item) => <button key={item.scenarioId} className={`rounded-xl border p-3 text-left ${selectedScenarioId === item.scenarioId ? "border-brand bg-emerald-50 dark:bg-emerald-950" : "border-line"}`} onClick={() => setSelectedScenarioId(item.scenarioId)}>
              <span className="flex items-center justify-between gap-2"><strong className="text-sm">{item.name}</strong><Badge tone={item.baselineSafe ? "good" : "bad"}>{item.baselineSafe ? "Protected" : "At risk"}</Badge></span>
              <span className="mt-1 block text-xs text-muted">Version {item.version} · {item.horizonDays} days · floor {money(item.protectedMinimum, item.currency)}</span>
            </button>)}
            {!scenarios.isLoading && !scenarios.data?.length ? <EmptyState title="No saved outcomes" detail="Run the lab to save an immutable scenario and proof." /> : null}
          </div>
        </Panel>
      </div>

      <div className="grid content-start gap-6">
        {scenario ? <ScenarioProof scenario={scenario} onRefresh={() => refresh.mutate(scenario.scenarioId)} refreshing={refresh.isPending} confirmed={confirmedGuardrails} setConfirmed={setConfirmedGuardrails} onAccept={(id) => accept.mutate(id)} accepting={accept.isPending} /> : <Panel><EmptyState title="No outcome selected" detail="Run a scenario or select a saved outcome to inspect its causal proof and guardrail drafts." /></Panel>}
      </div>
    </div>
  </div>;
}

function ScenarioProof({ scenario, onRefresh, refreshing, confirmed, setConfirmed, onAccept, accepting }: {
  scenario: OutcomeScenario;
  onRefresh: () => void;
  refreshing: boolean;
  confirmed: Record<string, boolean>;
  setConfirmed: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  onAccept: (id: string) => void;
  accepting: boolean;
}) {
  const failure = scenario.simulation.reverseStress;
  const proofTimeline = failure.failureFound ? failure.timeline : scenario.simulation.baseline.timeline;
  const visibleDays = proofTimeline.filter((day) => day.events.length || day.date === failure.failureDate || day.closingBalance === (failure.lowestBalance ?? scenario.simulation.baseline.lowestBalance));
  return <>
    <Panel title="Protection proof" action={<Button variant="secondary" onClick={onRefresh} disabled={refreshing}><RefreshCw className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`} />Check current state</Button>}>
      <div className="grid gap-5">
        <div className={`rounded-2xl border p-5 ${scenario.simulation.baseline.safe ? "border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950" : "border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950"}`}>
          <div className="flex items-start gap-3">{scenario.simulation.baseline.safe ? <CheckCircle2 className="mt-0.5 h-6 w-6 text-emerald-700" /> : <AlertTriangle className="mt-0.5 h-6 w-6 text-red-700" />}<div><p className="font-bold">{scenario.simulation.baseline.safe ? "Baseline outcome protected" : "Baseline outcome already at risk"}</p><p className="mt-1 text-sm">Starts at {money(scenario.sourceSnapshot.startingAvailableBalance, scenario.currency)}; lowest baseline balance is {money(scenario.simulation.baseline.lowestBalance, scenario.currency)} against a {money(scenario.protectedMinimum, scenario.currency)} floor.</p></div></div>
        </div>
        <div className="grid gap-3 sm:grid-cols-3"><ProofStat label="Failure date" value={failure.failureDate ? compactDate(failure.failureDate) : "No bounded failure"} /><ProofStat label="Lowest stressed balance" value={failure.lowestBalance === undefined ? "—" : money(failure.lowestBalance, scenario.currency)} /><ProofStat label="Search proof" value={`${scenario.simulation.evaluatedCombinations} combinations${scenario.simulation.searchCapped ? " (capped)" : ""}`} /></div>
        <p className="text-sm leading-6 text-muted">{failure.minimalityExplanation}</p>
        {failure.appliedShocks.length ? <div><p className="text-xs font-bold uppercase tracking-wide text-muted">Smallest plausible failure set</p><div className="mt-2 grid gap-2">{failure.appliedShocks.map((shock) => <div key={shock.shockId} className="rounded-xl border border-line p-3 text-sm"><strong>{shock.label}</strong><span className="ml-2 text-xs text-muted">severity {money(shock.severityScore, scenario.currency)}</span></div>)}</div></div> : null}
      </div>
    </Panel>

    <Panel title="Causal timeline">
      <div className="overflow-x-auto"><table className="w-full min-w-[680px] text-left text-sm"><thead className="border-b border-line text-xs uppercase text-muted"><tr><th className="py-2">Date</th><th>Opening</th><th>Triggering events</th><th className="text-right">Closing</th></tr></thead><tbody>{visibleDays.map((day) => <tr key={day.date} className={`border-b border-line last:border-0 ${day.date === failure.failureDate ? "bg-red-50 dark:bg-red-950/40" : ""}`}><td className="py-3 font-medium">{compactDate(day.date)}</td><td>{money(day.openingBalance, scenario.currency)}</td><td>{day.events.length ? <div className="grid gap-1">{day.events.map((event) => <span key={event.eventId}><Badge tone={event.source === "SCHEDULED_TRANSFER" ? "info" : event.amount >= 0 ? "good" : "warn"}>{event.source === "SCHEDULED_TRANSFER" ? "Scheduled" : "Assumption"}</Badge> <span className="ml-1">{event.label}: {money(event.amount, scenario.currency)}</span>{event.critical ? <span className="ml-1 text-xs text-muted">critical</span> : null}</span>)}</div> : "No event"}</td><td className="text-right font-semibold">{money(day.closingBalance, scenario.currency)}</td></tr>)}</tbody></table></div>
    </Panel>

    <Panel title="Authoritative source snapshot">
      <div className="grid gap-3 sm:grid-cols-2"><div><p className="text-xs font-bold uppercase tracking-wide text-muted">Ledger balances</p>{scenario.sourceSnapshot.ledgerAccounts.map((account) => <p key={account.accountId} className="mt-2 text-sm">Account {account.accountId}: <strong>{money(account.availableBalance, account.currency)}</strong> <span className="text-xs text-muted">projection v{account.projectionVersion}</span></p>)}</div><div><p className="text-xs font-bold uppercase tracking-wide text-muted">Known scheduled cashflows</p>{scenario.sourceSnapshot.scheduledCashflows.length ? scenario.sourceSnapshot.scheduledCashflows.map((flow) => <p key={flow.eventId} className="mt-2 text-sm">{compactDate(flow.date)} · {flow.label} · <strong>{money(flow.amount, scenario.currency)}</strong></p>) : <p className="mt-2 text-sm text-muted">No active scheduled transfer affects the selected accounts inside this horizon.</p>}</div></div>
    </Panel>

    <Panel title="Compiled repair and guardrail drafts">
      <div className="grid gap-4">
        <div className="rounded-xl border border-line p-4"><p className="flex items-center gap-2 font-semibold"><ShieldCheck className="h-5 w-5 text-brand" />Smallest verified repair set</p><p className="mt-2 text-sm text-muted">{scenario.simulation.repair.minimalityExplanation}</p>{scenario.simulation.repair.selectedRepairs.map((repair) => <p key={repair.actionId} className="mt-2 text-sm"><strong>{repair.type.split("_").join(" ")} · {money(repair.amount, scenario.currency)}</strong> — {repair.explanation}</p>)}</div>
        {scenario.guardrails.map((guardrail) => <div key={guardrail.guardrailId} className="rounded-xl border border-line p-4"><div className="flex flex-wrap items-start justify-between gap-2"><div><p className="font-semibold">{guardrail.type.split("_").join(" ")}</p><p className="mt-1 text-xs text-muted">Scope: accounts {guardrail.accountIds.join(", ")} · Expires {compactDate(guardrail.expiresAt)} · Threshold {money(guardrail.thresholdAmount, guardrail.currency)}</p></div><Badge tone={guardrail.status === "ACCEPTED" ? "good" : guardrail.status === "EXPIRED" ? "bad" : "info"}>{guardrail.status}</Badge></div><p className="mt-3 text-sm leading-6">{guardrail.previewText}</p>{guardrail.status === "DRAFT" ? <div className="mt-4 grid gap-3"><label className="flex items-start gap-2 text-sm"><input className="mt-1" type="checkbox" checked={Boolean(confirmed[guardrail.guardrailId])} onChange={(event) => setConfirmed((current) => ({ ...current, [guardrail.guardrailId]: event.target.checked }))} /><span>I confirm this is a consent record for a read-only preview. It will not hold, transfer, or schedule money.</span></label><Button variant="secondary" disabled={!confirmed[guardrail.guardrailId] || accepting} onClick={() => onAccept(guardrail.guardrailId)}>Accept preview draft</Button></div> : null}</div>)}
      </div>
    </Panel>
  </>;
}

function ProofStat({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl border border-line bg-white p-3 dark:bg-slate-950"><p className="text-xs font-bold uppercase tracking-wide text-muted">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div>;
}

function shockUnit(type: OutcomeShockType) {
  if (type === "INCOME_DELAY" || type === "PAYMENT_TIMING_SHIFT") return "Days";
  if (type === "INCOME_REDUCTION") return "Reduction %";
  return "Spike amount";
}

function shockLabel(item: CashflowDraft) {
  if (item.shockType === "INCOME_DELAY") return `${item.label} arrives ${item.shockValue} days late`;
  if (item.shockType === "INCOME_REDUCTION") return `${item.label} is reduced by ${item.shockValue}%`;
  if (item.shockType === "PAYMENT_TIMING_SHIFT") return `${item.label} is charged ${item.shockValue} days earlier`;
  return `${item.label} increases by ${item.shockValue}`;
}

function errorMessage(error: unknown) { return error instanceof Error ? error.message : undefined; }
