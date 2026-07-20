export function money(value: number | string | undefined | null, currency = "USD") {
  const numeric = Number(value ?? 0);
  const locale = currency === "INR" ? "en-IN" : "en-US";
  return new Intl.NumberFormat(locale, { style: "currency", currency }).format(Number.isFinite(numeric) ? numeric : 0);
}

function utcDate(value: string) {
  return new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`);
}

export function compactDate(value?: string) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: value.includes("T") ? "short" : undefined,
    timeZone: "UTC"
  }).format(utcDate(value));
}

export function utcDateTime(value?: string) {
  if (!value) {
    return "n/a";
  }
  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    second: "2-digit",
    timeZone: "UTC",
    timeZoneName: "short"
  }).format(utcDate(value));
}

export function percent(value?: number) {
  return `${Number(value ?? 0).toFixed(1)}%`;
}
