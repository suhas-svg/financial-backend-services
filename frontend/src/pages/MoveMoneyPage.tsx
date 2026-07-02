import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { UseFormRegisterReturn } from "react-hook-form";
import { useForm } from "react-hook-form";
import { deposit, listAccounts, listBeneficiaries, transfer, withdraw } from "../lib/queries";
import { createIdempotencyKey } from "../lib/idempotency";
import { availableBalance, canDebit } from "../lib/accountBalances";
import { moneyMovementSchema, transferSchema, type MoneyMovementValues, type TransferValues } from "../lib/schemas";
import { Button, ErrorNotice, Field, Input, Panel, Select } from "../components/ui";
import type { Beneficiary } from "../types";

function AccountSelect({ field, debitSource = false, amount = 0 }: { field: UseFormRegisterReturn; debitSource?: boolean; amount?: number }) {
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: () => listAccounts() });
  return (
    <Select {...field}>
      <option value="">Select account</option>
      {accounts.data?.content.map((account) => (
        <option key={account.id} value={String(account.id)} disabled={debitSource && !canDebit(account, amount)}>
          #{account.id} - {account.accountType} - Available {availableBalance(account).toFixed(2)}{account.status === "FROZEN" ? " - FROZEN" : ""}
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
  const beneficiaries = useQuery({ queryKey: ["beneficiaries", "ACTIVE"], queryFn: () => listBeneficiaries({ status: "ACTIVE" }) });
  const withdrawAmount = Number(withdrawForm.watch("amount") || 0);
  const transferAmount = Number(transferForm.watch("amount") || 0);
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
          <Field label="Withdraw account" error={withdrawForm.formState.errors.accountId?.message}>
            <AccountSelect field={withdrawForm.register("accountId")} debitSource amount={withdrawAmount} />
          </Field>
          <MoneyFields form={withdrawForm} />
          <Button type="submit" disabled={withdrawMutation.isPending}>Withdraw</Button>
        </form>
      </Panel>
      <Panel title="Transfer">
        <form className="grid gap-4" onSubmit={transferForm.handleSubmit((values) => transferMutation.mutate(values))}>
          <ErrorNotice message={transferMutation.error instanceof Error ? transferMutation.error.message : undefined} />
          <Field label="From account" error={transferForm.formState.errors.fromAccountId?.message}>
            <AccountSelect field={transferForm.register("fromAccountId")} debitSource amount={transferAmount} />
          </Field>
          <Field label="Saved recipient">
            <RecipientSelect
              beneficiaries={beneficiaries.data?.content ?? []}
              onSelect={(beneficiary) => {
                transferForm.setValue("toAccountId", beneficiary.destinationAccountId, { shouldValidate: true });
                transferForm.setValue("currency", beneficiary.currency, { shouldValidate: true });
              }}
            />
          </Field>
          <Field label="To account" error={transferForm.formState.errors.toAccountId?.message}>
            <Input className="mt-2" placeholder="Manual destination account" {...transferForm.register("toAccountId")} />
          </Field>
          <Field label="Transfer amount" error={transferForm.formState.errors.amount?.message}>
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

function RecipientSelect({ beneficiaries, onSelect }: { beneficiaries: Beneficiary[]; onSelect: (beneficiary: Beneficiary) => void }) {
  return (
    <Select
      defaultValue=""
      onChange={(event) => {
        const beneficiary = beneficiaries.find((item) => item.beneficiaryId === event.target.value);
        if (beneficiary) {
          onSelect(beneficiary);
        }
      }}
    >
      <option value="">Manual destination</option>
      {beneficiaries.map((beneficiary) => (
        <option key={beneficiary.beneficiaryId} value={beneficiary.beneficiaryId}>
          {beneficiary.displayName} - Account {beneficiary.destinationAccountId}
        </option>
      ))}
    </Select>
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
