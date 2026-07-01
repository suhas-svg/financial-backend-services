import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { forwardRef, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { Badge, Button, EmptyState, ErrorNotice, Field, Input, Panel, Select } from "../components/ui";
import { availableBalance } from "../lib/accountBalances";
import { compactDate, money } from "../lib/format";
import { cancelScheduledTransfer, createScheduledTransfer, listAccounts, listScheduledTransferRuns, listScheduledTransfers, pauseScheduledTransfer, resumeScheduledTransfer } from "../lib/queries";
import { scheduledTransferSchema, type ScheduledTransferValues } from "../lib/schemas";
import type { Account, ScheduledTransfer, ScheduledTransferStatus } from "../types";

const statuses: Array<ScheduledTransferStatus | ""> = ["ACTIVE", "PAUSED", ""];

export function ScheduledTransfersPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<ScheduledTransferStatus | "">("ACTIVE");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [statusOverrides, setStatusOverrides] = useState<Record<string, ScheduledTransferStatus>>({});
  const form = useForm<ScheduledTransferValues>({
    resolver: zodResolver(scheduledTransferSchema),
    defaultValues: {
      fromAccountId: "",
      toAccountId: "",
      amount: 0,
      currency: "USD",
      description: "",
      reference: "",
      scheduleType: "RECURRING",
      frequency: "MONTHLY",
      firstRunAt: "",
      endAt: ""
    }
  });

  const accounts = useQuery({ queryKey: ["accounts"], queryFn: () => listAccounts() });
  const schedules = useQuery({ queryKey: ["scheduled-transfers", status], queryFn: () => listScheduledTransfers({ status }) });
  const selected = useMemo(
    () => schedules.data?.content.find((schedule) => schedule.scheduleId === selectedId) ?? schedules.data?.content[0] ?? null,
    [schedules.data, selectedId]
  );
  const selectedStatus = selected ? statusOverrides[selected.scheduleId] ?? selected.status : undefined;
  const runs = useQuery({
    queryKey: ["scheduled-transfer-runs", selected?.scheduleId],
    queryFn: () => listScheduledTransferRuns(selected!.scheduleId),
    enabled: Boolean(selected?.scheduleId)
  });
  const scheduleType = form.watch("scheduleType");

  useEffect(() => {
    if (!selectedId && selected) {
      setSelectedId(selected.scheduleId);
    }
  }, [selected, selectedId]);

  useEffect(() => {
    if (scheduleType === "ONE_TIME") {
      form.setValue("frequency", undefined);
    }
    if (scheduleType === "RECURRING" && !form.getValues("frequency")) {
      form.setValue("frequency", "MONTHLY");
    }
  }, [form, scheduleType]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["scheduled-transfers"] });
    queryClient.invalidateQueries({ queryKey: ["scheduled-transfer-runs"] });
  };
  const createMutation = useMutation({
    mutationFn: createScheduledTransfer,
    onSuccess: (schedule) => {
      setSelectedId(schedule.scheduleId);
      form.reset({
        fromAccountId: "",
        toAccountId: "",
        amount: 0,
        currency: "USD",
        description: "",
        reference: "",
        scheduleType: "RECURRING",
        frequency: "MONTHLY",
        firstRunAt: "",
        endAt: ""
      });
      invalidate();
    }
  });
  const pauseMutation = useMutation({
    mutationFn: pauseScheduledTransfer,
    onSuccess: (schedule) => {
      setStatusOverrides((current) => ({ ...current, [schedule.scheduleId]: schedule.status }));
      invalidate();
    }
  });
  const resumeMutation = useMutation({
    mutationFn: resumeScheduledTransfer,
    onSuccess: (schedule) => {
      setStatusOverrides((current) => ({ ...current, [schedule.scheduleId]: schedule.status }));
      invalidate();
    }
  });
  const cancelMutation = useMutation({
    mutationFn: cancelScheduledTransfer,
    onSuccess: (_result, scheduleId) => {
      setStatusOverrides((current) => ({ ...current, [scheduleId]: "CANCELED" }));
      invalidate();
    }
  });

  const error = errorMessage(createMutation.error) || errorMessage(pauseMutation.error) || errorMessage(resumeMutation.error) || errorMessage(cancelMutation.error) || errorMessage(schedules.error);

  return (
    <div className="grid gap-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Scheduled transfers</h1>
          <p className="text-sm text-muted">{schedules.data?.totalElements ?? 0} schedules</p>
        </div>
        <Field label="Status">
          <Select className="min-w-44" value={status} onChange={(event) => setStatus(event.target.value as ScheduledTransferStatus | "")}>
            {statuses.map((value) => <option key={value || "all"} value={value}>{value || "All statuses"}</option>)}
          </Select>
        </Field>
      </div>

      <ErrorNotice message={error} />

      <div className="grid gap-6 xl:grid-cols-[380px_1fr]">
        <Panel title="Create schedule">
          <form className="grid gap-4" onSubmit={form.handleSubmit((values) => createMutation.mutate(values))}>
            <Field label="From account" error={form.formState.errors.fromAccountId?.message}>
              <AccountSelect accounts={accounts.data?.content ?? []} value={form.watch("fromAccountId")} {...form.register("fromAccountId")} />
            </Field>
            <Field label="To account" error={form.formState.errors.toAccountId?.message}>
              <AccountSelect accounts={accounts.data?.content ?? []} value={form.watch("toAccountId")} {...form.register("toAccountId")} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Amount" error={form.formState.errors.amount?.message}>
                <Input type="number" step="0.01" {...form.register("amount")} />
              </Field>
              <Field label="Currency" error={form.formState.errors.currency?.message}>
                <Select {...form.register("currency")}>
                  <option value="USD">USD</option>
                  <option value="EUR">EUR</option>
                  <option value="GBP">GBP</option>
                </Select>
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Schedule type" error={form.formState.errors.scheduleType?.message}>
                <Select {...form.register("scheduleType")}>
                  <option value="ONE_TIME">ONE_TIME</option>
                  <option value="RECURRING">RECURRING</option>
                </Select>
              </Field>
              {scheduleType === "RECURRING" ? (
                <Field label="Frequency" error={form.formState.errors.frequency?.message}>
                  <Select {...form.register("frequency")}>
                    <option value="WEEKLY">WEEKLY</option>
                    <option value="BIWEEKLY">BIWEEKLY</option>
                    <option value="MONTHLY">MONTHLY</option>
                  </Select>
                </Field>
              ) : null}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="First run date" error={form.formState.errors.firstRunAt?.message}>
                <Input type="datetime-local" {...form.register("firstRunAt")} />
              </Field>
              <Field label="End date" error={form.formState.errors.endAt?.message}>
                <Input type="datetime-local" {...form.register("endAt")} />
              </Field>
            </div>
            <Field label="Description" error={form.formState.errors.description?.message}>
              <Input {...form.register("description")} />
            </Field>
            <Field label="Reference" error={form.formState.errors.reference?.message}>
              <Input {...form.register("reference")} />
            </Field>
            <Button type="submit" disabled={createMutation.isPending}>Create schedule</Button>
          </form>
        </Panel>

        <div className="grid gap-6">
          <Panel title="Schedules">
            {schedules.isLoading ? <p className="text-sm text-muted">Loading scheduled transfers...</p> : null}
            {!schedules.isLoading && !schedules.data?.content.length ? <EmptyState title="No scheduled transfers" detail="Create a one-time or recurring transfer to automate movement between accounts." /> : null}
            {schedules.data?.content.length ? (
              <ScheduleTable schedules={schedules.data.content} selectedId={selected?.scheduleId} statusOverrides={statusOverrides} onSelect={setSelectedId} />
            ) : null}
          </Panel>

          <Panel
            title="Schedule detail"
            action={
              selected ? (
                <ActionBar
                  schedule={selected}
                  status={selectedStatus ?? selected.status}
                  onPause={() => pauseMutation.mutate(selected.scheduleId)}
                  onResume={() => resumeMutation.mutate(selected.scheduleId)}
                  onCancel={() => cancelMutation.mutate(selected.scheduleId)}
                  pending={pauseMutation.isPending || resumeMutation.isPending || cancelMutation.isPending}
                />
              ) : null
            }
          >
            {selected ? <ScheduleDetail schedule={selected} status={selectedStatus ?? selected.status} runs={runs.data?.content ?? []} /> : <EmptyState title="No schedule selected" detail="Select a schedule to inspect its execution history." />}
          </Panel>
        </div>
      </div>
    </div>
  );
}

