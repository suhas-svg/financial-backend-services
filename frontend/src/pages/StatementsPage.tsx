import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Button, EmptyState, ErrorNotice, Field, Input, Panel, Stat } from "../components/ui";
import { compactDate, money } from "../lib/format";
import { exportStatementCsv, generateStatement, listStatements } from "../lib/queries";
import type { CustomerStatement } from "../types";

export function StatementsPage() {
  const queryClient = useQueryClient();
  const [accountId, setAccountId] = useState("1001");
  const [statementMonth, setStatementMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const statements = useQuery({ queryKey: ["statements"], queryFn: listStatements });
  const selected = useMemo(
    () => statements.data?.find((statement) => statement.statementId === selectedId) ?? statements.data?.[0] ?? null,
    [selectedId, statements.data]
  );

  useEffect(() => {
    if (!selectedId && selected) {
      setSelectedId(selected.statementId);
      setAccountId(selected.externalAccountId);
      setStatementMonth(selected.periodStart.slice(0, 7));
    }
  }, [selected, selectedId]);

  const generateMutation = useMutation({
    mutationFn: () => generateStatement({ externalAccountId: accountId.trim(), yearMonth: statementMonth }),
    onSuccess: (statement) => {
      setSelectedId(statement.statementId);
      setAccountId(statement.externalAccountId);
      setStatementMonth(statement.periodStart.slice(0, 7));
      queryClient.invalidateQueries({ queryKey: ["statements"] });
    }
  });

  const csvMutation = useMutation({
    mutationFn: (statementId: string) => exportStatementCsv(statementId),
    onSuccess: (csv, statementId) => {
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `statement-${statementId}.csv`;
      if (!navigator.userAgent.includes("jsdom")) {
        anchor.click();
      }
      URL.revokeObjectURL(url);
    }
  });

  return (
    <div className="grid gap-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Statements</h1>
          <p className="text-sm text-muted">Immutable monthly statements generated from posted ledger journals.</p>
        </div>
        <form
          className="grid gap-2 sm:grid-cols-[180px_180px_auto]"
          onSubmit={(event) => {
            event.preventDefault();
            generateMutation.mutate();
          }}
        >
          <Field label="Account">
            <Input value={accountId} onChange={(event) => setAccountId(event.target.value)} />
          </Field>
          <Field label="Statement month">
            <Input type="month" value={statementMonth} onChange={(event) => setStatementMonth(event.target.value)} />
          </Field>
          <Button className="self-end" type="submit" disabled={generateMutation.isPending || !accountId.trim() || !statementMonth}>
            Generate statement
          </Button>
        </form>
      </div>

      <ErrorNotice message={errorMessage(statements.error) || errorMessage(generateMutation.error) || errorMessage(csvMutation.error)} />

      <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
        <Panel title="Monthly statements">
          {statements.isLoading ? <p className="text-sm text-muted">Loading statements...</p> : null}
          {!statements.isLoading && !statements.data?.length ? (
            <EmptyState title="No statements found" detail="Generate a monthly statement for an account after ledger activity posts." />
          ) : null}
          {statements.data?.length ? <StatementTable statements={statements.data} selectedId={selected?.statementId} onSelect={setSelectedId} /> : null}
        </Panel>

        <Panel
          title="Statement detail"
          action={
            selected ? (
              <Button variant="secondary" onClick={() => csvMutation.mutate(selected.statementId)} disabled={csvMutation.isPending}>
                Download CSV
              </Button>
            ) : null
          }
        >
          {selected ? <StatementDetail statement={selected} /> : <EmptyState title="No statement selected" detail="Select or generate a statement to inspect its ledger lines." />}
        </Panel>
      </div>
    </div>
  );
}

function StatementTable({
  statements,
  selectedId,
  onSelect
}: {
  statements: CustomerStatement[];
  selectedId?: string;
  onSelect: (statementId: string) => void;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Account</th>
            <th>Period</th>
            <th>Version</th>
            <th>Opening</th>
            <th>Closing</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {statements.map((statement) => (
            <tr key={statement.statementId} className="border-b border-line last:border-0">
              <td className="py-3 font-medium">{statement.externalAccountId}</td>
              <td>{compactDate(statement.periodStart)} - {compactDate(statement.periodEnd)}</td>
              <td>{statement.statementVersion}</td>
              <td>{money(statement.openingBalance, statement.currency)}</td>
              <td>{money(statement.closingBalance, statement.currency)}</td>
              <td className="text-right">
                <Button variant={selectedId === statement.statementId ? "primary" : "secondary"} onClick={() => onSelect(statement.statementId)}>
                  View
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatementDetail({ statement }: { statement: CustomerStatement }) {
  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <Stat label="Account" value={`Account ${statement.externalAccountId}`} />
        <Stat label="Currency" value={statement.currency} />
        <Stat label="Opening balance" value={money(statement.openingBalance, statement.currency)} />
        <Stat label="Closing balance" value={`Balance ${money(statement.closingBalance, statement.currency)}`} />
      </div>
      <div className="grid gap-2 text-sm">
        <p className="text-xs font-medium uppercase tracking-wide text-muted">Posted ledger lines</p>
        {statement.lines.length ? (
          statement.lines.map((line) => (
            <div key={line.lineId} className="rounded-md border border-line bg-white p-3">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="font-medium">{line.description || line.journalId}</p>
                  <p className="text-xs text-muted">{compactDate(line.effectiveDate)} · Journal {line.journalId}</p>
                </div>
                <p className="font-semibold">{money(line.amount, line.currency)}</p>
              </div>
              <p className="mt-1 text-xs text-muted">Running balance {money(line.runningBalance, line.currency)}</p>
            </div>
          ))
        ) : (
          <EmptyState title="No posted activity" detail="This statement has no posted ledger lines for the period." />
        )}
      </div>
    </div>
  );
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : undefined;
}
