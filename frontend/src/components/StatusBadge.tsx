import { Badge } from "./ui";

export function StatusBadge({ value }: { value?: string }) {
  const normalized = value ?? "UNKNOWN";
  const tone = normalized === "COMPLETED" || normalized === "UP" || normalized === "ACTIVE" ? "good" : normalized === "FAILED" || normalized === "DOWN" || normalized === "FROZEN" ? "bad" : normalized === "PENDING" ? "warn" : "neutral";
  return <Badge tone={tone}>{normalized}</Badge>;
}
