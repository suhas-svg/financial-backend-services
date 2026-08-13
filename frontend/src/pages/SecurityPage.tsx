import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, ErrorNotice, Field, Input, Panel } from "../components/ui";
import { confirmMfa, disableMfa, enrollMfa, getMfaStatus, regenerateRecoveryCodes, getSpendingLimits, updateSpendingLimits } from "../lib/queries";
import type { MfaEnrollment } from "../types";

export function SecurityPage() {
  const queryClient = useQueryClient();
  const status = useQuery({ queryKey: ["mfa-status"], queryFn: getMfaStatus });
  const limits = useQuery({ queryKey: ["spending-limits"], queryFn: getSpendingLimits });
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [enrollment, setEnrollment] = useState<MfaEnrollment>();
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["mfa-status"] });
  const enroll = useMutation({ mutationFn: () => enrollMfa(password), onSuccess: setEnrollment });
  const confirm = useMutation({
    mutationFn: () => confirmMfa(code),
    onSuccess: (result) => { setRecoveryCodes(result.recoveryCodes); setEnrollment(undefined); setPassword(""); setCode(""); refresh(); }
  });
  const regenerate = useMutation({ mutationFn: () => regenerateRecoveryCodes(password), onSuccess: (result) => { setRecoveryCodes(result.recoveryCodes); setPassword(""); refresh(); } });
  const disable = useMutation({ mutationFn: () => disableMfa(password, code), onSuccess: () => { setPassword(""); setCode(""); setRecoveryCodes([]); refresh(); } });
  const [limitDrafts, setLimitDrafts] = useState<Record<number, { transfer: string; withdrawal: string; credential: string }>>({});
  const updateLimit = useMutation({ mutationFn: ({ accountId, transfer, withdrawal, credential }: { accountId: number; transfer: string; withdrawal: string; credential: string }) => updateSpendingLimits(accountId, { transferDailyLimit: Number(transfer), withdrawalDailyLimit: Number(withdrawal), credential: credential || undefined }), onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["spending-limits"] }); setLimitDrafts({}); } });
  const error = [enroll.error, confirm.error, regenerate.error, disable.error, updateLimit.error].find((value) => value instanceof Error);

  return (
    <div className="grid max-w-3xl gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Security</h1>
        <p className="mt-1 text-sm text-muted">Protect high-risk transfers with an authenticator app.</p>
      </div>
      <Panel title="Authenticator app">
        <div className="grid gap-4">
          <ErrorNotice message={error instanceof Error ? error.message : undefined} />
          <p className="text-sm">Status: <strong>{status.data?.enrolled ? "Enabled" : "Not enabled"}</strong>{status.data?.enrolled ? ` · ${status.data.recoveryCodesRemaining} recovery codes remaining` : ""}</p>
          {!status.data?.enrolled && !enrollment ? (
            <>
              <Field label="Current password"><Input name="mfa-enrollment-password" autoComplete="new-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></Field>
              <Button disabled={!password || enroll.isPending} onClick={() => enroll.mutate()}>Set up authenticator</Button>
            </>
          ) : null}
          {enrollment ? (
            <div className="grid gap-5 rounded-2xl border border-emerald-200/70 bg-emerald-50/70 p-4 shadow-sm dark:border-emerald-900/70 dark:bg-[#091713] sm:p-5">
              <div>
                <p className="text-sm font-semibold text-ink dark:text-emerald-50">Connect your authenticator</p>
                <p className="mt-1 text-sm leading-6 text-muted">Add the setup key below to your authenticator app, then enter the generated 6-digit code.</p>
              </div>
              <div className="grid gap-2">
                <span className="text-xs font-semibold uppercase tracking-[.12em] text-emerald-800 dark:text-emerald-300">Manual setup key</span>
                <code className="break-all rounded-xl border border-emerald-200 bg-white/80 p-3 font-mono text-sm leading-6 text-emerald-950 shadow-inner dark:border-emerald-900 dark:bg-[#06110e] dark:text-emerald-200">{enrollment.secret}</code>
              </div>
              <Field label="Authenticator code"><Input className="h-12 text-center font-mono text-lg tracking-[.3em]" name="mfa-enrollment-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))} /></Field>
              <Button className="h-12" disabled={code.length !== 6 || confirm.isPending} onClick={() => confirm.mutate()}>{confirm.isPending ? "Verifying..." : "Confirm and enable"}</Button>
            </div>
          ) : null}
          {status.data?.enrolled ? (
            <div className="grid gap-4 border-t border-line pt-4">
              <Field label="Current password"><Input name="mfa-management-password" autoComplete="new-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></Field>
              <Field label="Authenticator or recovery code"><Input name="mfa-management-code" autoComplete="one-time-code" value={code} onChange={(event) => setCode(event.target.value)} /></Field>
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" disabled={!password || regenerate.isPending} onClick={() => regenerate.mutate()}>Replace recovery codes</Button>
                <Button variant="danger" disabled={!password || !code || disable.isPending} onClick={() => disable.mutate()}>Disable authenticator</Button>
              </div>
            </div>
          ) : null}
        </div>
      </Panel>
      <Panel title="Transfer and withdrawal limits">
        <div className="grid gap-4">
          <p className="text-sm text-muted">Reductions apply immediately. Increases require an authenticator or recovery code and take effect after a 24-hour cooling period.</p>
          {(Array.isArray(limits.data) ? limits.data : []).map((limit) => {
            const draft = limitDrafts[limit.accountId] ?? { transfer: String(limit.transferDailyLimit), withdrawal: String(limit.withdrawalDailyLimit), credential: "" };
            return <div key={limit.accountId} className="grid gap-3 rounded-md border border-line p-4">
              <div className="font-medium">Account #{limit.accountId} ({limit.currency})</div>
              <p className="text-xs text-muted">Used today: transfers {limit.transferUsedToday.toFixed(2)} · withdrawals {limit.withdrawalUsedToday.toFixed(2)}</p>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Daily transfer limit"><Input type="number" min="0" step="0.01" value={draft.transfer} onChange={(e) => setLimitDrafts((all) => ({ ...all, [limit.accountId]: { ...draft, transfer: e.target.value } }))} /></Field>
                <Field label="Daily withdrawal limit"><Input type="number" min="0" step="0.01" value={draft.withdrawal} onChange={(e) => setLimitDrafts((all) => ({ ...all, [limit.accountId]: { ...draft, withdrawal: e.target.value } }))} /></Field>
              </div>
              <Field label="Authenticator or recovery code (for increases)"><Input name={`spending-limit-credential-${limit.accountId}`} autoComplete="one-time-code" value={draft.credential} onChange={(e) => setLimitDrafts((all) => ({ ...all, [limit.accountId]: { ...draft, credential: e.target.value } }))} /></Field>
              {limit.pendingEffectiveAt ? <p className="text-sm text-amber-700">Verified increase pending until {new Date(limit.pendingEffectiveAt).toLocaleString()}.</p> : null}
              <Button disabled={!draft.transfer || !draft.withdrawal || updateLimit.isPending} onClick={() => updateLimit.mutate({ accountId: limit.accountId, ...draft })}>Save limits</Button>
            </div>;
          })}
          {!limits.isLoading && !limits.data?.length ? <p className="text-sm text-muted">Create an account before configuring spending limits.</p> : null}
        </div>
      </Panel>
      {recoveryCodes.length ? (
        <Panel title="Save your recovery codes">
          <p className="mb-3 text-sm text-muted">Each code works once. Store these offline; they will not be shown again.</p>
          <div className="grid grid-cols-2 gap-2 font-mono text-sm">{recoveryCodes.map((item) => <code key={item} className="rounded bg-slate-100 p-2">{item}</code>)}</div>
        </Panel>
      ) : null}
    </div>
  );
}
