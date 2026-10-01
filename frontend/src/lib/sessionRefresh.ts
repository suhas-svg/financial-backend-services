import { clearSession, decodeJwtPayload, getRawToken, saveSession } from "./session";

export const SESSION_UPDATED_EVENT = "financial-console:session-updated";

const REFRESH_URL = "/account-api/api/auth/refresh";
const LOGOUT_URL = "/account-api/api/auth/logout";
// Renew a little before expiry so requests never race the deadline.
const RENEW_BEFORE_EXPIRY_MS = 30_000;

let inflight: Promise<string | null> | null = null;

/**
 * Exchanges the httpOnly refresh cookie for a new access token. Concurrent callers
 * share one request, because each refresh rotates the cookie. Resolves to null when
 * there is no valid session.
 */
export function refreshSession(): Promise<string | null> {
  if (!inflight) {
    inflight = requestRefresh().finally(() => {
      inflight = null;
    });
  }
  return inflight;
}

async function requestRefresh(): Promise<string | null> {
  try {
    const response = await fetch(REFRESH_URL, {
      method: "POST",
      credentials: "same-origin",
      headers: { "X-Requested-With": "XMLHttpRequest" }
    });
    if (!response.ok) {
      return null;
    }
    const payload = (await response.json().catch(() => null)) as { accessToken?: string; token?: string } | null;
    const token = payload?.accessToken ?? payload?.token ?? null;
    if (!token) {
      return null;
    }
    saveSession(token);
    window.dispatchEvent(new CustomEvent(SESSION_UPDATED_EVENT));
    return token;
  } catch {
    return null;
  }
}

export function accessTokenNeedsRenewal(token: string) {
  const exp = decodeJwtPayload(token).exp;
  return typeof exp === "number" && exp * 1000 - Date.now() <= RENEW_BEFORE_EXPIRY_MS;
}

/** Returns a usable access token, renewing it first if it is about to expire. */
export async function currentAccessToken(): Promise<string | null> {
  const token = getRawToken();
  if (token && accessTokenNeedsRenewal(token)) {
    return (await refreshSession()) ?? token;
  }
  return token;
}

/** Revokes the server-side session and forgets the access token. */
export function endSession() {
  clearSession();
  void fetch(LOGOUT_URL, {
    method: "POST",
    credentials: "same-origin",
    headers: { "X-Requested-With": "XMLHttpRequest" }
  }).catch(() => undefined);
}
