import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import { addRiskCaseNote, cancelScheduledTransfer, claimRiskCase, createRiskCaseFromAlert, createScheduledTransfer, exportInvestigationTimelineCsv, getCustomerJournal, getInvestigationSummary, getInvestigationTimeline, getScheduledTransfer, listLedgerAccounts, listScheduledTransferRuns, listScheduledTransfers, pauseScheduledTransfer, resumeScheduledTransfer, searchAuditEvents, searchRiskAlerts, searchRiskCases, updateAccountStatus, updateRiskAlertStatus, updateRiskCaseStatus } from "./queries";
import { clearSession, saveSession } from "./session";

function tokenFor(payload: object) {
  const encoded = btoa(JSON.stringify(payload)).replace(/=/g, "");
  return `header.${encoded}.signature`;
}

describe("apiRequest", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    clearSession();
  });

  it("maps account requests to account proxy and attaches bearer token", async () => {
    saveSession(tokenFor({ sub: "alex", roles: ["ROLE_USER"] }));
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200, headers: { "Content-Type": "application/json" } })
    );

    await apiRequest("account", "/api/accounts");

    expect(fetchMock).toHaveBeenCalledWith(
      "/account-api/api/accounts",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: expect.stringContaining("Bearer header.") })
      })
    );
  });

  it("maps transaction requests to transaction proxy and includes idempotency key", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ transactionId: "t-1" }), {
        status: 201,
        headers: { "Content-Type": "application/json" }
      })
    );

    await apiRequest("transaction", "/api/transactions/deposit", {
      method: "POST",
      idempotencyKey: "idem-1",
      body: { accountId: "1", amount: 10 }
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/transactions/deposit",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "Idempotency-Key": "idem-1" }),
        body: JSON.stringify({ accountId: "1", amount: 10 })
      })
    );
  });

  it("supports backend login response shape with accessToken", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ accessToken: "jwt-token" }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    );

    await expect(apiRequest("account", "/api/auth/login", { method: "POST", body: { username: "u", password: "p" } })).resolves.toEqual({
      accessToken: "jwt-token"
    });
  });

  it("patches account status through the account proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ id: 101, status: "FROZEN" }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    );

    await updateAccountStatus(101, { status: "FROZEN", reason: "Fraud review" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/account-api/api/accounts/101/status",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ status: "FROZEN", reason: "Fraud review" })
      })
    );
  });

  it("maps audit search requests to the transaction proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ content: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    );

    await searchAuditEvents({ userId: "customer", outcome: "FAILURE" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/audit/events?size=20&sort=createdAt%2Cdesc&userId=customer&outcome=FAILURE",
      expect.any(Object)
    );
  });

  it("maps risk alert search requests to the transaction proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ content: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    );

    await searchRiskAlerts({ userId: "customer", status: "OPEN" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/risk/alerts?size=20&sort=createdAt%2Cdesc&userId=customer&status=OPEN",
      expect.any(Object)
    );
  });

  it("patches risk alert status through the transaction proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ alertId: "alert-1", status: "ESCALATED" }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    );

    await updateRiskAlertStatus("alert-1", { status: "ESCALATED", resolutionNote: "Review with fraud ops" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/risk/alerts/alert-1/status",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ status: "ESCALATED", resolutionNote: "Review with fraud ops" })
      })
    );
  });

  it("maps risk case search requests to the transaction proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ content: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    );

    await searchRiskCases({ userId: "customer", status: "OPEN", assignedTo: "UNASSIGNED" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/risk/cases?size=20&sort=createdAt%2Cdesc&userId=customer&status=OPEN&assignedTo=UNASSIGNED",
      expect.any(Object)
    );
  });

  it("creates, claims, updates, and notes risk cases through the transaction proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() => Promise.resolve(
      new Response(JSON.stringify({ caseId: "case-1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    ));

    await createRiskCaseFromAlert("alert-1", { title: "Review high-value transfer", priority: "HIGH", reason: "Manual review" });
    await claimRiskCase("case-1");
    await updateRiskCaseStatus("case-1", { status: "RESOLVED", resolutionNote: "Reviewed" });
    await addRiskCaseNote("case-1", { note: "Internal note" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/risk/cases/from-alert/alert-1",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ title: "Review high-value transfer", priority: "HIGH", reason: "Manual review" })
      })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/risk/cases/case-1/claim",
      expect.objectContaining({ method: "PATCH" })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/risk/cases/case-1/status",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ status: "RESOLVED", resolutionNote: "Reviewed" })
      })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/risk/cases/case-1/notes",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ note: "Internal note" })
      })
    );
  });

  it("maps investigation timeline and summary requests to the transaction proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() => Promise.resolve(
      new Response(JSON.stringify({ content: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    ));

    await getInvestigationTimeline({ userId: "customer", transactionId: "txn-1", accountId: "101", alertId: "alert-1", caseId: "case-1" });
    await getInvestigationSummary({ userId: "customer", transactionId: "txn-1" });
    await exportInvestigationTimelineCsv({ userId: "customer", transactionId: "txn-1", accountId: "101", alertId: "alert-1", caseId: "case-1" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/investigations/timeline?size=50&sort=createdAt%2Cdesc&userId=customer&transactionId=txn-1&accountId=101&alertId=alert-1&caseId=case-1",
      expect.any(Object)
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/investigations/summary?userId=customer&transactionId=txn-1",
      expect.any(Object)
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/investigations/export?userId=customer&transactionId=txn-1&accountId=101&alertId=alert-1&caseId=case-1",
      expect.any(Object)
    );
  });

  it("maps customer ledger projection and journal requests to the transaction proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() => Promise.resolve(
      new Response(JSON.stringify([]), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    ));

    await listLedgerAccounts();
    await getCustomerJournal("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/ledger/accounts",
      expect.any(Object)
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/transaction-api/api/ledger/journals/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      expect.any(Object)
    );
  });

  it("maps scheduled transfer requests to the transaction proxy", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() => Promise.resolve(
      new Response(JSON.stringify({ content: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    ));

    await createScheduledTransfer({
      fromAccountId: "101",
      toAccountId: "202",
      amount: 75,
      currency: "USD",
      description: "Rent",
      reference: "JULY",
      scheduleType: "RECURRING",
      frequency: "MONTHLY",
      firstRunAt: "2026-07-15T10:00"
    });
    await listScheduledTransfers({ status: "ACTIVE" });
    await getScheduledTransfer("schedule-1");
    await pauseScheduledTransfer("schedule-1");
    await resumeScheduledTransfer("schedule-1");
    await cancelScheduledTransfer("schedule-1");
    await listScheduledTransferRuns("schedule-1");

    expect(fetchMock).toHaveBeenCalledWith("/transaction-api/api/scheduled-transfers", expect.objectContaining({ method: "POST" }));
    const createBody = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(createBody).toEqual(expect.objectContaining({ firstRunAt: new Date("2026-07-15T10:00").toISOString() }));
    expect(createBody).not.toHaveProperty("endAt");
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/transaction-api/api/scheduled-transfers?"), expect.anything());
    expect(fetchMock).toHaveBeenCalledWith("/transaction-api/api/scheduled-transfers/schedule-1", expect.any(Object));
    expect(fetchMock).toHaveBeenCalledWith("/transaction-api/api/scheduled-transfers/schedule-1/pause", expect.objectContaining({ method: "PATCH" }));
    expect(fetchMock).toHaveBeenCalledWith("/transaction-api/api/scheduled-transfers/schedule-1/resume", expect.objectContaining({ method: "PATCH" }));
    expect(fetchMock).toHaveBeenCalledWith("/transaction-api/api/scheduled-transfers/schedule-1", expect.objectContaining({ method: "DELETE" }));
    expect(fetchMock).toHaveBeenCalledWith("/transaction-api/api/scheduled-transfers/schedule-1/runs", expect.any(Object));
  });
});
