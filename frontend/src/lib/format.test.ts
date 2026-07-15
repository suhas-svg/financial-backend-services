import { describe, expect, it } from "vitest";
import { money } from "./format";

describe("money", () => {
  it("formats INR with the rupee symbol and Indian digit grouping", () => {
    const formatted = money("123456.78", "INR");
    expect(formatted).toContain("₹");
    expect(formatted).toContain("1,23,456.78");
  });
});
