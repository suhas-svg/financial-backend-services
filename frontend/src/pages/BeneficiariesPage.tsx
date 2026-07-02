import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { Badge, Button, EmptyState, ErrorNotice, Field, Input, Panel, Select } from "../components/ui";
import { createBeneficiary, disableBeneficiary, listBeneficiaries, updateBeneficiary } from "../lib/queries";
import { beneficiarySchema, type BeneficiaryValues } from "../lib/schemas";
import type { Beneficiary } from "../types";

const defaultValues: BeneficiaryValues = {
  displayName: "",
  destinationAccountId: "",
  currency: "USD",
  nickname: "",
  notes: ""
};

export function BeneficiariesPage() {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const form = useForm<BeneficiaryValues>({ resolver: zodResolver(beneficiarySchema), defaultValues });
  const beneficiaries = useQuery({ queryKey: ["beneficiaries", "ACTIVE"], queryFn: () => listBeneficiaries({ status: "ACTIVE" }) });
  const selected = useMemo(
    () => beneficiaries.data?.content.find((beneficiary) => beneficiary.beneficiaryId === selectedId) ?? beneficiaries.data?.content[0] ?? null,
    [beneficiaries.data, selectedId]
  );

  useEffect(() => {
    if (!selectedId && selected) {
      setSelectedId(selected.beneficiaryId);
    }
  }, [selected, selectedId]);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["beneficiaries"] });
  const createMutation = useMutation({
    mutationFn: createBeneficiary,
    onSuccess: (beneficiary) => {
      setSelectedId(beneficiary.beneficiaryId);
      form.reset(defaultValues);
      invalidate();
    }
  });
  const updateMutation = useMutation({
    mutationFn: (values: BeneficiaryValues) => updateBeneficiary(editingId!, values),
    onSuccess: (beneficiary) => {
      setSelectedId(beneficiary.beneficiaryId);
      setEditingId(null);
      form.reset(defaultValues);
      invalidate();
    }
  });
  const disableMutation = useMutation({
    mutationFn: disableBeneficiary,
    onSuccess: () => {
      setSelectedId(null);
      setEditingId(null);
      form.reset(defaultValues);
      invalidate();
    }
  });

  const startEdit = (beneficiary: Beneficiary) => {
    setEditingId(beneficiary.beneficiaryId);
    setSelectedId(beneficiary.beneficiaryId);
    form.reset({
      displayName: beneficiary.displayName,
      destinationAccountId: beneficiary.destinationAccountId,
      currency: beneficiary.currency,
      nickname: beneficiary.nickname ?? "",
      notes: beneficiary.notes ?? ""
    });
  };

  const submit = (values: BeneficiaryValues) => {
    if (editingId) {
      updateMutation.mutate(values);
    } else {
      createMutation.mutate(values);
    }
  };

  const error = errorMessage(beneficiaries.error) || errorMessage(createMutation.error) || errorMessage(updateMutation.error) || errorMessage(disableMutation.error);

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Recipients</h1>
        <p className="text-sm text-muted">{beneficiaries.data?.totalElements ?? 0} active recipients</p>
      </div>
      <ErrorNotice message={error} />
      <div className="grid gap-6 xl:grid-cols-[380px_1fr]">
        <Panel title={editingId ? "Edit recipient" : "Save recipient"}>
          <form className="grid gap-4" onSubmit={form.handleSubmit(submit)}>
            <Field label="Display name" error={form.formState.errors.displayName?.message}>
              <Input {...form.register("displayName")} />
            </Field>
            <Field label="Destination account ID" error={form.formState.errors.destinationAccountId?.message}>
              <Input {...form.register("destinationAccountId")} disabled={Boolean(editingId)} />
            </Field>
            <Field label="Currency" error={form.formState.errors.currency?.message}>
              <Select {...form.register("currency")} disabled={Boolean(editingId)}>
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
                <option value="GBP">GBP</option>
              </Select>
            </Field>
            <Field label="Nickname" error={form.formState.errors.nickname?.message}>
              <Input {...form.register("nickname")} />
            </Field>
            <Field label="Notes" error={form.formState.errors.notes?.message}>
              <Input {...form.register("notes")} />
            </Field>
            <div className="flex gap-2">
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
                {editingId ? "Update recipient" : "Save recipient"}
              </Button>
              {editingId ? <Button type="button" variant="secondary" onClick={() => { setEditingId(null); form.reset(defaultValues); }}>Cancel</Button> : null}
            </div>
          </form>
        </Panel>

        <div className="grid gap-6">
          <Panel title="Saved recipients">
            {beneficiaries.isLoading ? <p className="text-sm text-muted">Loading recipients...</p> : null}
            {!beneficiaries.isLoading && !beneficiaries.data?.content.length ? <EmptyState title="No recipients" detail="Save a trusted recipient to reuse it in transfer flows." /> : null}
            {beneficiaries.data?.content.length ? (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-line text-xs uppercase text-muted">
                    <tr>
                      <th className="py-2">Recipient</th>
                      <th>Destination</th>
                      <th>Currency</th>
                      <th>Status</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {beneficiaries.data.content.map((beneficiary) => (
                      <tr key={beneficiary.beneficiaryId} className="border-b border-line last:border-0">
                        <td className="py-3 font-medium">{beneficiary.displayName}</td>
                        <td>{beneficiary.destinationAccountId}</td>
                        <td>{beneficiary.currency}</td>
                        <td><Badge tone={beneficiary.status === "ACTIVE" ? "good" : "neutral"}>{beneficiary.status}</Badge></td>
                        <td className="flex justify-end gap-2 py-2">
                          <Button variant="secondary" onClick={() => startEdit(beneficiary)}>Edit {beneficiary.beneficiaryId}</Button>
                          <Button variant="danger" onClick={() => disableMutation.mutate(beneficiary.beneficiaryId)} disabled={disableMutation.isPending}>Disable {beneficiary.beneficiaryId}</Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}
          </Panel>

          <Panel title="Recipient detail">
            {selected ? (
              <div className="grid gap-2 text-sm">
                <p><span className="font-medium">Name:</span> {selected.displayName}</p>
                <p><span className="font-medium">Destination:</span> Account {selected.destinationAccountId}</p>
                <p><span className="font-medium">Currency:</span> {selected.currency}</p>
                {selected.nickname ? <p><span className="font-medium">Nickname:</span> {selected.nickname}</p> : null}
                {selected.notes ? <p><span className="font-medium">Notes:</span> {selected.notes}</p> : null}
              </div>
            ) : <EmptyState title="No recipient selected" detail="Select or create a recipient to inspect its details." />}
          </Panel>
        </div>
      </div>
    </div>
  );
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : undefined;
}
