import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { UseFormRegisterReturn } from "react-hook-form";
import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { authorizeTransfer, cancelTransferAuthorization, deposit, getDepositCapability, getTransferAuthorization, listBeneficiaries, listOwnedAccounts, transfer, verifyStepUpChallenge, withdraw } from "../lib/queries";
import { createIdempotencyKey } from "../lib/idempotency";
import { availableBalance, canDebit } from "../lib/accountBalances";
import { moneyMovementSchema, transferSchema, type MoneyMovementValues, type TransferValues } from "../lib/schemas";
import { invalidateMoneyMovementQueries, MONEY_STATE_REFRESH_INTERVAL_MS } from "../lib/queryInvalidation";
import { Button, ErrorNotice, Field, Input, Panel, Select, StatusNotice } from "../components/ui";
import { ApiError } from "../lib/api";
import type { Beneficiary, Transaction } from "../types";
import { Link } from "../routing";

const AUTHORIZATION_STATUS_TIMEOUT_MS = 60_000;

function AccountSelect({ field, debitSource = false, amount = 0 }: { field: UseFormRegisterReturn; debitSource?: boolean; amount?: number }) {
  const accounts = useQuery({ queryKey: ["accounts", "owned"], queryFn: () => listOwnedAccounts(), refetchInterval: MONEY_STATE_REFRESH_INTERVAL_MS });
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
  const transferForm = useForm<TransferValues>({ resolver: zodResolver(transferSchema), defaultValues: { fromAccountId: "", toAccountId: "", beneficiaryId: "", amount: 0, currency: "USD", description: "", reference: "" } });
  const [pendingAuthorization, setPendingAuthorization] = useState<Transaction>();
  const [verificationCode, setVerificationCode] = useState("");
  const [depositStatus, setDepositStatus] = useState<string>();
  const [withdrawStatus, setWithdrawStatus] = useState<string>();
  const [transferStatus, setTransferStatus] = useState<string>();
  const [authorizationWaitStartedAt, setAuthorizationWaitStartedAt] = useState<number>();
  const [authorizationStatusTimedOut, setAuthorizationStatusTimedOut] = useState(false);
  const depositCapability = useQuery({ queryKey: ["deposit-capability"], queryFn: getDepositCapability, retry: false });
  const authorizationStatus = useQuery({
    queryKey: ["transfer-authorization", pendingAuthorization?.transactionId],
    queryFn: () => getTransferAuthorization(pendingAuthorization!.transactionId),
    enabled: Boolean(pendingAuthorization),
    refetchInterval: pendingAuthorization && !authorizationStatusTimedOut ? 5000 : false
  });
  const beneficiaries = useQuery({ queryKey: ["beneficiaries", "ACTIVE"], queryFn: () => listBeneficiaries({ status: "ACTIVE" }) });
  const withdrawAmount = Number(withdrawForm.watch("amount") || 0);
  const transferAmount = Number(transferForm.watch("amount") || 0);
  const destinationField = transferForm.register("toAccountId");
  const invalidate = useCallback(() => {
    invalidateMoneyMovementQueries(queryClient);
  }, [queryClient]);
  useEffect(() => {
    if (!pendingAuthorization || !authorizationWaitStartedAt || authorizationStatusTimedOut) return;
    const remaining = Math.max(0, AUTHORIZATION_STATUS_TIMEOUT_MS - (Date.now() - authorizationWaitStartedAt));
    const timeout = window.setTimeout(() => setAuthorizationStatusTimedOut(true), remaining);
    return () => window.clearTimeout(timeout);
  }, [authorizationStatusTimedOut, authorizationWaitStartedAt, pendingAuthorization]);
  useEffect(() => {
    const result = authorizationStatus.data;
    if (!result || !pendingAuthorization) return;
    if (result.status === "COMPLETED") {
      setPendingAuthorization(undefined);
      setAuthorizationWaitStartedAt(undefined);
      setAuthorizationStatusTimedOut(false);
      setVerificationCode("");
      setTransferStatus(`Transfer complete. ${formatMoney(result.amount, result.currency)} was sent from account #${result.fromAccountId} to account #${result.toAccountId}.`);
      transferForm.reset();
      invalidate();
    } else if (result.status === "FAILED" || result.status === "CANCELLED") {
      setPendingAuthorization(undefined);
      setAuthorizationWaitStartedAt(undefined);
      setAuthorizationStatusTimedOut(false);
      setVerificationCode("");
      setTransferStatus(result.status === "FAILED"
        ? "Transfer authorization failed. No money moved. You can start a new transfer."
        : "Transfer authorization cancelled. No money moved.");
      invalidate();
    }
  }, [authorizationStatus.data, invalidate, pendingAuthorization, transferForm]);
  const depositMutation = useMutation({
    mutationFn: (values: MoneyMovementValues) => deposit(values, createIdempotencyKey("deposit")),
    onMutate: () => setDepositStatus(undefined),
    onSuccess: (result, values) => {
      setDepositStatus(`Deposit complete. ${formatMoney(result.amount, result.currency)} was added to account #${values.accountId}.`);
      depositForm.reset();
      invalidate();
    },
    onError: () => invalidate()
  });
  const withdrawMutation = useMutation({
    mutationFn: (values: MoneyMovementValues) => withdraw(values, createIdempotencyKey("withdraw")),
    onMutate: () => setWithdrawStatus(undefined),
    onSuccess: (result, values) => {
      setWithdrawStatus(`Withdrawal complete. ${formatMoney(result.amount, result.currency)} was withdrawn from account #${values.accountId}.`);
      withdrawForm.reset();
      invalidate();
    },
    onError: () => invalidate()
  });
  const transferMutation = useMutation({
    mutationFn: (values: TransferValues) => transfer(values, createIdempotencyKey("transfer")),
    onMutate: () => setTransferStatus(undefined),
    onSuccess: (result) => {
      if (result.authorizationRequired) {
        setPendingAuthorization(result);
        setAuthorizationWaitStartedAt(Date.now());
        setAuthorizationStatusTimedOut(false);
        setTransferStatus("Transfer is awaiting additional verification. No money has moved yet.");
      } else {
        setTransferStatus(`Transfer complete. ${formatMoney(result.amount, result.currency)} was sent from account #${result.fromAccountId} to account #${result.toAccountId}.`);
        transferForm.reset();
        invalidate();
      }
    },
    onError: (error) => {
      setTransferStatus(isMfaEnrollmentRequired(error)
        ? undefined
        : "Transfer could not be confirmed. No money was moved; check Transactions before retrying.");
      invalidate();
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
      setAuthorizationWaitStartedAt(undefined);
      setAuthorizationStatusTimedOut(false);
      setVerificationCode("");
      setTransferStatus(`Transfer complete. ${formatMoney(result.amount, result.currency)} was sent from account #${result.fromAccountId} to account #${result.toAccountId}.`);
      transferForm.reset();
      invalidate();
    },
    onError: (error) => {
      setVerificationCode("");
      if (isDeterministicMovementFailure(error)) {
        setPendingAuthorization(undefined);
        setAuthorizationWaitStartedAt(undefined);
        setAuthorizationStatusTimedOut(false);
        setTransferStatus("Transfer was declined. No money moved. Start a new transfer after reviewing your available balance and limits.");
        invalidate();
      } else {
        setTransferStatus("Verification was accepted, but processing needs recovery. Do not submit another transfer; refresh the authorization status or check Transactions.");
      }
    }
  });
  const cancelMutation = useMutation({
    mutationFn: () => cancelTransferAuthorization(pendingAuthorization!.transactionId),
    onSuccess: () => {
      setPendingAuthorization(undefined);
      setAuthorizationWaitStartedAt(undefined);
      setAuthorizationStatusTimedOut(false);
      setVerificationCode("");
      setTransferStatus("Transfer authorization cancelled. No money moved.");
      invalidate();
    }
  });

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <Panel title="Deposit">
        <div className="grid gap-4">
          {depositCapability.isLoading ? <StatusNotice pending message="Checking whether a funding provider is available..." /> : null}
          {depositCapability.data?.enabled ? (
            <>
              <p className="text-sm text-muted">Controlled synthetic beta funding only. This does not connect to a real bank or payment rail.</p>
              <form className="grid gap-4" onSubmit={depositForm.handleSubmit((values) => depositMutation.mutate(values))}>
                <ErrorNotice message={friendlyMovementError(depositMutation.error, "deposit")} />
                <StatusNotice pending={depositMutation.isPending} message={depositMutation.isPending ? "Checking deposit request. You do not need to submit it again." : depositStatus} />
                <Field label="Deposit account" error={depositForm.formState.errors.accountId?.message}>
                  <AccountSelect field={depositForm.register("accountId")} />
                </Field>
                <MoneyFields form={depositForm} />
                <Button type="submit" disabled={depositMutation.isPending}>{depositMutation.isPending ? "Processing deposit..." : "Deposit"}</Button>
              </form>
            </>
          ) : (
            <p aria-live="polite" className="text-sm text-muted">{depositCapability.data?.message ?? "Customer deposits are unavailable until a funding provider is activated."}</p>
          )}
          {depositCapability.isError ? <ErrorNotice message="Funding capability could not be confirmed. No deposit was attempted." /> : null}
        </div>
      </Panel>
      <Panel title="Withdraw">
        <form className="grid gap-4" onSubmit={withdrawForm.handleSubmit((values) => withdrawMutation.mutate(values))}>
          <ErrorNotice message={friendlyMovementError(withdrawMutation.error, "withdrawal")} />
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
          <ErrorNotice message={friendlyMovementError(transferMutation.error, "transfer")} />
          <StatusNotice
            pending={transferMutation.isPending || Boolean(pendingAuthorization)}
            message={transferMutation.isPending
              ? "Transfer is processing. You do not need to submit it again."
              : authorizationStatusTimedOut
                ? "Authorization status is taking longer than expected. Do not submit another transfer; refresh status or check Transactions."
                : transferStatus}
          />
          {isMfaEnrollmentRequired(transferMutation.error) ? (
            <p role="status" className="text-sm text-amber-800">
              <Link className="font-semibold underline" to="/security">Open Security and set up an authenticator</Link>, then start a new transfer.
            </p>
          ) : null}
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
            {authorizationStatusTimedOut ? (
              <StatusNotice
                message="Status polling paused after 60 seconds. The original idempotency key remains protected; refresh the authorization or review Transactions before retrying."
              />
            ) : null}
            <ErrorNotice message={friendlyMovementError(authorizeMutation.error, "transfer authorization")} />
            {authorizationStatus.data?.processingState === "AUTHORIZATION_ACCEPTED_RETRYABLE" ? <p role="status" className="text-sm text-amber-800">Verification was accepted. The transfer is still protected by its idempotency key; do not submit a duplicate.</p> : null}
            <Field label="Authenticator or recovery code">
              <Input autoFocus autoComplete="one-time-code" value={verificationCode} onChange={(event) => setVerificationCode(event.target.value)} />
            </Field>
            <div className="flex gap-2">
              <Button disabled={!verificationCode || authorizeMutation.isPending} onClick={() => authorizeMutation.mutate()}>Verify and transfer</Button>
              <Button variant="secondary" disabled={cancelMutation.isPending} onClick={() => cancelMutation.mutate()}>Cancel</Button>
              {authorizationStatusTimedOut ? (
                <Button
                  variant="secondary"
                  disabled={authorizationStatus.isFetching}
                  onClick={() => {
                    setAuthorizationStatusTimedOut(false);
                    setAuthorizationWaitStartedAt(Date.now());
                    void authorizationStatus.refetch();
                  }}
                >
                  Refresh authorization status
                </Button>
              ) : null}
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

function isDeterministicMovementFailure(error: unknown) {
  if (!(error instanceof ApiError)) return false;
  if (error.status === 400 || error.status === 409) return true;
  const message = `${error.message} ${JSON.stringify(error.payload)}`.toLowerCase();
  return message.includes("insufficient") || message.includes("limit") || message.includes("not found");
}

function friendlyMovementError(error: unknown, operation: string) {
  if (!error) return undefined;
  if (error instanceof ApiError) {
    const payload = error.payload;
    const payloadText = typeof payload === "object" && payload !== null
      ? Object.values(payload as Record<string, unknown>).filter((value) => typeof value === "string").join(" ")
      : "";
    const combined = `${error.message} ${payloadText}`.toLowerCase();
    if (isMfaEnrollmentRequired(error)) {
      return "Additional verification is required, but this profile has no authenticator. No money moved.";
    }
    if (combined.includes("insufficient")) return "Insufficient funds. No money moved. Your available balance was unchanged.";
    if (error.status === 409) return `This ${operation} is already being processed. Check Transactions before retrying.`;
    if (error.status >= 500) return `The ${operation} could not be confirmed. No new submission was made; check Transactions before retrying.`;
    return error.message;
  }
  return error instanceof Error ? error.message : `The ${operation} could not be confirmed.`;
}

function isMfaEnrollmentRequired(error: unknown) {
  if (!(error instanceof ApiError)) return false;
  const payload = error.payload;
  const payloadText = typeof payload === "object" && payload !== null
    ? Object.values(payload as Record<string, unknown>).filter((value) => typeof value === "string").join(" ")
    : String(payload ?? "");
  return `${error.message} ${payloadText}`.toUpperCase().includes("MFA_ENROLLMENT_REQUIRED");
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
