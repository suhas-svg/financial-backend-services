import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { AuthProvider } from "../state/AuthProvider";

const emptyPage = { content: [], number: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true };
const sampleAccount = {
  id: 101,
  ownerId: "customer",
  accountType: "CHECKING",
  balance: 250,
  ledgerBalance: 250,
  availableBalance: 175,
  createdAt: "2026-04-28T10:00:00Z",
  status: "ACTIVE"
};

const sampleLedgerAccount = {
  externalAccountId: "101",
  currency: "USD",
  postedBalance: 200,
  pendingBalance: 50,
  availableBalance: 175,
  projectionVersion: 7,
  updatedAt: "2026-06-25T07:00:00Z"
};

const frozenAccount = {
  ...sampleAccount,
  id: 202,
  ledgerBalance: 250,
  availableBalance: 250,
  status: "FROZEN",
  statusReason: "Fraud review"
};

function tokenFor(payload: Record<string, unknown>) {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "HS256", typ: "JWT" })}.${encode(payload)}.signature`;
}

function renderApp(route = "/", token?: string) {
  sessionStorage.clear();
  if (token) {
    sessionStorage.setItem("financial-console-token", token);
  }

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false }
    }
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[route]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
          <App />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}

function jsonResponse(payload: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(payload), {
      status,
      headers: { "Content-Type": "application/json" }
    })
  );
}

function mockFetch(handler?: (url: string, init?: RequestInit) => Promise<Response> | Response | undefined) {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, init });
    const handled = handler?.(url, init);
    if (handled) {
      return Promise.resolve(handled);
    }
    if (url.includes("/api/auth/login")) {
      return jsonResponse({ accessToken: tokenFor({ sub: "customer", roles: ["ROLE_USER"] }) });
    }
    if (url.includes("/api/auth/register")) {
      return jsonResponse({ username: "new_customer", roles: ["ROLE_USER"] }, 201);
    }
    if (url.includes("/api/accounts")) {
      return jsonResponse({ ...emptyPage, content: [sampleAccount], totalElements: 1, totalPages: 1 });
    }
    if (url.includes("/api/ledger/accounts")) {
      return jsonResponse([]);
    }
    if (url.includes("/api/notifications/summary")) {
      return jsonResponse({ total: 0, unread: 0, bySeverity: {}, byType: {}, bySourceType: {} });
    }
    if (url.includes("/api/notifications")) {
      return jsonResponse(emptyPage);
    }
    if (url.includes("/api/transactions/user/stats")) {
      return jsonResponse({ totalTransactions: 0, successRate: 0, transactionCountsByType: {} });
    }
    if (url.includes("/api/transactions/limits")) {
      return jsonResponse({ dailyLimit: 10000, monthlyLimit: 50000, singleTransactionLimit: 10000, currency: "USD" });
    }
    if (url.includes("/api/transactions/search") || url.includes("/api/transactions")) {
      return jsonResponse(emptyPage);
    }
    if (url.includes("/api/health") || url.includes("/api/monitoring")) {
      return jsonResponse({ status: "UP" });
    }
    if (url.includes("/api/audit/summary")) {
      return jsonResponse({ totalEvents: 0, failureEvents: 0, reversalEvents: 0, securityEvents: 0 });
    }
    if (url.includes("/api/audit/events")) {
      return jsonResponse(emptyPage);
    }
    if (url.includes("/api/risk/summary")) {
      return jsonResponse({ totalAlerts: 0, openAlerts: 0, highSeverityAlerts: 0, escalatedAlerts: 0 });
    }
    if (url.includes("/api/risk/cases/summary")) {
      return jsonResponse({ totalCases: 0, openCases: 0, inReviewCases: 0, resolvedCases: 0, closedCases: 0, unassignedCases: 0 });
    }
    if (url.includes("/api/risk/cases")) {
      return jsonResponse(emptyPage);
    }
    if (url.includes("/api/disputes/admin/summary")) {
      return jsonResponse({ totalDisputes: 0, openDisputes: 0, inReviewDisputes: 0, approvedDisputes: 0, deniedDisputes: 0, closedDisputes: 0, unassignedDisputes: 0 });
    }
    if (url.includes("/api/disputes")) {
      return jsonResponse(emptyPage);
    }
    if (url.includes("/api/investigations/summary")) {
      return jsonResponse({ transactions: 0, auditEvents: 0, riskAlerts: 0, riskCases: 0, disputes: 0, disputeNotes: 0, failures: 0, reversals: 0, highSeverityItems: 0 });
    }
    if (url.includes("/api/investigations/timeline")) {
      return jsonResponse(emptyPage);
    }
    if (url.includes("/api/risk/alerts")) {
      return jsonResponse(emptyPage);
    }
    return jsonResponse({});
  });
  vi.stubGlobal("fetch", fetchMock);
  return { fetchMock, calls };
}

beforeEach(() => {
  sessionStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  sessionStorage.clear();
});

describe("auth screens", () => {
  it("shows login success and failure states", async () => {
    const user = userEvent.setup();
    let loginAttempts = 0;
    mockFetch((url) => {
      if (url.includes("/api/auth/login")) {
        loginAttempts += 1;
        if (loginAttempts === 1) {
          return jsonResponse({ message: "Bad credentials" }, 401);
        }
        return jsonResponse({ accessToken: tokenFor({ sub: "customer", roles: ["ROLE_USER"] }) });
      }
      return undefined;
    });

    renderApp("/login");

    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Username is required")).toBeInTheDocument();

    const username = document.querySelector<HTMLInputElement>('input[name="username"]');
    const password = document.querySelector<HTMLInputElement>('input[name="password"]');
    expect(username).not.toBeNull();
    expect(password).not.toBeNull();
    await user.type(username!, "customer");
    await user.type(password!, "wrong");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Bad credentials")).toBeInTheDocument();

    await user.clear(password!);
    await user.type(password!, "password123");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("customer")).toBeInTheDocument();
    expect(screen.getByText("ROLE_USER")).toBeInTheDocument();
  });

  it("shows register success and failure states", async () => {
    const user = userEvent.setup();
    let registerAttempts = 0;
    mockFetch((url) => {
      if (url.includes("/api/auth/register")) {
        registerAttempts += 1;
        if (registerAttempts === 1) {
          return jsonResponse({ message: "Username already exists" }, 409);
        }
        return jsonResponse({ username: "new_customer", roles: ["ROLE_USER"] }, 201);
      }
      return undefined;
    });

    renderApp("/register");

    await user.type(screen.getByLabelText("Username"), "new_customer");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Register" }));
    expect(await screen.findByText("Username already exists")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Register" }));
    expect(await screen.findByText("Registered new_customer. You can sign in now.")).toBeInTheDocument();
  });
});

describe("account forms", () => {
  it("shows available balance as primary and ledger balance as secondary on dashboard", async () => {
    mockFetch();

    renderApp("/", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByText("Available balance")).toBeInTheDocument();
    expect((await screen.findAllByText("$175.00")).length).toBeGreaterThan(0);
    expect(await screen.findByText("Ledger $250.00")).toBeInTheDocument();
  });

  it("uses authoritative ledger projections on the dashboard and labels pending funds unavailable", async () => {
    const { calls } = mockFetch((url) => {
      if (url.includes("/api/accounts")) {
        return jsonResponse({
          ...emptyPage,
          content: [{ ...sampleAccount, balance: 999, ledgerBalance: 999, availableBalance: 999 }],
          totalElements: 1,
          totalPages: 1
        });
      }
      if (url.includes("/api/ledger/accounts")) {
        return jsonResponse([sampleLedgerAccount]);
      }
      return undefined;
    });

    renderApp("/", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByText("Available balance")).toBeInTheDocument();
    expect((await screen.findAllByText("$175.00")).length).toBeGreaterThan(0);
    expect(await screen.findByText("Posted $200.00")).toBeInTheDocument();
    expect(await screen.findByText("Pending $50.00 unavailable")).toBeInTheDocument();
    expect(calls.some(({ url }) => url.includes("/transaction-api/api/ledger/accounts"))).toBe(true);
  });

  it("keeps legacy account balances visible when ledger projections are unavailable during deployment skew", async () => {
    mockFetch((url) => {
      if (url.includes("/api/accounts")) {
        return jsonResponse({ ...emptyPage, content: [sampleAccount], totalElements: 1, totalPages: 1 });
      }
      if (url.includes("/api/ledger/accounts")) {
        return jsonResponse({ message: "Ledger warming up" }, 503);
      }
      return undefined;
    });

    renderApp("/", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByText("Available balance")).toBeInTheDocument();
    expect((await screen.findAllByText("$175.00")).length).toBeGreaterThan(0);
    expect(await screen.findByText("Ledger $250.00")).toBeInTheDocument();
  });

  it("shows posted, pending, and available authoritative balances on the accounts page", async () => {
    const user = userEvent.setup();
    mockFetch((url) => {
      if (url.includes("/api/accounts")) {
        return jsonResponse({
          ...emptyPage,
          content: [{ ...sampleAccount, balance: 999, ledgerBalance: 999, availableBalance: 999 }],
          totalElements: 1,
          totalPages: 1
        });
      }
      if (url.includes("/api/ledger/accounts")) {
        return jsonResponse([sampleLedgerAccount]);
      }
      return undefined;
    });

    renderApp("/accounts", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByText("Posted")).toBeInTheDocument();
    expect(await screen.findByText("Pending")).toBeInTheDocument();
    expect((await screen.findAllByText("$175.00")).length).toBeGreaterThan(0);
    expect(screen.getByText("$200.00")).toBeInTheDocument();
    expect(screen.getByText("$50.00")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "View account 101" }));
    expect(screen.getByText("Projection version")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
  });

  it("renders account type-specific fields", async () => {
    const user = userEvent.setup();
    mockFetch();

    renderApp("/accounts", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    const typeSelect = screen.getByLabelText("Type");
    expect(screen.queryByLabelText("Interest rate")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Credit limit")).not.toBeInTheDocument();

    await user.selectOptions(typeSelect, "SAVINGS");
    expect(screen.getByLabelText("Interest rate")).toBeInTheDocument();

    await user.selectOptions(typeSelect, "CREDIT");
    expect(screen.getByLabelText("Credit limit")).toBeInTheDocument();
    expect(screen.getByLabelText("Due date")).toBeInTheDocument();
  });

  it("shows frozen account warnings on customer account detail", async () => {
    const user = userEvent.setup();
    mockFetch((url) => {
      if (url.includes("/api/accounts")) {
        return jsonResponse({ ...emptyPage, content: [frozenAccount], totalElements: 1, totalPages: 1 });
      }
      return undefined;
    });

    renderApp("/accounts", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    await user.click(await screen.findByRole("button", { name: "View account 202" }));

    expect(screen.getAllByText("FROZEN").length).toBeGreaterThan(0);
    expect(screen.getByText("Fraud review")).toBeInTheDocument();
  });
});

describe("transaction filters", () => {
  it("updates transaction search query parameters from filters", async () => {
    const user = userEvent.setup();
    const { calls } = mockFetch();

    renderApp("/transactions", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    await user.type(screen.getByPlaceholderText("Account"), "101");
    await user.selectOptions(screen.getByDisplayValue("All types"), "DEPOSIT");
    await user.selectOptions(screen.getByDisplayValue("All status"), "COMPLETED");

    await waitFor(() => {
      expect(calls.some(({ url }) => url.includes("/transaction-api/api/transactions/search") && url.includes("accountId=101") && url.includes("type=DEPOSIT") && url.includes("status=COMPLETED"))).toBe(true);
    });
  });

  it("lets customers dispute eligible completed transactions from detail", async () => {
    const user = userEvent.setup();
    const transaction = sampleTransaction();
    const { calls } = mockFetch((url, init) => {
      if (url.includes("/api/transactions/txn-1")) {
        return jsonResponse(transaction);
      }
      if (url.includes("/api/transactions")) {
        return jsonResponse({ ...emptyPage, content: [transaction], totalElements: 1, totalPages: 1 });
      }
      if (url.includes("/api/disputes") && init?.method === "POST") {
        return jsonResponse(sampleDispute(), 201);
      }
      if (url.includes("/api/disputes")) {
        return jsonResponse({ ...emptyPage, content: [], totalElements: 0 });
      }
      return undefined;
    });

    renderApp("/transactions", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    await user.click(await screen.findByText("txn-1"));
    expect(await screen.findByRole("button", { name: "Dispute transaction" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Dispute transaction" }));
    await user.selectOptions(screen.getByLabelText("Dispute reason"), "UNAUTHORIZED");
    await user.type(screen.getByLabelText("Explanation"), "I did not authorize this transaction.");
    await user.click(screen.getByRole("button", { name: "Submit dispute" }));

    await waitFor(() => {
      expect(calls.some(({ url, init }) =>
        url.includes("/transaction-api/api/disputes")
        && init?.method === "POST"
        && String(init.body).includes("\"transactionId\":\"txn-1\"")
        && String(init.body).includes("\"reasonCode\":\"UNAUTHORIZED\"")
      )).toBe(true);
    });
    expect(await screen.findByText("DP-20260530-0001")).toBeInTheDocument();
  });

  it("shows customer-safe journal postings for a selected transaction with a journal link", async () => {
    const user = userEvent.setup();
    const transaction = sampleTransaction({ journalId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa" });
    const { calls } = mockFetch((url) => {
      if (url.includes("/api/ledger/journals/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")) {
        return jsonResponse({
          journalId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
          journalReference: "txn-1",
          journalType: "TRANSFER",
          state: "POSTED",
          currency: "USD",
          customerAmount: 42.5,
          description: "Card purchase",
          postedAt: "2026-05-20T10:00:05",
          postings: [
            {
              externalAccountId: "101",
              direction: "DEBIT",
              amount: 42.5,
              currency: "USD",
              memo: "Card purchase"
            }
          ]
        });
      }
      if (url.includes("/api/transactions/txn-1")) {
        return jsonResponse(transaction);
      }
      if (url.includes("/api/transactions")) {
        return jsonResponse({ ...emptyPage, content: [transaction], totalElements: 1, totalPages: 1 });
      }
      return undefined;
    });

    renderApp("/transactions", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    await user.click(await screen.findByText("txn-1"));

    expect(await screen.findByText("Ledger journal")).toBeInTheDocument();
    expect(screen.getByText("POSTED")).toBeInTheDocument();
    expect(screen.getByText("Account 101")).toBeInTheDocument();
    expect(screen.getByText("DEBIT $42.50")).toBeInTheDocument();
    expect(calls.some(({ url }) => url.includes("/transaction-api/api/ledger/journals/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))).toBe(true);
    expect(screen.queryByText("ledgerAccountId")).not.toBeInTheDocument();
  });
});

describe("customer disputes", () => {
  it("lists submitted disputes and resolution notes", async () => {
    mockFetch((url) => {
      if (url.includes("/api/disputes")) {
        return jsonResponse({ ...emptyPage, content: [sampleDispute({ status: "APPROVED", resolutionNote: "Customer claim accepted." })], totalElements: 1, totalPages: 1 });
      }
      return undefined;
    });

    renderApp("/disputes", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByRole("heading", { name: "Disputes" })).toBeInTheDocument();
    expect(await screen.findByText("DP-20260530-0001")).toBeInTheDocument();
    expect(screen.getByText("APPROVED")).toBeInTheDocument();
    expect(screen.getByText("Customer claim accepted.")).toBeInTheDocument();
  });
});

describe("customer shell navigation", () => {
  it("shows the customer shell for authenticated users", async () => {
    mockFetch();
    const { unmount } = renderApp("/", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));
    expect(await screen.findByRole("link", { name: "Financial Console" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Accounts" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Move Money" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Transactions" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Disputes" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Notifications" })).toBeInTheDocument();
    expect(screen.queryByText("Operations")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Admin Accounts" })).not.toBeInTheDocument();
    unmount();

    renderApp("/", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));
    expect(await screen.findByRole("link", { name: "Financial Console" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Accounts" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Move Money" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Transactions" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Disputes" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Notifications" })).toBeInTheDocument();
    expect(screen.queryByText("Operations")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Admin Accounts" })).not.toBeInTheDocument();
  });

  it("shows the admin operations shell for admin users at the admin overview", async () => {
    mockFetch();
    renderApp("/admin", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("link", { name: "Operations Console" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Operations overview" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Overview" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Admin Accounts" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Monitoring" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ops Transactions" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Audit Log" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Risk Alerts" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Risk Cases" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Disputes" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Investigations" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Move Money" })).not.toBeInTheDocument();
  });

  it("redirects non-admin users from the admin overview to the customer dashboard", async () => {
    mockFetch();
    renderApp("/admin", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Operations overview" })).not.toBeInTheDocument();
  });

  it("redirects non-admin users away from admin routes", async () => {
    mockFetch();
    renderApp("/admin/monitoring", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Monitoring" })).not.toBeInTheDocument();
  });
});

describe("customer notifications", () => {
  it("shows unread badge and marks notifications read", async () => {
    const user = userEvent.setup();
    const { calls } = mockFetch((url, init) => {
      if (url.includes("/api/notifications/summary")) {
        return jsonResponse({ total: 2, unread: 1, bySeverity: { SUCCESS: 1 }, byType: { TRANSACTION_COMPLETED: 1 }, bySourceType: { TRANSACTION: 1 } });
      }
      if (url.includes("/api/notifications/1/read") && init?.method === "PATCH") {
        return jsonResponse({ notificationId: 1, status: "READ" });
      }
      if (url.includes("/api/notifications/read-all") && init?.method === "PATCH") {
        return jsonResponse({ updated: 1 });
      }
      if (url.includes("/api/notifications")) {
        return jsonResponse({
          ...emptyPage,
          content: [
            {
              notificationId: 1,
              userId: "customer",
              type: "TRANSACTION_COMPLETED",
              severity: "SUCCESS",
              status: "UNREAD",
              title: "Transaction completed",
              message: "TRANSFER completed for USD 100.00.",
              sourceType: "TRANSACTION",
              sourceId: "txn-1",
              dedupeKey: "transaction:txn-1:COMPLETED",
              createdAt: "2026-05-30T10:00:00Z"
            }
          ],
          totalElements: 1,
          totalPages: 1
        });
      }
      return undefined;
    });

    renderApp("/notifications", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByRole("heading", { name: "Notifications" })).toBeInTheDocument();
    expect(await screen.findByText("Transaction completed")).toBeInTheDocument();
    expect(screen.getByText("1 unread")).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Status"), "UNREAD");
    await user.selectOptions(screen.getByLabelText("Type"), "TRANSACTION_COMPLETED");
    expect(calls.some(({ url }) => url.includes("/account-api/api/notifications") && url.includes("status=UNREAD") && url.includes("type=TRANSACTION_COMPLETED"))).toBe(true);

    await user.click(screen.getByRole("button", { name: "Mark read" }));
    await waitFor(() => expect(calls.some(({ url, init }) => url.includes("/account-api/api/notifications/1/read") && init?.method === "PATCH")).toBe(true));

    await user.click(screen.getByRole("button", { name: "Mark all read" }));
    await waitFor(() => expect(calls.some(({ url, init }) => url.includes("/account-api/api/notifications/read-all") && init?.method === "PATCH")).toBe(true));
  });
});

describe("admin audit log", () => {
  it("renders summary, filters, table rows, and selected event details", async () => {
    const user = userEvent.setup();
    const { calls } = mockFetch((url) => {
      if (url.includes("/api/audit/summary")) {
        return jsonResponse({ totalEvents: 4, failureEvents: 1, reversalEvents: 1, securityEvents: 1 });
      }
      if (url.includes("/api/audit/events")) {
        return jsonResponse({
          ...emptyPage,
          content: [
            {
              eventId: "event-1",
              eventType: "TRANSACTION",
              action: "TRANSACTION_FAILED",
              outcome: "FAILURE",
              userId: "customer",
              transactionId: "txn-1",
              amount: 42.5,
              currency: "USD",
              details: "Insufficient funds",
              createdAt: "2026-05-08T10:15:30"
            }
          ],
          totalElements: 1,
          totalPages: 1
        });
      }
      return undefined;
    });

    renderApp("/admin/audit-log", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("heading", { name: "Audit Log" })).toBeInTheDocument();
    expect(await screen.findByText("4")).toBeInTheDocument();
    expect(screen.getByText("TRANSACTION_FAILED")).toBeInTheDocument();
    expect(screen.getByText("txn-1")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("User ID"), "customer");
    await user.type(screen.getByPlaceholderText("Transaction ID"), "txn-1");
    await user.selectOptions(screen.getByDisplayValue("All outcomes"), "FAILURE");

    await waitFor(() => {
      expect(calls.some(({ url }) =>
        url.includes("/transaction-api/api/audit/events")
        && url.includes("userId=customer")
        && url.includes("transactionId=txn-1")
        && url.includes("outcome=FAILURE")
      )).toBe(true);
    });

    await user.click(screen.getByRole("button", { name: "View event-1" }));
    expect(screen.getByText("Insufficient funds")).toBeInTheDocument();
  });

});

describe("admin account status controls", () => {
  it("shows available and ledger balance columns", async () => {
    mockFetch();

    renderApp("/admin/accounts", tokenFor({ sub: "admin", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("columnheader", { name: "Available" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Ledger" })).toBeInTheDocument();
    expect(await screen.findByText("$175.00")).toBeInTheDocument();
    expect(await screen.findByText("$250.00")).toBeInTheDocument();
  });

  it("freezes account with a required reason", async () => {
    const user = userEvent.setup();
    const { calls } = mockFetch((url, init) => {
      if (url.includes("/api/accounts/101/status") && init?.method === "PATCH") {
        return jsonResponse({ ...sampleAccount, status: "FROZEN", statusReason: "Fraud review" });
      }
      return undefined;
    });

    renderApp("/admin/accounts", tokenFor({ sub: "admin", roles: ["ROLE_ADMIN"] }));

    await screen.findByText("#101");
    await user.click(screen.getByRole("button", { name: "Freeze account 101" }));
    await user.click(screen.getByRole("button", { name: "Confirm status update" }));
    expect(await screen.findByText("Status reason is required")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Status reason"), "Fraud review");
    await user.click(screen.getByRole("button", { name: "Confirm status update" }));

    await waitFor(() => {
      expect(calls.some(({ url, init }) => url.includes("/account-api/api/accounts/101/status")
        && init?.method === "PATCH"
        && String(init.body).includes("\"status\":\"FROZEN\"")
        && String(init.body).includes("Fraud review"))).toBe(true);
    });
  });
});

describe("money movement account holds", () => {
  it("keeps frozen accounts selectable for deposit but disabled for debits", async () => {
    mockFetch((url) => {
      if (url.includes("/api/accounts")) {
        return jsonResponse({ ...emptyPage, content: [sampleAccount, frozenAccount], totalElements: 2, totalPages: 1 });
      }
      return undefined;
    });

    renderApp("/move-money", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    await screen.findAllByText(/#202 - CHECKING - Available 250.00 - FROZEN/);
    const depositSelect = screen.getAllByLabelText("Account")[0];
    const withdrawSelect = screen.getByLabelText("Withdraw account");
    const frozenDebitOption = Array.from(withdrawSelect.querySelectorAll("option")).find((option) => option.value === "202");

    expect(depositSelect).toHaveTextContent("#202 - CHECKING - Available 250.00 - FROZEN");
    expect(withdrawSelect).toHaveTextContent("#202 - CHECKING - Available 250.00 - FROZEN");
    expect(frozenDebitOption).toBeDisabled();
  });

  it("disables debit source accounts when requested amount exceeds available balance", async () => {
    const user = userEvent.setup();
    mockFetch((url) => {
      if (url.includes("/api/accounts")) {
        return jsonResponse({ ...emptyPage, content: [sampleAccount], totalElements: 1, totalPages: 1 });
      }
      return undefined;
    });

    renderApp("/move-money", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    await user.clear(screen.getAllByLabelText("Amount")[1]);
    await user.type(screen.getAllByLabelText("Amount")[1], "200");
    const withdrawSelect = screen.getByLabelText("Withdraw account");
    const activeDebitOption = Array.from(withdrawSelect.querySelectorAll("option")).find((option) => option.value === "101");

    expect(activeDebitOption).toBeDisabled();
    expect(withdrawSelect).toHaveTextContent("#101 - CHECKING - Available 175.00");
  });
});

describe("admin risk alerts", () => {
  it("renders summary, filters, table rows, details, status actions, and create case action", async () => {
    const user = userEvent.setup();
    const { calls } = mockFetch((url, init) => {
      if (url.includes("/api/risk/summary")) {
        return jsonResponse({ totalAlerts: 5, openAlerts: 3, highSeverityAlerts: 2, escalatedAlerts: 1 });
      }
      if (url.includes("/api/risk/cases/from-alert/alert-1") && init?.method === "POST") {
        return jsonResponse({
          caseId: "case-1",
          caseNumber: "RC-20260509-0001",
          status: "OPEN",
          priority: "HIGH",
          title: "Review high-value transfer",
          primaryAlertId: "alert-1",
          userId: "customer",
          transactionId: "txn-1",
          createdBy: "ops",
          createdAt: "2026-05-09T10:30:00",
          updatedAt: "2026-05-09T10:30:00",
          linkedAlerts: [],
          notes: []
        });
      }
      if (url.includes("/api/risk/alerts/alert-1/status") && init?.method === "PATCH") {
        return jsonResponse({
          alertId: "alert-1",
          alertType: "HIGH_VALUE_TRANSFER",
          severity: "HIGH",
          status: "ESCALATED",
          userId: "customer",
          transactionId: "txn-1",
          amount: 6000,
          currency: "USD",
          reason: "Transfer amount exceeded high-value threshold",
          recommendation: "Review sender and recipient",
          reviewedBy: "ops",
          resolutionNote: "Escalated to fraud operations",
          createdAt: "2026-05-09T10:15:30",
          updatedAt: "2026-05-09T10:20:30"
        });
      }
      if (url.includes("/api/risk/alerts")) {
        return jsonResponse({
          ...emptyPage,
          content: [
            {
              alertId: "alert-1",
              alertType: "HIGH_VALUE_TRANSFER",
              severity: "HIGH",
              status: "OPEN",
              userId: "customer",
              transactionId: "txn-1",
              amount: 6000,
              currency: "USD",
              reason: "Transfer amount exceeded high-value threshold",
              recommendation: "Review sender and recipient",
              metadata: "{\"threshold\":\"5000.00\"}",
              createdAt: "2026-05-09T10:15:30",
              updatedAt: "2026-05-09T10:15:30"
            }
          ],
          totalElements: 1,
          totalPages: 1
        });
      }
      return undefined;
    });

    renderApp("/admin/risk-alerts", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("heading", { name: "Risk Alerts" })).toBeInTheDocument();
    expect(await screen.findByText("5")).toBeInTheDocument();
    expect(screen.getByText("HIGH_VALUE_TRANSFER")).toBeInTheDocument();
    expect(screen.getByText("txn-1")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("User ID"), "customer");
    await user.type(screen.getByPlaceholderText("Transaction ID"), "txn-1");
    await user.selectOptions(screen.getByDisplayValue("All status"), "OPEN");
    await user.selectOptions(screen.getByDisplayValue("All severity"), "HIGH");

    await waitFor(() => {
      expect(calls.some(({ url }) =>
        url.includes("/transaction-api/api/risk/alerts")
        && url.includes("userId=customer")
        && url.includes("transactionId=txn-1")
        && url.includes("status=OPEN")
        && url.includes("severity=HIGH")
      )).toBe(true);
    });

    await user.click(screen.getByRole("button", { name: "View alert-1" }));
    expect(screen.getByText("Transfer amount exceeded high-value threshold")).toBeInTheDocument();
    expect(screen.getByText("Review sender and recipient")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create case" }));
    expect(await screen.findByText("Case RC-20260509-0001 created")).toBeInTheDocument();
    expect(calls.some(({ url, init }) =>
      url.includes("/transaction-api/api/risk/cases/from-alert/alert-1")
      && init?.method === "POST"
      && String(init.body).includes("Review HIGH_VALUE_TRANSFER")
    )).toBe(true);

    await user.type(screen.getByPlaceholderText("Resolution note"), "Escalated to fraud operations");
    await user.click(screen.getByRole("button", { name: "Escalate alert" }));

    await waitFor(() => {
      expect(calls.some(({ url, init }) =>
        url.includes("/transaction-api/api/risk/alerts/alert-1/status")
        && init?.method === "PATCH"
        && String(init.body).includes("\"status\":\"ESCALATED\"")
        && String(init.body).includes("Escalated to fraud operations")
      )).toBe(true);
    });
  });
});

describe("admin risk cases", () => {
  it("renders summary, filters, table rows, detail panel, claim, status, and notes actions", async () => {
    const user = userEvent.setup();
    const { calls } = mockFetch((url, init) => {
      if (url.includes("/api/risk/cases/summary")) {
        return jsonResponse({ totalCases: 6, openCases: 3, inReviewCases: 1, resolvedCases: 1, closedCases: 1, unassignedCases: 2 });
      }
      if (url.includes("/api/risk/cases/case-1/claim") && init?.method === "PATCH") {
        return jsonResponse(sampleRiskCase({ assignedTo: "ops", status: "IN_REVIEW", claimedAt: "2026-05-09T10:45:00" }));
      }
      if (url.includes("/api/risk/cases/case-1/status") && init?.method === "PATCH") {
        return jsonResponse(sampleRiskCase({ status: "RESOLVED", resolutionNote: "Reviewed and resolved", closedAt: "2026-05-09T11:00:00" }));
      }
      if (url.includes("/api/risk/cases/case-1/notes") && init?.method === "POST") {
        return jsonResponse(sampleRiskCase({
          notes: [
            { noteId: "note-1", author: "ops", note: "Customer pattern needs follow-up", createdAt: "2026-05-09T11:15:00" }
          ]
        }));
      }
      if (url.includes("/api/risk/cases")) {
        return jsonResponse({ ...emptyPage, content: [sampleRiskCase()], totalElements: 1, totalPages: 1 });
      }
      return undefined;
    });

    renderApp("/admin/risk-cases", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("heading", { name: "Risk Cases" })).toBeInTheDocument();
    expect(await screen.findByText("6")).toBeInTheDocument();
    expect(screen.getByText("RC-20260509-0001")).toBeInTheDocument();
    expect(screen.getByText("txn-1")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("User ID"), "customer");
    await user.type(screen.getByPlaceholderText("Transaction ID"), "txn-1");
    await user.type(screen.getByPlaceholderText("Alert ID"), "alert-1");
    await user.selectOptions(screen.getByDisplayValue("All status"), "OPEN");
    await user.selectOptions(screen.getByDisplayValue("All priority"), "HIGH");
    await user.selectOptions(screen.getByDisplayValue("All assignment"), "UNASSIGNED");

    await waitFor(() => {
      expect(calls.some(({ url }) =>
        url.includes("/transaction-api/api/risk/cases")
        && url.includes("userId=customer")
        && url.includes("transactionId=txn-1")
        && url.includes("alertId=alert-1")
        && url.includes("status=OPEN")
        && url.includes("priority=HIGH")
        && url.includes("assignedTo=UNASSIGNED")
      )).toBe(true);
    });

    await user.click(screen.getByRole("button", { name: "View RC-20260509-0001" }));
    expect(screen.getAllByText("Review high-value transfer").length).toBeGreaterThan(0);
    expect(screen.getByText("HIGH_VALUE_TRANSFER")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Claim case" }));
    await waitFor(() => {
      expect(calls.some(({ url, init }) => url.includes("/transaction-api/api/risk/cases/case-1/claim") && init?.method === "PATCH")).toBe(true);
    });

    await user.type(screen.getByPlaceholderText("Internal note"), "Customer pattern needs follow-up");
    await user.click(screen.getByRole("button", { name: "Add note" }));
    expect(await screen.findByText("Customer pattern needs follow-up")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("Resolution note"), "Reviewed and resolved");
    await user.click(screen.getByRole("button", { name: "Resolve case" }));

    await waitFor(() => {
      expect(calls.some(({ url, init }) =>
        url.includes("/transaction-api/api/risk/cases/case-1/status")
        && init?.method === "PATCH"
        && String(init.body).includes("\"status\":\"RESOLVED\"")
        && String(init.body).includes("Reviewed and resolved")
      )).toBe(true);
    });
  });
});

describe("admin disputes", () => {
  it("renders queue filters, claim, status update, and notes", async () => {
    const user = userEvent.setup();
    const { calls } = mockFetch((url, init) => {
      if (url.includes("/api/disputes/admin/summary")) {
        return jsonResponse({ totalDisputes: 5, openDisputes: 2, inReviewDisputes: 1, approvedDisputes: 1, deniedDisputes: 1, closedDisputes: 0, unassignedDisputes: 2 });
      }
      if (url.includes("/api/disputes/admin/dispute-1/claim") && init?.method === "PATCH") {
        return jsonResponse(sampleDispute({ status: "IN_REVIEW", assignedTo: "ops", claimedAt: "2026-05-30T11:00:00" }));
      }
      if (url.includes("/api/disputes/admin/dispute-1/status") && init?.method === "PATCH") {
        return jsonResponse(sampleDispute({ status: "APPROVED", resolutionNote: "Customer claim accepted.", closedAt: "2026-05-30T12:00:00" }));
      }
      if (url.includes("/api/disputes/admin/dispute-1/notes") && init?.method === "POST") {
        return jsonResponse(sampleDispute({ notes: [{ noteId: "note-1", author: "ops", note: "Reviewed transaction logs.", createdAt: "2026-05-30T11:30:00" }] }));
      }
      if (url.includes("/api/disputes/admin")) {
        return jsonResponse({ ...emptyPage, content: [sampleDispute()], totalElements: 1, totalPages: 1 });
      }
      return undefined;
    });

    renderApp("/admin/disputes", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("heading", { name: "Disputes" })).toBeInTheDocument();
    expect(await screen.findByText("5")).toBeInTheDocument();
    expect(screen.getByText("DP-20260530-0001")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("User ID"), "customer");
    await user.type(screen.getByPlaceholderText("Transaction ID"), "txn-1");
    await user.selectOptions(screen.getByDisplayValue("All status"), "OPEN");
    await user.selectOptions(screen.getByDisplayValue("All reasons"), "UNAUTHORIZED");

    await waitFor(() => {
      expect(calls.some(({ url }) =>
        url.includes("/transaction-api/api/disputes/admin")
        && url.includes("userId=customer")
        && url.includes("transactionId=txn-1")
        && url.includes("status=OPEN")
        && url.includes("reasonCode=UNAUTHORIZED")
      )).toBe(true);
    });

    await user.click(screen.getByRole("button", { name: "View DP-20260530-0001" }));
    await user.click(screen.getByRole("button", { name: "Claim dispute" }));
    await user.type(screen.getByPlaceholderText("Internal note"), "Reviewed transaction logs.");
    await user.click(screen.getByRole("button", { name: "Add note" }));
    expect(await screen.findByText("Reviewed transaction logs.")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("Resolution note"), "Customer claim accepted.");
    await user.click(screen.getByRole("button", { name: "Approve dispute" }));

    await waitFor(() => {
      expect(calls.some(({ url, init }) =>
        url.includes("/transaction-api/api/disputes/admin/dispute-1/status")
        && init?.method === "PATCH"
        && String(init.body).includes("\"status\":\"APPROVED\"")
        && String(init.body).includes("Customer claim accepted.")
      )).toBe(true);
    });
  });
});

describe("admin investigations", () => {
  it("renders summary, search controls, timeline items, and selected item details", async () => {
    const user = userEvent.setup();
    const { calls } = mockFetch((url) => {
      if (url.includes("/api/investigations/summary")) {
        return jsonResponse({ transactions: 2, auditEvents: 2, riskAlerts: 1, riskCases: 1, failures: 1, reversals: 1, highSeverityItems: 2 });
      }
      if (url.includes("/api/investigations/timeline")) {
        return jsonResponse({
          ...emptyPage,
          size: 50,
          content: [
            {
              itemId: "alert-1",
              itemType: "RISK_ALERT",
              title: "HIGH_VALUE_TRANSFER",
              description: "Transfer amount exceeded threshold",
              severity: "HIGH",
              status: "OPEN",
              userId: "customer",
              transactionId: "txn-1",
              accountId: "101",
              alertId: "alert-1",
              amount: "6000.00",
              currency: "USD",
              createdAt: "2026-05-10T10:02:00",
              metadata: { recommendation: "Review sender and recipient" }
            },
            {
              itemId: "case-1",
              itemType: "RISK_CASE",
              title: "RC-20260510-0001",
              description: "Review high value transfer",
              severity: "HIGH",
              status: "OPEN",
              userId: "customer",
              transactionId: "txn-1",
              alertId: "alert-1",
              caseId: "case-1",
              createdAt: "2026-05-10T10:03:00",
              metadata: { assignedTo: "ops" }
            }
          ],
          totalElements: 2,
          totalPages: 1
        });
      }
      return undefined;
    });

    renderApp("/admin/investigations", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("heading", { name: "Investigations" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText("2").length).toBeGreaterThan(0));
    expect(screen.getByText("HIGH_VALUE_TRANSFER")).toBeInTheDocument();
    expect(screen.getByText("RC-20260510-0001")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("User ID"), "customer");
    await user.type(screen.getByPlaceholderText("Transaction ID"), "txn-1");
    await user.type(screen.getByPlaceholderText("Account ID"), "101");
    await user.type(screen.getByPlaceholderText("Alert ID"), "alert-1");
    await user.type(screen.getByPlaceholderText("Case ID"), "case-1");

    await waitFor(() => {
      expect(calls.some(({ url }) =>
        url.includes("/transaction-api/api/investigations/timeline")
        && url.includes("userId=customer")
        && url.includes("transactionId=txn-1")
        && url.includes("accountId=101")
        && url.includes("alertId=alert-1")
        && url.includes("caseId=case-1")
      )).toBe(true);
    });

    await user.click(screen.getByRole("button", { name: "View alert-1" }));
    expect(screen.getAllByText("Transfer amount exceeded threshold").length).toBeGreaterThan(0);
    expect(screen.getByText("Review sender and recipient")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open alert" })).toHaveAttribute("href", "/admin/risk-alerts");
  });

  it("exports the current investigation filters as a CSV download", async () => {
    const user = userEvent.setup();
    const createObjectUrl = vi.fn(() => "blob:investigation-export");
    const revokeObjectUrl = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    vi.stubGlobal("URL", { createObjectURL: createObjectUrl, revokeObjectURL: revokeObjectUrl });

    const { calls } = mockFetch((url) => {
      if (url.includes("/api/investigations/export")) {
        return new Response("itemId,itemType\nalert-1,RISK_ALERT\n", {
          status: 200,
          headers: { "Content-Type": "text/csv" }
        });
      }
      if (url.includes("/api/investigations/summary")) {
        return jsonResponse({ transactions: 0, auditEvents: 0, riskAlerts: 0, riskCases: 0, failures: 0, reversals: 0, highSeverityItems: 0 });
      }
      if (url.includes("/api/investigations/timeline")) {
        return jsonResponse(emptyPage);
      }
      return undefined;
    });

    renderApp("/admin/investigations", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("heading", { name: "Investigations" })).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText("User ID"), "customer");
    await user.type(screen.getByPlaceholderText("Alert ID"), "alert-1");
    await user.click(screen.getByRole("button", { name: "Export CSV" }));

    await waitFor(() => {
      expect(calls.some(({ url }) =>
        url.includes("/transaction-api/api/investigations/export")
        && url.includes("userId=customer")
        && url.includes("alertId=alert-1")
      )).toBe(true);
    });
    expect(createObjectUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(revokeObjectUrl).toHaveBeenCalledWith("blob:investigation-export");
  });

  it("shows a report preview and prints the investigation report", async () => {
    const user = userEvent.setup();
    const printSpy = vi.fn();
    vi.stubGlobal("print", printSpy);

    mockFetch((url) => {
      if (url.includes("/api/investigations/summary")) {
        return jsonResponse({ transactions: 1, auditEvents: 1, riskAlerts: 1, riskCases: 1, failures: 1, reversals: 0, highSeverityItems: 2 });
      }
      if (url.includes("/api/investigations/timeline")) {
        return jsonResponse({
          ...emptyPage,
          size: 50,
          content: [
            {
              itemId: "alert-1",
              itemType: "RISK_ALERT",
              title: "HIGH_VALUE_TRANSFER",
              description: "Transfer amount exceeded threshold",
              severity: "HIGH",
              status: "OPEN",
              userId: "customer",
              transactionId: "txn-1",
              accountId: "101",
              alertId: "alert-1",
              amount: "6000.00",
              currency: "USD",
              createdAt: "2026-05-10T10:02:00",
              metadata: { recommendation: "Review sender and recipient" }
            }
          ],
          totalElements: 1,
          totalPages: 1
        });
      }
      return undefined;
    });

    renderApp("/admin/investigations", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("heading", { name: "Investigation report" })).toBeInTheDocument();
    expect(screen.getByText("Report scope")).toBeInTheDocument();
    expect(await screen.findByText("High-risk items require review")).toBeInTheDocument();
    expect(screen.getByText("alert-1")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Print report" }));

    expect(printSpy).toHaveBeenCalledTimes(1);
  });
});

function sampleRiskCase(overrides: Record<string, unknown> = {}) {
  return {
    caseId: "case-1",
    caseNumber: "RC-20260509-0001",
    status: "OPEN",
    priority: "HIGH",
    title: "Review high-value transfer",
    userId: "customer",
    transactionId: "txn-1",
    primaryAlertId: "alert-1",
    assignedTo: undefined,
    createdBy: "ops",
    createdAt: "2026-05-09T10:30:00",
    updatedAt: "2026-05-09T10:30:00",
    linkedAlerts: [
      {
        alertId: "alert-1",
        alertType: "HIGH_VALUE_TRANSFER",
        severity: "HIGH",
        status: "OPEN",
        userId: "customer",
        transactionId: "txn-1",
        amount: 6000,
        currency: "USD",
        reason: "Transfer amount exceeded high-value threshold",
        createdAt: "2026-05-09T10:15:30"
      }
    ],
    notes: [],
    ...overrides
  };
}

function sampleTransaction(overrides: Record<string, unknown> = {}) {
  return {
    transactionId: "txn-1",
    fromAccountId: "101",
    toAccountId: "202",
    amount: 42.5,
    currency: "USD",
    type: "TRANSFER",
    status: "COMPLETED",
    description: "Card purchase",
    createdBy: "customer",
    createdAt: "2026-05-20T10:00:00",
    processedAt: "2026-05-20T10:00:05",
    ...overrides
  };
}

function sampleDispute(overrides: Record<string, unknown> = {}) {
  return {
    disputeId: "dispute-1",
    disputeNumber: "DP-20260530-0001",
    transactionId: "txn-1",
    userId: "customer",
    status: "OPEN",
    reasonCode: "UNAUTHORIZED",
    description: "I did not authorize this transaction.",
    assignedTo: undefined,
    createdBy: "customer",
    createdAt: "2026-05-30T10:00:00",
    updatedAt: "2026-05-30T10:00:00",
    notes: [],
    ...overrides
  };
}

describe("admin monitoring", () => {
  it("renders readable metric summaries and derives alert status", async () => {
    mockFetch((url) => {
      if (url.includes("/api/health/status")) {
        return jsonResponse({ status: "UP" });
      }
      if (url.includes("/api/health/metrics")) {
        return jsonResponse({ health_check_total: 3, health_check_failure_total: 0, deployment_success_total: 1, application_health_score: 100 });
      }
      if (url.includes("/api/health/deployment")) {
        return jsonResponse({ version: "1.0.0", environment: "dev", healthScore: 100, uptimeSeconds: 120 });
      }
      if (url.includes("/api/monitoring/health/detailed")) {
        return jsonResponse({ service: "transaction-service" });
      }
      if (url.includes("/api/monitoring/stats/transactions")) {
        return jsonResponse({ dailyVolume: 2, dailyAmount: 25, successRate: 1, activeTransactions: 0 });
      }
      if (url.includes("/api/monitoring/stats/system")) {
        return jsonResponse({ system: { cpuUsage: 4.2 }, jvm: { memoryUsed: 1000 }, database: { connectionPoolActive: 0 }, http: { avgResponseTime: 18 } });
      }
      if (url.includes("/api/monitoring/alerts/status")) {
        return jsonResponse({ alertingEnabled: true, criticalAlerts: 0, warningAlerts: 0, infoAlerts: 0, activeAlertSuppressions: 0 });
      }
      if (url.includes("/api/monitoring/metrics/available")) {
        return jsonResponse({ totalMeters: 217, categories: { alerts: ["a"], jvm: ["j"], system: ["s"] } });
      }
      return undefined;
    });

    renderApp("/admin/monitoring", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(await screen.findByRole("heading", { name: "Monitoring" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText("0 active").length).toBeGreaterThan(0));
    expect(screen.getByText("Health checks")).toBeInTheDocument();
    expect(screen.getByText("Total meters")).toBeInTheDocument();
    expect(screen.queryByText("{}")).not.toBeInTheDocument();
  });
});
