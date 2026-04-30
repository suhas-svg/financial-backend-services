import type { Role } from "../types";

const SESSION_KEY = "financial-console-token";

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
  sessionStorage.setItem(SESSION_KEY, token);
}

export function clearSession() {
  sessionStorage.removeItem(SESSION_KEY);
}

export function getRawToken() {
  return sessionStorage.getItem(SESSION_KEY);
}

export function getSession(): Session | null {
  const token = getRawToken();
  if (!token) {
    return null;
  }
  const payload = decodeJwtPayload(token);
  return {
    token,
    username: payload.sub ?? "unknown",
    roles: Array.isArray(payload.roles) ? payload.roles : []
  };
}

export function isAdmin() {
  return getSession()?.roles.includes("ROLE_ADMIN") ?? false;
}
