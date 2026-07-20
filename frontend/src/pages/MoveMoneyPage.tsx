import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { UseFormRegisterReturn } from "react-hook-form";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { authorizeTransfer, cancelTransferAuthorization, listAccounts, listBeneficiaries, transfer, verifyStepUpChallenge, withdraw } from "../lib/queries";
import { createIdempotencyKey } from "../lib/idempotency";
import { availableBalance, canDebit } from "../lib/accountBalances";
import { moneyMovementSchema, transferSchema, type MoneyMovementValues, type TransferValues } from "../lib/schemas";
import { Button, ErrorNotice, Field, Input, Panel, Select, StatusNotice } from "../components/ui";
import type { Beneficiary, Transaction } from "../types";

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
  const withdrawForm = useForm<MoneyMovementValues>({ resolver: zodResolver(moneyMovementSchema), defaultValues: { accountId: "", amount: 0, currency: "USD", description: "", reference: "" } });
  const transferForm = useForm<TransferValues>({ resolver: zodResolver(transferSchema), defaultValues: { fromAccountId: "", toAccountId: "", beneficiaryId: "", amount: 0, currency: "USD", description: "", reference: "" } });
  const [pendingAuthorization, setPendingAuthorization] = useState<Transaction>();
  const [verificationCode, setVerificationCode] = useState("");
  const [withdrawStatus, setWithdrawStatus] = useState<string>();
  const [transferStatus, setTransferStatus] = useState<string>();
  const beneficiaries = useQuery({ queryKey: ["beneficiaries", "ACTIVE"], queryFn: () => listBeneficiaries({ status: "ACTIVE" }) });
  const withdrawAmount = Number(withdrawForm.watch("amount") || 0);
  const transferAmount = Number(transferForm.watch("amount") || 0);
  const destinationField = transferForm.register("toAccountId");
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["accounts"] });
    queryClient.invalidateQueries({ queryKey: ["transactions"] });
    queryClient.invalidateQueries({ queryKey: ["stats"] });
  };
  const withdrawMutation = useMutation({
    mutationFn: (values: MoneyMovementValues) => withdraw(values, createIdempotencyKey("withdraw")),
    onMutate: () => setWithdrawStatus(undefined),
    onSuccess: (result, values) => {
      setWithdrawStatus(`Withdrawal complete. ${formatMoney(result.amount, result.currency)} was withdrawn from account #${values.accountId}.`);
      withdrawForm.reset();
      invalidate();
    }
  });
  const transferMutation = useMutation({
    mutationFn: (values: TransferValues) => transfer(values, createIdempotencyKey("transfer")),
    onMutate: () => setTransferStatus(undefined),
    onSuccess: (result) => {
      if (result.authorizationRequired) {
        setPendingAuthorization(result);
        setTransferStatus("Transfer is awaiting additional verification. No money has moved yet.");
      } else {
        setTransferStatus(`Transfer complete. ${formatMoney(result.amount, result.currency)} was sent from account #${result.fromAccountId} to account #${result.toAccountId}.`);
        transferForm.reset();
        invalidate();
      }
    }
  });
  const authorizeMutation = useMutation({
    mutationFn: async () => {
      if (!pendingAuthorization?.authorizationChallengeId) throw new Error("Authorization challenge is missing");
      const verified = await verifyStepUpChallenge(pendingAuthorization.authorizationChallengeId, verificationCode);
      return authorizeTransfer(pendingAuthorization.transactionId, verified.proof);
    },
    onSuccess: (result) => {
      setPendingAuthorization(undefined);
      setVerificationCode("");
      setTransferStatus(`Transfer complete. ${formatMoney(result.amount, result.currency)} was sent from account #${result.fromAccountId} to account #${result.toAccountId}.`);
      transferForm.reset();
      invalidate();
    }
  });
  const cancelMutation = useMutation({
    mutationFn: () => cancelTransferAuthorization(pendingAuthorization!.transactionId),
    onSuccess: () => { setPendingAuthorization(undefined); setVerificationCode(""); }
  });

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <Panel title="Withdraw">
        <form className="grid gap-4" onSubmit={withdrawForm.handleSubmit((values) => withdrawMutation.mutate(values))}>
          <ErrorNotice message={withdrawMutation.error instanceof Error ? withdrawMutation.error.message : undefined} />
          <StatusNotice
            pending={withdrawMutation.isPending}
            message={withdrawMutation.isPending ? "Checking withdrawal request. You do not need to submit it again." : withdrawStatus}
          />
          <Field label="Withdraw account" error={withdrawForm.formState.errors.accountId?.message}>
            <AccountSelect field={withdrawForm.register("accountId")} debitSource amount={withdrawAmount} />
          </Field>
          <MoneyFields form={withdrawForm} />
          <Button type="submit" disabled={withdrawMutation.isPending}>{withdrawMutation.isPending ? "Checking withdrawal..." : "Withdraw"}</Button>
        </form>
      </Panel>
      <Panel title="Transfer">
        <form className="grid gap-4" onSubmit={transferForm.handleSubmit((values) => transferMutation.mutate(values))}>
          <ErrorNotice message={transferMutation.error instanceof Error ? transferMutation.error.message : undefined} />
          <StatusNotice pending={transferMutation.isPending || Boolean(pendingAuthorization)} message={transferMutation.isPending ? "Transfer is processing. You do not need to submit it again." : transferStatus} />
          <Field label="From account" error={transferForm.formState.errors.fromAccountId?.message}>
            <AccountSelect field={transferForm.register("fromAccountId")} debitSource amount={transferAmount} />
          </Field>
          <Field label="Saved recipient">
            <RecipientSelect
              beneficiaries={beneficiaries.data?.content ?? []}
              onSelect={(beneficiary) => {
                transferForm.setValue("beneficiaryId", beneficiary?.beneficiaryId ?? "", { shouldValidate: true });
                if (beneficiary) {
                  transferForm.setValue("toAccountId", beneficiary.destinationAccountId, { shouldValidate: true });
                  transferForm.setValue("currency", beneficiary.currency, { shouldValidate: true });
                }
              }}
            />
          </Field>
          <input type="hidden" {...transferForm.register("beneficiaryId")} />
          <Field label="To account" error={transferForm.formState.errors.toAccountId?.message}>
            <Input
              className="mt-2"
              placeholder="Manual destination account"
              {...destinationField}
              onChange={(event) => {
                destinationField.onChange(event);
                transferForm.setValue("beneficiaryId", "");
              }}
            />
          </Field>
          <Field label="Transfer amount" error={transferForm.formState.errors.amount?.message}>
            <Input type="number" step="0.01" {...transferForm.register("amount")} />
          </Field>
          <Field label="Currency" error={transferForm.formState.errors.currency?.message}>
            <Select {...transferForm.register("currency")}>
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
              <option value="INR">INR</option>
            </Select>
          </Field>
          <Field label="Description" error={transferForm.formState.errors.description?.message}>
            <Input {...transferForm.register("description")} />
          </Field>
          <Field label="Reference" error={transferForm.formState.errors.reference?.message}>
            <Input {...transferForm.register("reference")} />
          </Field>
          <Button type="submit" disabled={transferMutation.isPending}>{transferMutation.isPending ? "Processing transfer..." : "Transfer"}</Button>
        </form>
        {pendingAuthorization ? (
          <div role="dialog" aria-modal="true" aria-labelledby="transfer-verification-title" className="mt-4 grid gap-3 rounded-md border border-amber-200 bg-amber-50 p-4">
            <h3 id="transfer-verification-title" className="font-semibold">Verify this transfer</h3>
            <p className="text-sm">This transfer needs additional verification before any money moves.</p>
            {pendingAuthorization.authorizationReasons?.length ? <p className="text-xs text-muted">Checks: {pendingAuthorization.authorizationReasons.map(formatReason).join(", ")}</p> : null}
            <ErrorNotice message={authorizeMutation.error instanceof Error ? authorizeMutation.error.message : undefined} />
            <Field label="Authenticator or recovery code">
              <Input autoFocus autoComplete="one-time-code" value={verificationCode} onChange={(event) => setVerificationCode(event.target.value)} />
            </Field>
            <div className="flex gap-2">
              <Button disabled={!verificationCode || authorizeMutation.isPending} onClick={() => authorizeMutation.mutate()}>Verify and transfer</Button>
              <Button variant="secondary" disabled={cancelMutation.isPending} onClick={() => cancelMutation.mutate()}>Cancel</Button>
            </div>
          </div>
        ) : null}
      </Panel>
    </div>
  );
}

function RecipientSelect({ beneficiaries, onSelect }: { beneficiaries: Beneficiary[]; onSelect: (beneficiary?: Beneficiary) => void }) {
  return (
    <Select
      defaultValue=""
      onChange={(event) => {
        const beneficiary = beneficiaries.find((item) => item.beneficiaryId === event.target.value);
        onSelect(beneficiary);
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

function formatReason(reason: string) {
  return reason.toLowerCase().replace(/_/g, " ");
}

function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat(currency === "INR" ? "en-IN" : "en-US", { style: "currency", currency }).format(amount);
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
          <option value="INR">INR</option>
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
