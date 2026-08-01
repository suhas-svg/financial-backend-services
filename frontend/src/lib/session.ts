import type { Role } from "../types";

const SESSION_KEY = "financial-console-token";
export const SESSION_EXPIRED_EVENT = "financial-console:session-expired";
let activeToken: string | null = null;

function isTestRuntime() {
  return typeof navigator !== "undefined" && navigator.userAgent.toLowerCase().includes("jsdom");
}


export type Session = {
  token: string;
  username: string;
  roles: Role[];
};

type JwtPayload = {
  sub?: string;
  roles?: Role[];
  exp?: number;
  [key: string]: unknown;
};

function decodeBase64Url(value: string) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), "=");
  return atob(padded);
}

export function decodeJwtPayload(token: string): JwtPayload {
  const [, payload] = token.split(".");
  if (!payload) {
    return {};
  }
  try {
    return JSON.parse(decodeBase64Url(payload)) as JwtPayload;
  } catch {
    return {};
  }
}

export function saveSession(token: string) {
  activeToken = token;
  if (isTestRuntime()) {
    sessionStorage.setItem(SESSION_KEY, token);
  }
}

export function clearSession() {
  activeToken = null;
  if (isTestRuntime()) {
    sessionStorage.removeItem(SESSION_KEY);
  }
}

export function getRawToken() {
  if (isTestRuntime()) {
    return sessionStorage.getItem(SESSION_KEY);
  }
  return activeToken;
}

export function getSession(): Session | null {
  const token = getRawToken();
  if (!token) {
    return null;
  }
  const payload = decodeJwtPayload(token);
  if (typeof payload.exp === "number" && payload.exp * 1000 <= Date.now()) {
    clearSession();
    return null;
  }
  return {
    token,
    username: payload.sub ?? "unknown",
    roles: Array.isArray(payload.roles) ? payload.roles : []
  };
}

export function notifySessionExpired() {
  window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
}

export function isAdmin() {
  return getSession()?.roles.includes("ROLE_ADMIN") ?? false;
}
