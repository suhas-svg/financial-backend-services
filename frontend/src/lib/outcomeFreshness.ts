import { ApiError } from "./api";

export const isScenarioDivergedError = (error: unknown) => error instanceof ApiError
  && typeof error.payload === "object" && error.payload !== null
  && "error" in error.payload
  && (error.payload as { error: unknown }).error === "SCENARIO_DIVERGED";
