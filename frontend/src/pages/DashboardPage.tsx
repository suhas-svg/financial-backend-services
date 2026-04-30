import { useQuery } from "@tanstack/react-query";
import { ArrowRightLeft, PlusCircle } from "lucide-react";
import { Link } from "react-router-dom";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { listAccounts, getLimits, getTransactions, getUserStats } from "../lib/queries";
import { compactDate, money, percent } from "../lib/format";
import { EmptyState, Panel, Stat } from "../components/ui";
import { StatusBadge } from "../components/StatusBadge";

export function DashboardPage() {
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: () => listAccounts() });
  const transactions = useQuery({ queryKey: ["transactions", 0], queryFn: () => getTransactions(0) });
  const stats = useQuery({ queryKey: ["stats", "user"], queryFn: getUserStats });
  const limits = useQuery({ queryKey: ["limits"], queryFn: getLimits });
  const totalBalance = accounts.data?.content.reduce((sum, account) => sum + Number(account.balance), 0) ?? 0;
  const chartData = Object.entries(stats.data?.transactionAmountsByType ?? {}).map(([name, value]) => ({ name, value }));

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Dashboard</h1>
          <p className="text-sm text-muted">Balances, limits, and recent account activity.</p>
        </div>
        <div className="flex gap-2">
          <Link className="inline-flex h-9 items-center justify-center gap-2 rounded-md border border-line bg-white px-3 text-sm font-medium text-ink hover:bg-slate-50" to="/accounts">
            <PlusCircle className="h-4 w-4" />
            New account
          </Link>
          <Link className="inline-flex h-9 items-center justify-center gap-2 rounded-md bg-brand px-3 text-sm font-medium text-white hover:bg-teal-800" to="/move-money">
            <ArrowRightLeft className="h-4 w-4" />
            Move money
          </Link>
        </div>
      </div>
      <div className="grid gap-3 md:grid-cols-4">
        <Stat label="Total balance" value={money(totalBalance)} />
        <Stat label="Transactions" value={stats.data?.totalTransactions ?? 0} />
        <Stat label="Success rate" value={percent(stats.data?.successRate)} />
        <Stat label="Single limit" value={money(limits.data?.singleTransactionLimit, limits.data?.currency)} />
      </div>
      <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
        <Panel title="Accounts">
          <div className="grid gap-3 md:grid-cols-2">
            {accounts.data?.content.length ? (
              accounts.data.content.map((account) => (
                <div key={account.id} className="rounded-md border border-line p-3">
                  <div className="flex items-center justify-between">
                    <p className="font-medium">#{account.id}</p>
                    <StatusBadge value={account.accountType} />
                  </div>
                  <p className="mt-2 text-2xl font-semibold">{money(account.balance)}</p>
                  <p className="text-xs text-muted">Opened {compactDate(account.createdAt)}</p>
                </div>
              ))
            ) : (
              <EmptyState title="No accounts yet" detail="Create an account to begin moving money." />
            )}
          </div>
        </Panel>
        <Panel title="Transaction mix">
          {chartData.length ? (
            <div className="h-64">
              <ResponsiveContainer>
                <BarChart data={chartData}>
                  <XAxis dataKey="name" />
                  <YAxis />
                  <Tooltip />
                  <Bar dataKey="value" fill="#0f766e" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <EmptyState title="No stats available" detail="Transaction summaries appear after activity is recorded." />
          )}
        </Panel>
      </div>
      <Panel title="Recent transactions">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line text-xs uppercase text-muted">
              <tr>
                <th className="py-2">ID</th>
                <th>Type</th>
                <th>Status</th>
                <th>Amount</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {transactions.data?.content.slice(0, 6).map((item) => (
                <tr key={item.transactionId} className="border-b border-line last:border-0">
                  <td className="py-2 font-mono text-xs">{item.transactionId}</td>
                  <td>{item.type}</td>
                  <td>
                    <StatusBadge value={item.status} />
                  </td>
                  <td>{money(item.amount, item.currency)}</td>
                  <td>{compactDate(item.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
    </div>
  );
}
