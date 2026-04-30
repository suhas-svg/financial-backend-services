import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
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
  createdAt: "2026-04-28T10:00:00Z"
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
});

describe("admin navigation", () => {
  it("appears only for ROLE_ADMIN users", async () => {
    mockFetch();
    const { unmount } = renderApp("/", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));
    expect(screen.queryByText("Operations")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Admin Accounts" })).not.toBeInTheDocument();
    unmount();

    renderApp("/", tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));
    expect(await screen.findByText("Operations")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Admin Accounts" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Monitoring" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ops Transactions" })).toBeInTheDocument();
  });

  it("redirects non-admin users away from admin routes", async () => {
    mockFetch();
    renderApp("/admin/monitoring", tokenFor({ sub: "customer", roles: ["ROLE_USER"] }));

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Monitoring" })).not.toBeInTheDocument();
  });
});

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
