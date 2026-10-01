import type { OutcomeMonitoringHealth } from "../types";
import { dateTime } from "./format";

export type MonitoringNotice = { tone: "info" | "warn" | "bad"; text: string };

/** What to tell the customer about whether automatic checks are keeping this outcome current. */
export function monitoringNotice(monitoring: OutcomeMonitoringHealth | undefined, timeZone?: string): MonitoringNotice | null {
  if (!monitoring) return null;
  const retry = monitoring.nextAttemptAt ? ` Next automatic attempt ${dateTime(monitoring.nextAttemptAt, timeZone)}.` : "";
  const reason = monitoring.lastError ? ` Last error: ${monitoring.lastError}.` : "";
  if (monitoring.degradedAt) {
    return {
      tone: "bad",
      text: `Automatic checks keep failing for this outcome (${monitoring.consecutiveFailures} attempts in a row), so this proof may be out of date.${reason}${retry} Use Check current state to retry now.`
    };
  }
  if (monitoring.consecutiveFailures > 0) {
    return {
      tone: "warn",
      text: `The last automatic check failed.${reason}${retry}`
    };
  }
  return monitoring.lastCheckedAt
    ? { tone: "info", text: `Last checked ${dateTime(monitoring.lastCheckedAt, timeZone)}.` }
    : null;
}
