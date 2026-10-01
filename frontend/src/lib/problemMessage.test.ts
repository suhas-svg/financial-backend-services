import { describe, expect, it } from "vitest";
import { problemMessage } from "./api";

describe("problemMessage", () => {
  it("prefers the RFC 9457 detail", () => {
    expect(problemMessage({ type: "urn:financial:problem:insufficient-funds", title: "Insufficient Funds", detail: "Balance too low", message: "old" }, 400))
      .toBe("Balance too low");
  });

  it("falls back to the pre-RFC message, then the title", () => {
    expect(problemMessage({ message: "Legacy text" }, 400)).toBe("Legacy text");
    expect(problemMessage({ title: "Conflict", detail: null }, 409)).toBe("Conflict");
  });

  it("describes bodies that are not problem details", () => {
    expect(problemMessage("<html>Bad gateway</html>", 502)).toBe("Request failed with status 502");
    expect(problemMessage(null, 500)).toBe("Request failed with status 500");
  });
});
