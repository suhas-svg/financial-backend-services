import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, ErrorNotice, Field, Input, Panel } from "../components/ui";
import { confirmMfa, disableMfa, enrollMfa, getMfaStatus, regenerateRecoveryCodes } from "../lib/queries";
import type { MfaEnrollment } from "../types";

export function SecurityPage() {
  const queryClient = useQueryClient();
  const status = useQuery({ queryKey: ["mfa-status"], queryFn: getMfaStatus });
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
  const error = [enroll.error, confirm.error, regenerate.error, disable.error].find((value) => value instanceof Error);

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
              <Field label="Current password"><Input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></Field>
              <Button disabled={!password || enroll.isPending} onClick={() => enroll.mutate()}>Set up authenticator</Button>
            </>
          ) : null}
          {enrollment ? (
            <div className="grid gap-4 rounded-md border border-line bg-slate-50 p-4">
              <p className="text-sm">Add this secret to your authenticator app, then enter its 6-digit code.</p>
              <code className="break-all rounded bg-white p-3 text-sm">{enrollment.secret}</code>
              <Field label="Authenticator code"><Input inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={(event) => setCode(event.target.value)} /></Field>
              <Button disabled={code.length !== 6 || confirm.isPending} onClick={() => confirm.mutate()}>Confirm and enable</Button>
            </div>
          ) : null}
          {status.data?.enrolled ? (
            <div className="grid gap-4 border-t border-line pt-4">
              <Field label="Current password"><Input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></Field>
              <Field label="Authenticator or recovery code"><Input autoComplete="one-time-code" value={code} onChange={(event) => setCode(event.target.value)} /></Field>
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" disabled={!password || regenerate.isPending} onClick={() => regenerate.mutate()}>Replace recovery codes</Button>
                <Button variant="danger" disabled={!password || !code || disable.isPending} onClick={() => disable.mutate()}>Disable authenticator</Button>
              </div>
            </div>
          ) : null}
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
