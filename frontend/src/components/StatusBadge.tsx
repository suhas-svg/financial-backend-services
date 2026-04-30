import { Badge } from "./ui";

export function StatusBadge({ value }: { value?: string }) {
  const normalized = value ?? "UNKNOWN";
  const tone = normalized === "COMPLETED" || normalized === "UP" ? "good" : normalized === "FAILED" || normalized === "DOWN" ? "bad" : normalized === "PENDING" ? "warn" : "neutral";
  return <Badge tone={tone}>{normalized}</Badge>;
}
