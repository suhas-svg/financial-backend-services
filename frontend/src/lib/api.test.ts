import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
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
});
