import clsx from "clsx";
import { forwardRef } from "react";
import type { InputHTMLAttributes, SelectHTMLAttributes } from "react";

export function Button({
  variant = "primary",
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" | "ghost" }) {
  return (
    <button
      {...props}
      className={clsx(
        "inline-flex h-10 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-60",
        variant === "primary" && "bg-brand text-white hover:bg-teal-800",
        variant === "secondary" && "border border-line bg-white text-ink hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800",
        variant === "danger" && "bg-danger text-white hover:bg-red-800",
        variant === "ghost" && "text-muted hover:bg-slate-100 hover:text-ink dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white",
        className
      )}
    />
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(props, ref) {
  return <input ref={ref} {...props} className={clsx("admin-control h-10 w-full rounded-xl border border-line bg-white px-3 text-sm outline-none transition focus:border-brand focus:ring-4 focus:ring-cyan-600/10 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100", props.className)} />;
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(props, ref) {
  return <select ref={ref} {...props} className={clsx("admin-control h-10 w-full rounded-xl border border-line bg-white px-3 text-sm outline-none transition focus:border-brand focus:ring-4 focus:ring-cyan-600/10 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100", props.className)} />;
});

export function Field({
  label,
  error,
  children
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="admin-field grid gap-2 text-sm">
      <span className="font-medium text-ink">{label}</span>
      {children}
      {error ? <span className="text-xs text-danger">{error}</span> : null}
    </label>
  );
}

export function Panel({ title, action, children, className }: { title?: string; action?: React.ReactNode; children: React.ReactNode; className?: string }) {
  return (
    <section className={clsx("overflow-hidden rounded-2xl border border-line bg-panel shadow-subtle dark:border-slate-800 dark:bg-slate-900", className)}>
      {title || action ? (
        <div className="flex min-h-14 items-center justify-between border-b border-line px-5 dark:border-slate-800">
          {title ? <h2 className="text-sm font-bold text-ink dark:text-slate-100">{title}</h2> : <span />}
          {action}
        </div>
      ) : null}
      <div className="p-5">{children}</div>
    </section>
  );
}

export function Badge({ tone = "neutral", children }: { tone?: "neutral" | "good" | "warn" | "bad" | "info"; children: React.ReactNode }) {
  return (
    <span
      className={clsx(
        "inline-flex rounded-full px-2 py-0.5 text-xs font-medium",
        tone === "neutral" && "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200",
        tone === "good" && "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
        tone === "warn" && "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
        tone === "bad" && "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300",
        tone === "info" && "bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300"
      )}
    >
      {children}
    </span>
  );
}

export function EmptyState({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="rounded-md border border-dashed border-line p-6 text-center">
      <p className="text-sm font-medium text-ink">{title}</p>
      <p className="mt-1 text-sm text-muted">{detail}</p>
    </div>
  );
}

export function ErrorNotice({ message }: { message?: string }) {
  if (!message) return null;
  return <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-danger">{message}</div>;
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx("animate-pulse rounded-lg bg-slate-200/80", className)} aria-hidden="true" />;
}

export function PageHeader({ eyebrow, title, detail, action }: { eyebrow?: string; title: string; detail: string; action?: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        {eyebrow ? <p className="text-xs font-bold uppercase tracking-[0.18em] text-cyan-700">{eyebrow}</p> : null}
        <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 dark:text-white sm:text-3xl">{title}</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">{detail}</p>
      </div>
      {action}
    </div>
  );
}

export function Stat({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="admin-stat rounded-2xl border border-line bg-white p-5 shadow-subtle dark:border-slate-800 dark:bg-slate-900">
      <p className="admin-stat-label text-xs font-semibold uppercase tracking-wide text-muted">{label}</p>
      <div className="admin-stat-value mt-2 text-2xl font-bold text-ink dark:text-white">{value}</div>
    </div>
  );
}
