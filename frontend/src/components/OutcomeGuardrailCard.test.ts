import { describe, expect, it } from "vitest";
import { ApiError } from "../lib/api";
import { isScenarioDivergedError } from "../lib/outcomeFreshness";

describe("OutcomeGuardrailCard freshness recovery", () => {
  it("recognizes the stable scenario-diverged API error", () => {
    const error = new ApiError(409, "Authoritative state changed", {
      error: "SCENARIO_DIVERGED",
      message: "Authoritative state changed. Refresh or re-run the scenario and select and consent to a fresh repair."
    });

    expect(isScenarioDivergedError(error)).toBe(true);
    expect(isScenarioDivergedError(new Error("SCENARIO_DIVERGED"))).toBe(false);
  });
});
