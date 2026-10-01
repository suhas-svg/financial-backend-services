import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import { clearSession, getRawToken, saveSession, SESSION_EXPIRED_EVENT } from "./session";
import { endSession, refreshSession } from "./sessionRefresh";

function tokenFor(payload: object) {
  const encoded = btoa(JSON.stringify(payload)).replace(/=/g, "");
  return `header.${encoded}.signature`;
}

function json(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { "Content-Type": "application/json" } });
}

const inOneHour = () => Math.floor(Date.now() / 1000) + 3600;

describe("session refresh", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    clearSession();
  });

  it("shares one refresh request between concurrent callers", async () => {
    const renewed = tokenFor({ sub: "alex", exp: inOneHour() });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(json({ accessToken: renewed }));

    const [a, b] = await Promise.all([refreshSession(), refreshSession()]);

    expect(a).toBe(renewed);
    expect(b).toBe(renewed);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/account-api/api/auth/refresh", expect.objectContaining({
      method: "POST",
      credentials: "same-origin",
      headers: { "X-Requested-With": "XMLHttpRequest" }
    }));
    expect(getRawToken()).toBe(renewed);
  });

  it("resolves null without a valid refresh cookie", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json({ message: "Session expired" }, 401));

    expect(await refreshSession()).toBeNull();
  });

  it("renews an access token that is about to expire before sending the request", async () => {
    saveSession(tokenFor({ sub: "alex", exp: Math.floor(Date.now() / 1000) + 5 }));
    const renewed = tokenFor({ sub: "alex", exp: inOneHour() });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) =>
      String(input).includes("/api/auth/refresh") ? json({ accessToken: renewed }) : json({ ok: true }));

    await apiRequest("account", "/api/accounts");

    expect(String(fetchMock.mock.calls[0][0])).toContain("/api/auth/refresh");
    expect(fetchMock.mock.calls[1][1]?.headers).toMatchObject({ Authorization: `Bearer ${renewed}` });
  });

  it("retries once with a renewed token after a 401", async () => {
    saveSession(tokenFor({ sub: "alex", exp: inOneHour() }));
    const renewed = tokenFor({ sub: "alex", exp: inOneHour() + 60 });
    let accountCalls = 0;
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      if (String(input).includes("/api/auth/refresh")) {
        return json({ accessToken: renewed });
      }
      accountCalls += 1;
      return accountCalls === 1 ? json({ message: "expired" }, 401) : json({ ok: true });
    });

    await expect(apiRequest("account", "/api/accounts")).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2][1]?.headers).toMatchObject({ Authorization: `Bearer ${renewed}` });
  });

  it("ends the session when the refresh also fails", async () => {
    saveSession(tokenFor({ sub: "alex", exp: inOneHour() }));
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json({ message: "Session expired" }, 401));
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);

    await expect(apiRequest("account", "/api/accounts")).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it("revokes the server session on logout", () => {
    saveSession(tokenFor({ sub: "alex", exp: inOneHour() }));
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    endSession();

    expect(getRawToken()).toBeNull();
    expect(fetchMock).toHaveBeenCalledWith("/account-api/api/auth/logout", expect.objectContaining({ method: "POST" }));
  });
});
