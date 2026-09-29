export function money(value: number | string | undefined | null, currency = "USD") {
  const numeric = Number(value ?? 0);
  const locale = currency === "INR" ? "en-IN" : "en-US";
  return new Intl.NumberFormat(locale, { style: "currency", currency }).format(Number.isFinite(numeric) ? numeric : 0);
}

// Backend LocalDateTime values are UTC but serialized without an offset.
export function utcDate(value: string) {
  return new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`);
}

// Timestamps render in the viewer's zone with the zone named; date-only values are
// calendar dates and render without any zone shift.
export function compactDate(value?: string | null, timeZone?: string) {
  if (!value) {
    return "-";
  }
  if (!value.includes("T")) {
    return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeZone: "UTC" }).format(utcDate(value));
  }
  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZone,
    timeZoneName: "short"
  }).format(utcDate(value));
}

// The viewer-local calendar day of a timestamp, for grouping by day.
export function calendarDay(value: string, timeZone?: string) {
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeZone }).format(utcDate(value));
}

export function dateTime(value?: string | null, timeZone?: string) {
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
    timeZone,
    timeZoneName: "short"
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
