import { useQuery } from "@tanstack/react-query";
import { ArrowDownLeft, ArrowRight, ArrowUpRight, CreditCard, PlusCircle, ShieldCheck, Sparkles, WalletCards } from "lucide-react";
import { Link } from "../routing";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis } from "recharts";
import { getNotificationSummary, getTransactions, getUserStats, listLedgerAccounts, listOwnedAccounts } from "../lib/queries";
import { compactDate, money, percent } from "../lib/format";
import { availableBalance, ledgerBalance, pendingBalance, projectionFor, projectionMap } from "../lib/accountBalances";
import { EmptyState, ErrorNotice, Panel, Skeleton } from "../components/ui";
import { StatusBadge } from "../components/StatusBadge";
import { MONEY_STATE_REFRESH_INTERVAL_MS } from "../lib/queryInvalidation";

export function DashboardPage() {
  const accounts = useQuery({ queryKey: ["accounts", "owned"], queryFn: () => listOwnedAccounts(), refetchInterval: MONEY_STATE_REFRESH_INTERVAL_MS });
  const ledgerAccounts = useQuery({ queryKey: ["ledger", "accounts"], queryFn: listLedgerAccounts, retry: false, refetchInterval: MONEY_STATE_REFRESH_INTERVAL_MS });
  const transactions = useQuery({ queryKey: ["transactions", 0], queryFn: () => getTransactions(0) });
  const stats = useQuery({ queryKey: ["stats", "user"], queryFn: getUserStats });
  const notifications = useQuery({ queryKey: ["notification-summary"], queryFn: getNotificationSummary });
  const projections = projectionMap(ledgerAccounts.data);
  const accountList = accounts.data?.content ?? [];
  const dashboardCurrency = accountList.length === 0
    ? null
    : accountList.every((account) => account.currency === accountList[0].currency) ? accountList[0].currency : null;
  const totalBalance = accountList.reduce((sum, account) => sum + availableBalance(account, projectionFor(account, projections)), 0);
  const chartData = Object.entries(stats.data?.transactionAmountsByType ?? {}).map(([name, value]) => ({ name: name.replace(/_/g, " "), value }));
  const loading = accounts.isLoading || transactions.isLoading || stats.isLoading;
  const error = accounts.error || transactions.error || stats.error;

  return <div className="grid gap-6">
    <section className="customer-hero overflow-hidden rounded-[28px] bg-[#0b2924] p-6 text-white shadow-xl shadow-emerald-950/10 sm:p-8">
      <div className="relative z-10 flex flex-col gap-7 lg:flex-row lg:items-end lg:justify-between">
        <div><p className="flex items-center gap-2 text-xs font-bold uppercase tracking-[.2em] text-emerald-200"><Sparkles className="h-4 w-4" />Your financial snapshot</p><h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Dashboard</h1><p className="mt-2 max-w-xl text-sm leading-6 text-emerald-50/70">A clear view of what is available, what is moving, and what needs your attention.</p></div>
        <div className="flex flex-wrap gap-2"><Link className="customer-action customer-action-light" to="/accounts"><PlusCircle className="h-4 w-4" />New account</Link><Link className="customer-action bg-emerald-300 text-emerald-950 hover:bg-emerald-200" to="/move-money"><ArrowUpRight className="h-4 w-4" />Move money</Link></div>
      </div>
      <div className="relative z-10 mt-8 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div className="customer-hero-stat"><span>Available balance</span><strong>{loading ? "—" : dashboardCurrency ? money(totalBalance, dashboardCurrency) : "Multiple currencies"}</strong><small>Across {accountList.length} account{accountList.length === 1 ? "" : "s"}</small></div>
        <div className="customer-hero-stat"><span>Transactions</span><strong>{stats.data?.totalTransactions ?? "—"}</strong><small>{percent(stats.data?.successRate)} success rate</small></div>
        <div className="customer-hero-stat"><span>Spending controls</span><strong>Per account</strong><small><Link className="underline" to="/security">Review authoritative limits</Link></small></div>
        <div className="customer-hero-stat"><span>Inbox</span><strong>{notifications.data?.unread ?? 0}</strong><small>Unread notification{notifications.data?.unread === 1 ? "" : "s"}</small></div>
      </div>
    </section>

    {error ? <ErrorNotice message="Some dashboard information could not be loaded. Your existing data and workflows remain available." /> : null}
    {loading ? <div className="grid gap-4 md:grid-cols-3"><Skeleton className="h-36" /><Skeleton className="h-36" /><Skeleton className="h-36" /></div> : null}

    <div className="grid gap-6 xl:grid-cols-[1.4fr_.8fr]">
      <Panel title="Your accounts" action={<Link className="customer-text-link" to="/accounts">View all <ArrowRight className="h-4 w-4" /></Link>}>
        {accountList.length ? <div className="grid gap-4 md:grid-cols-2">{accountList.slice(0, 4).map((account) => {
          const projection = projectionFor(account, projections);
          return <article key={account.id} className="customer-account-card">
            <div className="flex items-start justify-between gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-100 text-brand dark:bg-emerald-950"><WalletCards className="h-5 w-5" /></span><div className="flex flex-wrap justify-end gap-2"><StatusBadge value={account.accountType} /><StatusBadge value={account.status ?? "ACTIVE"} /></div></div>
            <p className="mt-5 text-xs font-semibold uppercase tracking-wider text-muted">Account #{account.id}</p>
            <p className="mt-1 text-2xl font-bold tabular-nums tracking-tight">{money(availableBalance(account, projection), projection?.currency ?? account.currency)}</p>
            <p className="mt-1 text-xs text-muted">{projection ? `Posted ${money(ledgerBalance(account, projection), projection.currency)}` : `Ledger ${money(ledgerBalance(account), account.currency)}`}</p>
            {projection ? <p className="text-xs text-muted">Pending {money(pendingBalance(projection), projection.currency)} unavailable</p> : null}
            {account.status === "FROZEN" ? <p className="mt-2 text-xs font-medium text-danger">{account.statusReason || "Debit hold active"}</p> : null}
            <p className="mt-4 border-t border-line pt-3 text-xs text-muted dark:border-slate-700">Opened {compactDate(account.createdAt)}</p>
          </article>;
        })}</div> : !accounts.isLoading ? <EmptyState title="No accounts yet" detail="Create an account to begin moving money." /> : null}
      </Panel>

      <Panel title="Quick actions">
        <div className="grid gap-3">
          <Link className="customer-quick-action" to="/move-money"><span className="bg-emerald-100 text-brand dark:bg-emerald-950"><ArrowUpRight /></span><div><strong>Send money</strong><small>Transfer between accounts or to a recipient</small></div><ArrowRight className="ml-auto h-4 w-4 text-muted" /></Link>
          <Link className="customer-quick-action" to="/move-money"><span className="bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300"><ArrowDownLeft /></span><div><strong>Add funds</strong><small>Deposit into an eligible account</small></div><ArrowRight className="ml-auto h-4 w-4 text-muted" /></Link>
          <Link className="customer-quick-action" to="/scheduled-transfers"><span className="bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300"><CreditCard /></span><div><strong>Plan a payment</strong><small>Create or manage scheduled transfers</small></div><ArrowRight className="ml-auto h-4 w-4 text-muted" /></Link>
          <Link className="customer-quick-action" to="/security"><span className="bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300"><ShieldCheck /></span><div><strong>Security center</strong><small>Review MFA and transfer controls</small></div><ArrowRight className="ml-auto h-4 w-4 text-muted" /></Link>
        </div>
      </Panel>
    </div>

    <div className="grid gap-6 xl:grid-cols-[1.4fr_.8fr]">
      <Panel title="Recent activity" action={<Link className="customer-text-link" to="/transactions">All transactions <ArrowRight className="h-4 w-4" /></Link>}>
        {transactions.data?.content.length ? <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th>Transaction</th><th>Type</th><th>Status</th><th className="text-right">Amount</th><th className="text-right">Date</th></tr></thead><tbody>{transactions.data.content.slice(0, 6).map((item) => <tr key={item.transactionId}><td className="font-mono text-xs">{item.transactionId}</td><td>{item.type}</td><td><StatusBadge value={item.status} /></td><td className="text-right font-semibold tabular-nums">{money(item.amount, item.currency)}</td><td className="text-right text-muted">{compactDate(item.createdAt)}</td></tr>)}</tbody></table></div> : !transactions.isLoading ? <EmptyState title="No recent activity" detail="Your completed and pending transactions will appear here." /> : null}
      </Panel>
      <Panel title="Activity mix">
        {chartData.length ? <div className="h-64"><ResponsiveContainer><BarChart data={chartData} margin={{ left: -24, right: 8 }}><CartesianGrid vertical={false} strokeDasharray="3 3" stroke="#d8dee8" /><XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fontSize: 11 }} /><Tooltip cursor={{ fill: "rgba(16,185,129,.08)" }} /><Bar dataKey="value" fill="#10b981" radius={[8, 8, 2, 2]} /></BarChart></ResponsiveContainer></div> : <EmptyState title="No stats available" detail="Transaction summaries appear after activity is recorded." />}
      </Panel>
    </div>
  </div>;
}
