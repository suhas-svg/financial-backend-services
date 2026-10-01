import { clearSession, decodeJwtPayload, getRawToken, saveSession } from "./session";

export const SESSION_UPDATED_EVENT = "financial-console:session-updated";

const REFRESH_URL = "/account-api/api/auth/refresh";
const LOGOUT_URL = "/account-api/api/auth/logout";
// Renew a little before expiry so requests never race the deadline.
const RENEW_BEFORE_EXPIRY_MS = 30_000;
// Matches the server's refresh-token idle lifetime. Background polling keeps
// making requests, so idleness is measured from what the person does.
export const IDLE_TIMEOUT_MS = 30 * 60_000;
const ACTIVITY_KEY = "financial-console-last-activity";
const ACTIVITY_WRITE_INTERVAL_MS = 15_000;

let inflight: Promise<string | null> | null = null;
let lastActivity = Date.now();

/** Records that the person interacted with the console (shared across tabs). */
export function markActivity(now = Date.now()) {
  if (now - lastActivity < ACTIVITY_WRITE_INTERVAL_MS) {
    return;
  }
  lastActivity = now;
  try {
    localStorage.setItem(ACTIVITY_KEY, String(now));
  } catch {
    // Storage can be unavailable (private mode); this tab's own activity still counts.
  }
}

function lastActivityAt() {
  let shared = 0;
  try {
    shared = Number(localStorage.getItem(ACTIVITY_KEY)) || 0;
  } catch {
    shared = 0;
  }
  return Math.max(lastActivity, shared);
}

export function isIdle(now = Date.now()) {
  return now - lastActivityAt() >= IDLE_TIMEOUT_MS;
}

/**
 * Exchanges the httpOnly refresh cookie for a new access token. Concurrent callers
 * share one request, because each refresh rotates the cookie. Resolves to null when
 * there is no valid session.
 */
export function refreshSession(): Promise<string | null> {
  if (isIdle()) {
    // Nobody has used the console for the idle window: let the session lapse
    // instead of renewing it on behalf of background polling.
    return Promise.resolve(null);
  }
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
