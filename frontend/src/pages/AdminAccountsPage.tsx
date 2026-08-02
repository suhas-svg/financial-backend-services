import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Banknote, CircleX, Lock, Pencil, Unlock } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { closeAccount, createAccount, listAccounts, syntheticFundAccount, updateAccount, updateAccountStatus } from "../lib/queries";
import { accountSchema, type AccountValues } from "../lib/schemas";
import { createIdempotencyKey } from "../lib/idempotency";
import { compactDate, money } from "../lib/format";
import { invalidateInBackground, MONEY_STATE_REFRESH_INTERVAL_MS } from "../lib/queryInvalidation";
import { availableBalance, ledgerBalance } from "../lib/accountBalances";
import type { Account } from "../types";
import { Button, ErrorNotice, Field, Input, PageHeader, Panel, Select } from "../components/ui";
import { StatusBadge } from "../components/StatusBadge";

export function AdminAccountsPage() {
  const queryClient = useQueryClient();
  const [ownerId, setOwnerId] = useState("");
  const [accountType, setAccountType] = useState("");
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState<Account | null>(null);
  const [statusTarget, setStatusTarget] = useState<Account | null>(null);
  const [statusReason, setStatusReason] = useState("");
  const [statusError, setStatusError] = useState("");
  const [accountAction, setAccountAction] = useState<{ kind: "FUND" | "CLOSE"; account: Account } | null>(null);
  const [actionAmount, setActionAmount] = useState("");
  const [actionReason, setActionReason] = useState("");
  const [actionError, setActionError] = useState("");
  const accounts = useQuery({ queryKey: ["admin-accounts", ownerId, accountType, status], queryFn: () => listAccounts({ ownerId, accountType, status: status as "" | "ACTIVE" | "FROZEN" | "CLOSED" }), refetchInterval: MONEY_STATE_REFRESH_INTERVAL_MS });
  const form = useForm<AccountValues>({
    resolver: zodResolver(accountSchema),
    defaultValues: { accountType: "CHECKING", ownerId: "", interestRate: 0 }
  });
  const watchedType = form.watch("accountType");
  const invalidateAccounts = () => invalidateInBackground(queryClient, ["admin-accounts"]);
  const clearAccountAction = () => {
    setAccountAction(null);
    setActionAmount("");
    setActionReason("");
    setActionError("");
  };
  const createMutation = useMutation({
    mutationFn: createAccount,
    onSuccess: () => {
      form.reset({ accountType: "CHECKING", ownerId: "", interestRate: 0 });
      invalidateAccounts();
    }
  });
  const updateMutation = useMutation({
    mutationFn: (values: AccountValues) => updateAccount(editing?.id ?? 0, values),
    onSuccess: () => {
      setEditing(null);
      form.reset({ accountType: "CHECKING", ownerId: "", interestRate: 0 });
      invalidateAccounts();
    }
  });
  const closeMutation = useMutation({
    mutationFn: ({ account, reason }: { account: Account; reason: string }) => closeAccount(account.id, reason),
    onSuccess: () => {
      clearAccountAction();
      invalidateAccounts();
    }
  });
  const fundingMutation = useMutation({
    mutationFn: ({ account, amount, reason }: { account: Account; amount: number; reason: string }) =>
      syntheticFundAccount(account.id, amount, reason, createIdempotencyKey("synthetic-funding")),
    onSuccess: () => {
      clearAccountAction();
      invalidateAccounts();
    }
  });
  const statusMutation = useMutation({
    mutationFn: ({ account, reason }: { account: Account; reason: string }) =>
      updateAccountStatus(account.id, { status: account.status === "FROZEN" ? "ACTIVE" : "FROZEN", reason }),
    onSuccess: () => {
      setStatusTarget(null);
      setStatusReason("");
      setStatusError("");
      invalidateAccounts();
    }
  });

  const startEdit = (account: Account) => {
    setEditing(account);
    form.reset({
      accountType: account.accountType,
      ownerId: account.ownerId,
      interestRate: account.interestRate ?? 0,
      creditLimit: account.creditLimit,
      dueDate: account.dueDate?.slice(0, 10)
    });
  };

  const resetForm = () => {
    setEditing(null);
    form.reset({ accountType: "CHECKING", ownerId: "", interestRate: 0 });
  };

  const confirmStatusUpdate = () => {
    if (!statusTarget) return;
    if (!statusReason.trim()) {
      setStatusError("Status reason is required");
      return;
    }
    statusMutation.mutate({ account: statusTarget, reason: statusReason.trim() });
  };

  const confirmAccountAction = () => {
    if (!accountAction) return;
    const reason = actionReason.trim();
    if (!reason) {
      setActionError("Action reason is required");
      return;
    }
    if (accountAction.kind === "CLOSE") {
      closeMutation.mutate({ account: accountAction.account, reason });
      return;
    }
    const amount = Number(actionAmount);
    if (!Number.isFinite(amount) || amount <= 0) {
      setActionError("Synthetic funding amount must be greater than zero");
      return;
    }
    fundingMutation.mutate({ account: accountAction.account, amount, reason });
  };

  return (
    <div className="admin-page grid gap-6 lg:gap-8">
      <PageHeader eyebrow="Customer operations" title="Account operations" detail="Create, inspect, update, freeze, and manage customer accounts across the platform." />
      <div className="grid gap-6 xl:grid-cols-[420px_1fr]">
      <div className="grid gap-6">
      <Panel title={editing ? `Edit account #${editing.id}` : "Create managed account"}>
        <form className="grid gap-4" onSubmit={form.handleSubmit((values) => (editing ? updateMutation.mutate(values) : createMutation.mutate(values)))}>
          <ErrorNotice message={(createMutation.error instanceof Error ? createMutation.error.message : undefined) ?? (updateMutation.error instanceof Error ? updateMutation.error.message : undefined)} />
          <Field label="Owner ID" error={form.formState.errors.ownerId?.message}>
            <Input {...form.register("ownerId")} />
          </Field>
          <Field label="Type" error={form.formState.errors.accountType?.message}>
            <Select {...form.register("accountType")}>
              <option value="CHECKING">Checking</option>
              <option value="SAVINGS">Savings</option>
              <option value="CREDIT">Credit</option>
            </Select>
          </Field>
          {watchedType === "SAVINGS" ? (
            <Field label="Interest rate" error={form.formState.errors.interestRate?.message}>
              <Input type="number" step="0.01" {...form.register("interestRate")} />
            </Field>
          ) : null}
          {watchedType === "CREDIT" ? (
            <>
              <Field label="Credit limit" error={form.formState.errors.creditLimit?.message}>
                <Input type="number" step="0.01" {...form.register("creditLimit")} />
              </Field>
              <Field label="Due date" error={form.formState.errors.dueDate?.message}>
                <Input type="date" {...form.register("dueDate")} />
              </Field>
            </>
          ) : null}
          <div className="flex gap-2">
            <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
              {editing ? "Update account" : "Create account"}
            </Button>
            {editing ? (
              <Button type="button" variant="secondary" onClick={resetForm}>
                Cancel
              </Button>
            ) : null}
          </div>
        </form>
      </Panel>
      <Panel title={statusTarget ? `${statusTarget.status === "FROZEN" ? "Unfreeze" : "Freeze"} account #${statusTarget.id}` : "Account hold"}>
        {statusTarget ? (
          <div className="grid gap-3">
            <ErrorNotice message={statusError || (statusMutation.error instanceof Error ? statusMutation.error.message : undefined)} />
            <p className="text-sm text-muted">
              Current status <StatusBadge value={statusTarget.status ?? "ACTIVE"} />
            </p>
            <Field label="Status reason">
              <Input value={statusReason} onChange={(event) => {
                setStatusReason(event.target.value);
                setStatusError("");
              }} />
            </Field>
            <div className="flex gap-2">
              <Button type="button" onClick={confirmStatusUpdate} disabled={statusMutation.isPending} aria-label="Confirm status update">
                Confirm status update
              </Button>
              <Button type="button" variant="secondary" onClick={() => setStatusTarget(null)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted">Select Freeze or Unfreeze from the account table.</p>
        )}
      </Panel>
      <Panel title={accountAction ? `${accountAction.kind === "FUND" ? "Synthetic fund" : "Close"} account #${accountAction.account.id}` : "Account action"}>
        {accountAction ? (
          <div className="grid gap-3">
            <ErrorNotice message={actionError || (fundingMutation.error instanceof Error ? fundingMutation.error.message : undefined) || (closeMutation.error instanceof Error ? closeMutation.error.message : undefined)} />
            <p className="text-sm text-muted">
              {accountAction.kind === "FUND" ? "Funding is synthetic-only, ledger-posted, and idempotent." : "Closure requires a zero ledger-authoritative balance and retains account history."}
            </p>
            {accountAction.kind === "FUND" ? (
              <Field label="Synthetic funding amount">
                <Input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={actionAmount}
                  onChange={(event) => { setActionAmount(event.target.value); setActionError(""); }}
                />
              </Field>
            ) : null}
            <Field label="Action reason">
              <Input
                value={actionReason}
                onChange={(event) => { setActionReason(event.target.value); setActionError(""); }}
              />
            </Field>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant={accountAction.kind === "CLOSE" ? "danger" : "primary"} onClick={confirmAccountAction} disabled={fundingMutation.isPending || closeMutation.isPending}>
                {accountAction.kind === "FUND" ? "Confirm synthetic funding" : "Confirm account closure"}
              </Button>
              <Button type="button" variant="secondary" onClick={clearAccountAction} disabled={fundingMutation.isPending || closeMutation.isPending}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted">Select synthetic funding or closure from the account table.</p>
        )}
      </Panel>
      </div>
      <Panel
        title="Admin account oversight"
        action={
          <div className="admin-filter-grid grid grid-cols-3 gap-2">
            <Input placeholder="Owner ID" value={ownerId} onChange={(event) => setOwnerId(event.target.value)} />
            <Select value={accountType} onChange={(event) => setAccountType(event.target.value)}>
              <option value="">All types</option>
              <option value="CHECKING">Checking</option>
              <option value="SAVINGS">Savings</option>
              <option value="CREDIT">Credit</option>
            </Select>
            <Select value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="">All status</option>
              <option value="ACTIVE">Active</option>
              <option value="FROZEN">Frozen</option>
              <option value="CLOSED">Closed</option>
            </Select>
          </div>
        }
      >
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line text-xs uppercase text-muted">
              <tr>
                <th className="py-2">Account</th>
                <th>Owner</th>
                <th>Type</th>
                <th>Status</th>
                <th>Available</th>
                <th>Ledger</th>
                <th>Opened</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {accounts.data?.content.map((account) => (
                <tr key={account.id} className="border-b border-line last:border-0">
                  <td className="py-2 font-medium">#{account.id}</td>
                  <td>{account.ownerId}</td>
                  <td>
                    <StatusBadge value={account.accountType} />
                  </td>
                  <td>
                    <StatusBadge value={account.status ?? "ACTIVE"} />
                  </td>
                  <td>{money(availableBalance(account), account.currency)}</td>
                  <td>{money(ledgerBalance(account), account.currency)}</td>
                  <td>{compactDate(account.createdAt)}</td>
                  <td className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button type="button" variant="ghost" onClick={() => {
                        setStatusTarget(account);
                        setStatusReason("");
                        setStatusError("");
                      }} disabled={account.status === "CLOSED" || statusMutation.isPending} aria-label={`${account.status === "FROZEN" ? "Unfreeze" : "Freeze"} account ${account.id}`}>
                        {account.status === "FROZEN" ? <Unlock className="h-4 w-4" /> : <Lock className="h-4 w-4" />}
                      </Button>
                      <Button type="button" variant="ghost" disabled={account.status === "CLOSED"} onClick={() => startEdit(account)} aria-label={`Edit account ${account.id}`}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button type="button" variant="ghost" disabled={account.status === "CLOSED" || fundingMutation.isPending} onClick={() => {
                        setAccountAction({ kind: "FUND", account });
                        setActionAmount("");
                        setActionReason("");
                        setActionError("");
                      }} aria-label={`Synthetic fund account ${account.id}`}>
                        <Banknote className="h-4 w-4" />
                      </Button>
                      <Button type="button" variant="ghost" disabled={account.status === "CLOSED" || closeMutation.isPending} onClick={() => {
                        setAccountAction({ kind: "CLOSE", account });
                        setActionAmount("");
                        setActionReason("");
                        setActionError("");
                      }} aria-label={`Close account ${account.id}`}>
                        <CircleX className="h-4 w-4" />
                      </Button>                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
      </div>
    </div>
  );
}
