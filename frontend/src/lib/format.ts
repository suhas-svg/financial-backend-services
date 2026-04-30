export function money(value: number | string | undefined | null, currency = "USD") {
  const numeric = Number(value ?? 0);
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(Number.isFinite(numeric) ? numeric : 0);
}

export function compactDate(value?: string) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeStyle: value.includes("T") ? "short" : undefined }).format(
    new Date(value)
  );
}

export function percent(value?: number) {
  return `${Number(value ?? 0).toFixed(1)}%`;
}
