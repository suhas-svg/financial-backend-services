import { afterEach, describe, expect, it } from "vitest";
import { clearSession, decodeJwtPayload, getSession, isAdmin, saveSession } from "./session";

function tokenFor(payload: object) {
  const encoded = btoa(JSON.stringify(payload)).replace(/=/g, "");
  return `header.${encoded}.signature`;
}

describe("session", () => {
  afterEach(() => {
    clearSession();
  });

  it("decodes username and roles from jwt claims", () => {
    const token = tokenFor({ sub: "alex", roles: ["ROLE_USER", "ROLE_ADMIN"] });

    expect(decodeJwtPayload(token)).toMatchObject({
      sub: "alex",
      roles: ["ROLE_USER", "ROLE_ADMIN"]
    });
  });

  it("persists session token and exposes admin status", () => {
    saveSession(tokenFor({ sub: "ops", roles: ["ROLE_ADMIN"] }));

    expect(getSession()?.username).toBe("ops");
    expect(isAdmin()).toBe(true);
  });
});
