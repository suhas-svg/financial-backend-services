import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { UseFormRegisterReturn } from "react-hook-form";
import { useForm } from "react-hook-form";
import { deposit, listAccounts, transfer, withdraw } from "../lib/queries";
import { createIdempotencyKey } from "../lib/idempotency";
import { moneyMovementSchema, transferSchema, type MoneyMovementValues, type TransferValues } from "../lib/schemas";
import { Button, ErrorNotice, Field, Input, Panel, Select } from "../components/ui";

function AccountSelect({ field }: { field: UseFormRegisterReturn }) {
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: () => listAccounts() });
  return (
    <Select {...field}>
      <option value="">Select account</option>
      {accounts.data?.content.map((account) => (
        <option key={account.id} value={String(account.id)}>
          #{account.id} - {account.accountType}
        </option>
      ))}
    </Select>
  );
}

export function MoveMoneyPage() {
  const queryClient = useQueryClient();
  const depositForm = useForm<MoneyMovementValues>({ resolver: zodResolver(moneyMovementSchema), defaultValues: { accountId: "", amount: 0, currency: "USD", description: "", reference: "" } });
  const withdrawForm = useForm<MoneyMovementValues>({ resolver: zodResolver(moneyMovementSchema), defaultValues: { accountId: "", amount: 0, currency: "USD", description: "", reference: "" } });
  const transferForm = useForm<TransferValues>({ resolver: zodResolver(transferSchema), defaultValues: { fromAccountId: "", toAccountId: "", amount: 0, currency: "USD", description: "", reference: "" } });
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["accounts"] });
    queryClient.invalidateQueries({ queryKey: ["transactions"] });
    queryClient.invalidateQueries({ queryKey: ["stats"] });
  };
  const depositMutation = useMutation({ mutationFn: (values: MoneyMovementValues) => deposit(values, createIdempotencyKey("deposit")), onSuccess: invalidate });
  const withdrawMutation = useMutation({ mutationFn: (values: MoneyMovementValues) => withdraw(values, createIdempotencyKey("withdraw")), onSuccess: invalidate });
  const transferMutation = useMutation({ mutationFn: (values: TransferValues) => transfer(values, createIdempotencyKey("transfer")), onSuccess: invalidate });

  return (
    <div className="grid gap-6 xl:grid-cols-3">
      <Panel title="Deposit">
        <form className="grid gap-4" onSubmit={depositForm.handleSubmit((values) => depositMutation.mutate(values))}>
          <ErrorNotice message={depositMutation.error instanceof Error ? depositMutation.error.message : undefined} />
          <Field label="Account" error={depositForm.formState.errors.accountId?.message}>
            <AccountSelect field={depositForm.register("accountId")} />
          </Field>
          <MoneyFields form={depositForm} />
          <Button type="submit" disabled={depositMutation.isPending}>Deposit</Button>
        </form>
      </Panel>
      <Panel title="Withdraw">
        <form className="grid gap-4" onSubmit={withdrawForm.handleSubmit((values) => withdrawMutation.mutate(values))}>
          <ErrorNotice message={withdrawMutation.error instanceof Error ? withdrawMutation.error.message : undefined} />
          <Field label="Account" error={withdrawForm.formState.errors.accountId?.message}>
            <AccountSelect field={withdrawForm.register("accountId")} />
          </Field>
          <MoneyFields form={withdrawForm} />
          <Button type="submit" disabled={withdrawMutation.isPending}>Withdraw</Button>
        </form>
      </Panel>
      <Panel title="Transfer">
        <form className="grid gap-4" onSubmit={transferForm.handleSubmit((values) => transferMutation.mutate(values))}>
          <ErrorNotice message={transferMutation.error instanceof Error ? transferMutation.error.message : undefined} />
          <Field label="From account" error={transferForm.formState.errors.fromAccountId?.message}>
            <AccountSelect field={transferForm.register("fromAccountId")} />
          </Field>
          <Field label="To account" error={transferForm.formState.errors.toAccountId?.message}>
            <AccountSelect field={transferForm.register("toAccountId")} />
          </Field>
          <Field label="Amount" error={transferForm.formState.errors.amount?.message}>
            <Input type="number" step="0.01" {...transferForm.register("amount")} />
          </Field>
          <Field label="Currency" error={transferForm.formState.errors.currency?.message}>
            <Select {...transferForm.register("currency")}>
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
            </Select>
          </Field>
          <Field label="Description" error={transferForm.formState.errors.description?.message}>
            <Input {...transferForm.register("description")} />
          </Field>
          <Field label="Reference" error={transferForm.formState.errors.reference?.message}>
            <Input {...transferForm.register("reference")} />
          </Field>
          <Button type="submit" disabled={transferMutation.isPending}>Transfer</Button>
        </form>
      </Panel>
    </div>
  );
}

function MoneyFields({ form }: { form: ReturnType<typeof useForm<MoneyMovementValues>> }) {
  return (
    <>
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
      <Field label="Description" error={form.formState.errors.description?.message}>
        <Input {...form.register("description")} />
      </Field>
      <Field label="Reference" error={form.formState.errors.reference?.message}>
        <Input {...form.register("reference")} />
      </Field>
    </>
  );
}
