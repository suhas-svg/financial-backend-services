import { describe, expect, it } from "vitest";
import { monitoringNotice } from "./outcomeMonitoring";

describe("monitoringNotice", () => {
  it("says nothing when the API did not report monitoring health", () => {
    expect(monitoringNotice(undefined)).toBeNull();
  });

  it("shows when a healthy outcome was last checked, in the viewer's zone", () => {
    expect(monitoringNotice({ consecutiveFailures: 0, lastCheckedAt: "2026-10-01T09:00:00Z" }, "Asia/Kolkata"))
      .toEqual({ tone: "info", text: "Last checked Oct 1, 2026, 2:30:00 PM GMT+5:30." });
  });

  it("warns after a failed check and says when it will retry", () => {
    const notice = monitoringNotice({
      consecutiveFailures: 2,
      lastCheckedAt: "2026-10-01T08:00:00Z",
      nextAttemptAt: "2026-10-01T09:10:00Z",
      lastError: "Account service unavailable"
    }, "UTC");

    expect(notice?.tone).toBe("warn");
    expect(notice?.text).toBe("The last automatic check failed. Last error: Account service unavailable. Next automatic attempt Oct 1, 2026, 9:10:00 AM UTC.");
  });

  it("flags a degraded outcome as possibly out of date and offers the manual retry", () => {
    const notice = monitoringNotice({
      consecutiveFailures: 5,
      degradedAt: "2026-10-01T09:00:00Z",
      nextAttemptAt: "2026-10-01T10:20:00Z",
      lastError: "Account not found"
    }, "UTC");

    expect(notice?.tone).toBe("bad");
    expect(notice?.text).toContain("5 attempts in a row");
    expect(notice?.text).toContain("may be out of date");
    expect(notice?.text).toContain("Last error: Account not found.");
    expect(notice?.text).toContain("Use Check current state to retry now.");
  });
});
