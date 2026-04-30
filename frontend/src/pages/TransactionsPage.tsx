import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { compactDate, money } from "../lib/format";
import { createIdempotencyKey } from "../lib/idempotency";
import { getReversalStatus, getReversals, getTransaction, getTransactions, reverseTransaction, searchTransactions } from "../lib/queries";
import { reversalSchema, type ReversalValues } from "../lib/schemas";
import type { Transaction } from "../types";
import { StatusBadge } from "../components/StatusBadge";
import { Button, EmptyState, ErrorNotice, Field, Input, Panel, Select } from "../components/ui";

export function TransactionsPage() {
  const [filters, setFilters] = useState({ type: "", status: "", accountId: "" });
  const [selected, setSelected] = useState<Transaction | null>(null);
  const transactions = useQuery({
    queryKey: ["transactions", filters],
    queryFn: () => (filters.type || filters.status || filters.accountId ? searchTransactions(filters) : getTransactions(0))
  });
  const detail = useQuery({
    queryKey: ["transaction", selected?.transactionId],
    queryFn: () => getTransaction(selected!.transactionId),
    enabled: Boolean(selected?.transactionId)
  });

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
      <Panel
        title="Transaction history"
        action={
          <div className="grid grid-cols-3 gap-2">
            <Input placeholder="Account" value={filters.accountId} onChange={(event) => setFilters((prev) => ({ ...prev, accountId: event.target.value }))} />
            <Select value={filters.type} onChange={(event) => setFilters((prev) => ({ ...prev, type: event.target.value }))}>
              <option value="">All types</option>
              <option value="DEPOSIT">Deposit</option>
              <option value="WITHDRAWAL">Withdrawal</option>
              <option value="TRANSFER">Transfer</option>
            </Select>
            <Select value={filters.status} onChange={(event) => setFilters((prev) => ({ ...prev, status: event.target.value }))}>
              <option value="">All status</option>
              <option value="COMPLETED">Completed</option>
              <option value="PENDING">Pending</option>
              <option value="FAILED">Failed</option>
            </Select>
          </div>
        }
      >
        {transactions.data?.content.length ? (
          <TransactionTable transactions={transactions.data.content} onSelect={setSelected} />
        ) : (
          <EmptyState title="No transactions found" detail="Adjust filters or create a money movement." />
        )}
      </Panel>
      <Panel title="Transaction detail">
        {detail.data ? <TransactionDetail transaction={detail.data} /> : selected ? <p className="text-sm text-muted">Loading transaction detail...</p> : <EmptyState title="No transaction selected" detail="Select a row to inspect the full transaction." />}
      </Panel>
    </div>
  );
}

export function TransactionTable({ transactions, onSelect }: { transactions: Transaction[]; onSelect?: (transaction: Transaction) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Transaction</th>
            <th>Type</th>
            <th>Status</th>
            <th>From</th>
            <th>To</th>
            <th>Amount</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((transaction) => (
            <tr key={transaction.transactionId} className="cursor-pointer border-b border-line last:border-0 hover:bg-slate-50" onClick={() => onSelect?.(transaction)}>
              <td className="py-2 font-mono text-xs">{transaction.transactionId}</td>
              <td>{transaction.type}</td>
              <td>
                <StatusBadge value={transaction.status} />
              </td>
              <td>{transaction.fromAccountId ?? "-"}</td>
              <td>{transaction.toAccountId ?? "-"}</td>
              <td>{money(transaction.amount, transaction.currency)}</td>
              <td>{compactDate(transaction.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function TransactionDetail({ transaction, allowReverse = false }: { transaction: Transaction; allowReverse?: boolean }) {
  const queryClient = useQueryClient();
  const reversalStatus = useQuery({
    queryKey: ["transaction", transaction.transactionId, "reversed"],
    queryFn: () => getReversalStatus(transaction.transactionId),
    enabled: allowReverse
  });
  const reversals = useQuery({
    queryKey: ["transaction", transaction.transactionId, "reversals"],
    queryFn: () => getReversals(transaction.transactionId),
    enabled: allowReverse
  });
  const form = useForm<ReversalValues>({ resolver: zodResolver(reversalSchema), defaultValues: { reason: "", reference: "" } });
  const mutation = useMutation({
    mutationFn: (values: ReversalValues) => reverseTransaction(transaction.transactionId, values, createIdempotencyKey("reverse")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["transaction", transaction.transactionId] });
      form.reset({ reason: "", reference: "" });
    }
  });
  return (
    <div className="grid gap-4 text-sm">
      <dl className="grid grid-cols-2 gap-3">
        <Info label="ID" value={transaction.transactionId} />
        <Info label="Type" value={transaction.type} />
        <Info label="Status" value={<StatusBadge value={transaction.status} />} />
        <Info label="Amount" value={money(transaction.amount, transaction.currency)} />
        <Info label="From" value={transaction.fromAccountId ?? "-"} />
        <Info label="To" value={transaction.toAccountId ?? "-"} />
        <Info label="Created" value={compactDate(transaction.createdAt)} />
        <Info label="Processed" value={compactDate(transaction.processedAt)} />
      </dl>
      <p className="text-muted">{transaction.description || "No description"}</p>
      {allowReverse ? (
        <>
          <div className="grid gap-3 border-t border-line pt-4">
            <Info label="Reversed" value={reversalStatus.data?.isReversed ? "Yes" : "No"} />
            <Info label="Reversal records" value={String(reversals.data?.length ?? 0)} />
            {reversals.data?.length ? (
              <div className="rounded-md border border-line">
                {reversals.data.map((reversal) => (
                  <div key={reversal.transactionId} className="border-b border-line p-2 last:border-0">
                    <p className="font-mono text-xs">{reversal.transactionId}</p>
                    <p className="text-xs text-muted">{money(reversal.amount, reversal.currency)} · {reversal.status}</p>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
          <form className="grid gap-3 border-t border-line pt-4" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
            <ErrorNotice message={mutation.error instanceof Error ? mutation.error.message : undefined} />
            <Field label="Reversal reason" error={form.formState.errors.reason?.message}>
              <Input {...form.register("reason")} />
            </Field>
            <Field label="Reference" error={form.formState.errors.reference?.message}>
              <Input {...form.register("reference")} />
            </Field>
            <Button type="submit" disabled={mutation.isPending}>Reverse transaction</Button>
          </form>
        </>
      ) : null}
    </div>
  );
}

function Info({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase text-muted">{label}</dt>
      <dd className="mt-1 break-all font-medium">{value}</dd>
    </div>
  );
}
