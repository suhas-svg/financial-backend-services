import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { searchTransactions } from "../lib/queries";
import type { Transaction } from "../types";
import { Button, EmptyState, Input, Panel, Select } from "../components/ui";
import { TransactionDetail, TransactionTable } from "./TransactionsPage";

export function AdminTransactionsPage() {
  const [filters, setFilters] = useState({ accountId: "", status: "", type: "", reference: "" });
  const [selected, setSelected] = useState<Transaction | null>(null);
  const transactions = useQuery({ queryKey: ["admin-transactions", filters], queryFn: () => searchTransactions(filters) });

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
      <Panel
        title="Operations transaction search"
        action={
          <div className="grid grid-cols-4 gap-2">
            <Input placeholder="Account" value={filters.accountId} onChange={(event) => setFilters((prev) => ({ ...prev, accountId: event.target.value }))} />
            <Input placeholder="Reference" value={filters.reference} onChange={(event) => setFilters((prev) => ({ ...prev, reference: event.target.value }))} />
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
        {transactions.data?.content.length ? <TransactionTable transactions={transactions.data.content} onSelect={setSelected} /> : <EmptyState title="No transactions found" detail="Use filters to locate a transaction for review or reversal." />}
      </Panel>
      <Panel
        title="Operations action"
        action={
          selected ? (
            <Button variant="secondary" onClick={() => setSelected(null)}>
              Clear
            </Button>
          ) : null
        }
      >
        {selected ? <TransactionDetail transaction={selected} allowReverse /> : <EmptyState title="No transaction selected" detail="Select a transaction to view reversal controls." />}
      </Panel>
    </div>
  );
}