const AccountSelect = forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement> & { accounts: Account[] }>(function AccountSelect({ accounts, ...props }, ref) {
  return (
    <Select ref={ref} {...props}>
      <option value="">Select account</option>
      {accounts.map((account) => (
        <option key={account.id} value={String(account.id)}>
          #{account.id} - {account.accountType} - Available {availableBalance(account).toFixed(2)}
        </option>
      ))}
    </Select>
  );
});

function ScheduleTable({
  schedules,
  selectedId,
  statusOverrides,
  onSelect
}: {
  schedules: ScheduledTransfer[];
  selectedId?: string;
  statusOverrides: Record<string, ScheduledTransferStatus>;
  onSelect: (scheduleId: string) => void;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase text-muted">
          <tr>
            <th className="py-2">Schedule</th>
            <th>Route</th>
            <th>Amount</th>
            <th>Cadence</th>
            <th>Next run</th>
            <th>Status</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {schedules.map((schedule) => {
            const status = statusOverrides[schedule.scheduleId] ?? schedule.status;
            return (
              <tr key={schedule.scheduleId} className="border-b border-line last:border-0">
                <td className="py-3 font-medium">{schedule.scheduleId}</td>
                <td>{schedule.fromAccountId} to {schedule.toAccountId}</td>
                <td>{money(schedule.amount, schedule.currency)}</td>
                <td>{schedule.scheduleType === "RECURRING" ? schedule.frequency : "ONE_TIME"}</td>
                <td>{compactDate(schedule.nextRunAt)}</td>
                <td><Badge tone={statusTone(status)}>{status}</Badge></td>
                <td className="text-right">
                  <Button variant={selectedId === schedule.scheduleId ? "primary" : "secondary"} onClick={() => onSelect(schedule.scheduleId)}>
                    View {schedule.scheduleId}
                  </Button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ActionBar({
  schedule,
  status,
  onPause,
  onResume,
  onCancel,
  pending
}: {
  schedule: ScheduledTransfer;
  status: ScheduledTransferStatus;
  onPause: () => void;
  onResume: () => void;
  onCancel: () => void;
  pending: boolean;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {status === "ACTIVE" ? <Button variant="secondary" onClick={onPause} disabled={pending}>Pause {schedule.scheduleId}</Button> : null}
      {status === "PAUSED" ? <Button variant="secondary" onClick={onResume} disabled={pending}>Resume {schedule.scheduleId}</Button> : null}
      {status !== "CANCELED" && status !== "COMPLETED" ? <Button variant="danger" onClick={onCancel} disabled={pending}>Cancel {schedule.scheduleId}</Button> : null}
    </div>
  );
}

function ScheduleDetail({ schedule, status, runs }: { schedule: ScheduledTransfer; status: ScheduledTransferStatus; runs: Array<{ runId: string; scheduledFor: string; status: string; transactionId?: string; failureReason?: string }> }) {
  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Detail label="Status" value={<Badge tone={statusTone(status)}>{status}</Badge>} />
        <Detail label="Amount" value={money(schedule.amount, schedule.currency)} />
        <Detail label="Next run" value={compactDate(schedule.nextRunAt)} />
        <Detail label="Last run" value={schedule.lastRunAt ? compactDate(schedule.lastRunAt) : "-"} />
      </div>
      <div className="grid gap-1 text-sm">
        <p><span className="font-medium">From:</span> Account {schedule.fromAccountId}</p>
        <p><span className="font-medium">To:</span> Account {schedule.toAccountId}</p>
        <p><span className="font-medium">Cadence:</span> {schedule.scheduleType === "RECURRING" ? schedule.frequency : "ONE_TIME"}</p>
        {schedule.description ? <p><span className="font-medium">Description:</span> {schedule.description}</p> : null}
        {schedule.reference ? <p><span className="font-medium">Reference:</span> {schedule.reference}</p> : null}
      </div>
      <div className="grid gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-muted">Recent runs</p>
        {runs.length ? runs.map((run) => (
          <div key={run.runId} className="rounded-md border border-line bg-white p-3 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium">{run.runId}</span>
              <Badge tone={run.status === "COMPLETED" ? "good" : run.status === "FAILED" ? "bad" : "neutral"}>{run.status}</Badge>
            </div>
            <p className="mt-1 text-xs text-muted">{compactDate(run.scheduledFor)}{run.transactionId ? ` - Transaction ${run.transactionId}` : ""}</p>
            {run.failureReason ? <p className="mt-1 text-xs text-danger">{run.failureReason}</p> : null}
          </div>
        )) : <EmptyState title="No runs yet" detail="Execution history appears after the first scheduled run is processed." />}
      </div>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="rounded-md border border-line bg-white p-3">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <div className="mt-1 text-sm font-semibold text-ink">{value}</div>
    </div>
  );
}

function statusTone(status: ScheduledTransferStatus) {
  if (status === "ACTIVE") return "good";
  if (status === "PAUSED") return "warn";
  if (status === "CANCELED") return "bad";
  return "neutral";
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : undefined;
}
