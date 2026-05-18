import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Lock, Pencil, Trash2, Unlock } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { createAccount, deleteAccount, listAccounts, updateAccount, updateAccountStatus } from "../lib/queries";
import { accountSchema, type AccountValues } from "../lib/schemas";
import { compactDate, money } from "../lib/format";
import type { Account } from "../types";
import { Button, ErrorNotice, Field, Input, Panel, Select } from "../components/ui";
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
  const accounts = useQuery({ queryKey: ["admin-accounts", ownerId, accountType, status], queryFn: () => listAccounts({ ownerId, accountType, status: status as "" | "ACTIVE" | "FROZEN" }) });
  const form = useForm<AccountValues>({
    resolver: zodResolver(accountSchema),
    defaultValues: { accountType: "CHECKING", balance: 0, ownerId: "", interestRate: 0 }
  });
  const watchedType = form.watch("accountType");
  const invalidateAccounts = () => queryClient.invalidateQueries({ queryKey: ["admin-accounts"] });
  const createMutation = useMutation({
    mutationFn: createAccount,
    onSuccess: () => {
      form.reset({ accountType: "CHECKING", balance: 0, ownerId: "", interestRate: 0 });
      invalidateAccounts();
    }
  });
  const updateMutation = useMutation({
    mutationFn: (values: AccountValues) => updateAccount(editing?.id ?? 0, values),
    onSuccess: () => {
      setEditing(null);
      form.reset({ accountType: "CHECKING", balance: 0, ownerId: "", interestRate: 0 });
      invalidateAccounts();
    }
  });
  const deleteMutation = useMutation({ mutationFn: deleteAccount, onSuccess: invalidateAccounts });
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
      balance: account.balance,
      ownerId: account.ownerId,
      interestRate: account.interestRate ?? 0,
      creditLimit: account.creditLimit,
      dueDate: account.dueDate?.slice(0, 10)
    });
  };

  const resetForm = () => {
    setEditing(null);
    form.reset({ accountType: "CHECKING", balance: 0, ownerId: "", interestRate: 0 });
  };

  const confirmStatusUpdate = () => {
    if (!statusTarget) return;
    if (!statusReason.trim()) {
      setStatusError("Status reason is required");
      return;
    }
    statusMutation.mutate({ account: statusTarget, reason: statusReason.trim() });
  };

  return (
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
          <Field label="Balance" error={form.formState.errors.balance?.message}>
            <Input type="number" step="0.01" {...form.register("balance")} />
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
      </div>
      <Panel
        title="Admin account oversight"
        action={
          <div className="grid grid-cols-3 gap-2">
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
                <th>Balance</th>
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
                  <td>{money(account.balance)}</td>
                  <td>{compactDate(account.createdAt)}</td>
                  <td className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button type="button" variant="ghost" onClick={() => {
                        setStatusTarget(account);
                        setStatusReason("");
                        setStatusError("");
                      }} aria-label={`${account.status === "FROZEN" ? "Unfreeze" : "Freeze"} account ${account.id}`}>
                        {account.status === "FROZEN" ? <Unlock className="h-4 w-4" /> : <Lock className="h-4 w-4" />}
                      </Button>
                      <Button type="button" variant="ghost" onClick={() => startEdit(account)} aria-label={`Edit account ${account.id}`}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button type="button" variant="ghost" onClick={() => deleteMutation.mutate(account.id)} disabled={deleteMutation.isPending} aria-label={`Delete account ${account.id}`}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
    </div>
  );
}
