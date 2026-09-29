import { describe, expect, it } from "vitest";
import { calendarDay, compactDate, dateTime, money, utcDate } from "./format";

describe("money", () => {
  it("formats INR with the rupee symbol and Indian digit grouping", () => {
    const formatted = money("123456.78", "INR");
    expect(formatted).toContain("₹");
    expect(formatted).toContain("1,23,456.78");
  });
});

describe("API timestamps", () => {
  // Backend LocalDateTime values are UTC without an offset.
  const posted = "2026-09-29T12:19:45.891812";

  it("reads offset-less backend timestamps as UTC", () => {
    expect(utcDate(posted).toISOString()).toBe("2026-09-29T12:19:45.891Z");
    expect(utcDate("2026-10-02T03:30:00Z").toISOString()).toBe("2026-10-02T03:30:00.000Z");
  });

  it("shows timestamps in the viewer's zone and names that zone", () => {
    expect(compactDate(posted, "Asia/Kolkata")).toBe("Sep 29, 2026, 5:49 PM GMT+5:30");
    expect(compactDate(posted, "UTC")).toBe("Sep 29, 2026, 12:19 PM UTC");
    expect(dateTime(posted, "Asia/Kolkata")).toBe("Sep 29, 2026, 5:49:45 PM GMT+5:30");
  });

  it("keeps date-only values on their calendar day in every zone", () => {
    expect(compactDate("2026-10-06", "Asia/Kolkata")).toBe("Oct 6, 2026");
    expect(compactDate("2026-10-06", "America/Los_Angeles")).toBe("Oct 6, 2026");
  });

  it("groups by the viewer's local day, which can differ from the UTC day", () => {
    expect(calendarDay("2026-09-29T20:00:00", "Asia/Kolkata")).toBe("Sep 30, 2026");
    expect(calendarDay("2026-09-29T20:00:00", "UTC")).toBe("Sep 29, 2026");
  });

  it("uses placeholders for missing values", () => {
    expect(compactDate(undefined)).toBe("-");
    expect(dateTime(null)).toBe("n/a");
  });
});
