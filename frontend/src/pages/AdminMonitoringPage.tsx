import { useMutation, useQueries } from "@tanstack/react-query";
import { triggerAccountHealthCheck, getAccountHealth, getAccountMetrics, getAlertStatus, getAvailableMetrics, getDeploymentInfo, getDetailedHealth, getSystemStats, getTransactionMonitoringStats } from "../lib/queries";
import { Badge, Button, Panel, Stat } from "../components/ui";

export function AdminMonitoringPage() {
  const results = useQueries({
    queries: [
      { queryKey: ["monitoring", "account-health"], queryFn: getAccountHealth },
      { queryKey: ["monitoring", "account-metrics"], queryFn: getAccountMetrics },
      { queryKey: ["monitoring", "deployment"], queryFn: getDeploymentInfo },
      { queryKey: ["monitoring", "transaction-health"], queryFn: getDetailedHealth },
      { queryKey: ["monitoring", "transaction-stats"], queryFn: getTransactionMonitoringStats },
      { queryKey: ["monitoring", "system-stats"], queryFn: getSystemStats },
      { queryKey: ["monitoring", "alerts"], queryFn: getAlertStatus },
      { queryKey: ["monitoring", "available-metrics"], queryFn: getAvailableMetrics }
    ]
  });
  const healthCheck = useMutation({ mutationFn: triggerAccountHealthCheck });

  const [accountHealth, accountMetrics, deployment, transactionHealth, transactionStats, systemStats, alerts, availableMetrics] = results;
  const alertSummary = getAlertSummary(alerts.data);

  return (
    <div className="grid gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Monitoring</h1>
          <p className="text-sm text-muted">Privileged service health, deployment, alert, and metric summaries.</p>
        </div>
        <Button onClick={() => healthCheck.mutate()} disabled={healthCheck.isPending}>Run health check</Button>
      </div>
      <div className="grid gap-3 md:grid-cols-4">
        <Stat label="Account health" value={<StatusText value={statusText(accountHealth.data, accountHealth.isLoading)} />} />
        <Stat label="Transaction health" value={<StatusText value={statusText(transactionHealth.data, transactionHealth.isLoading, "healthy")} />} />
        <Stat label="Alerts" value={<StatusText value={alerts.isLoading ? "loading" : alertSummary.label} />} />
        <Stat label="System" value={<StatusText value={systemStats.isLoading ? "loading" : systemStats.data ? "available" : "unknown"} />} />
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        <MetricPanel
          title="Account metrics"
          rows={[
            metricRow("Health checks", accountMetrics.data, "health_check_total"),
            metricRow("Health failures", accountMetrics.data, "health_check_failure_total"),
            metricRow("Deployment success", accountMetrics.data, "deployment_success_total"),
            metricRow("Application health", accountMetrics.data, "application_health_score", "%")
          ]}
          isLoading={accountMetrics.isLoading}
          error={accountMetrics.error}
        />
        <MetricPanel
          title="Deployment"
          rows={[
            metricRow("Version", deployment.data, "version"),
            metricRow("Environment", deployment.data, "environment"),
            metricRow("Health score", deployment.data, "healthScore", "%"),
            metricRow("Uptime", deployment.data, "uptimeSeconds", "s")
          ]}
          isLoading={deployment.isLoading}
          error={deployment.error}
        />
        <MetricPanel
          title="Transaction stats"
          rows={[
            metricRow("Daily volume", transactionStats.data, "dailyVolume"),
            metricRow("Daily amount", transactionStats.data, "dailyAmount"),
            metricRow("Success rate", transactionStats.data, "successRate"),
            metricRow("Active transactions", transactionStats.data, "activeTransactions")
          ]}
          isLoading={transactionStats.isLoading}
          error={transactionStats.error}
        />
        <MetricPanel
          title="System stats"
          rows={[
            metricRow("CPU usage", systemStats.data, "system.cpuUsage", "%"),
            metricRow("JVM memory used", systemStats.data, "jvm.memoryUsed", "bytes"),
            metricRow("DB active connections", systemStats.data, "database.connectionPoolActive"),
            metricRow("Avg response time", systemStats.data, "http.avgResponseTime", "ms")
          ]}
          isLoading={systemStats.isLoading}
          error={systemStats.error}
        />
        <MetricPanel
          title="Alerts"
          rows={[
            { label: "Status", value: alertSummary.label, tone: alertSummary.tone },
            metricRow("Critical", alerts.data, "criticalAlerts"),
            metricRow("Warnings", alerts.data, "warningAlerts"),
            metricRow("Info", alerts.data, "infoAlerts"),
            metricRow("Suppressions", alerts.data, "activeAlertSuppressions")
          ]}
          isLoading={alerts.isLoading}
          error={alerts.error}
        />
        <MetricPanel
          title="Available metrics"
          rows={[
            metricRow("Total meters", availableMetrics.data, "totalMeters"),
            metricRow("Alert metrics", availableMetrics.data, "categories.alerts.length"),
            metricRow("JVM metrics", availableMetrics.data, "categories.jvm.length"),
            metricRow("System metrics", availableMetrics.data, "categories.system.length")
          ]}
          isLoading={availableMetrics.isLoading}
          error={availableMetrics.error}
        />
      </div>
    </div>
  );
}

type MetricRow = {
  label: string;
  value: string;
  tone?: "neutral" | "good" | "warn" | "bad" | "info";
};

function MetricPanel({ title, rows, isLoading, error }: { title: string; rows: MetricRow[]; isLoading: boolean; error: unknown }) {
  return (
    <Panel title={title}>
      {error instanceof Error ? <p className="text-sm text-danger">{error.message}</p> : null}
      {isLoading ? <LoadingRows /> : null}
      {!isLoading && !error ? (
        <dl className="grid gap-3 sm:grid-cols-2">
          {rows.map((row) => (
            <div key={row.label} className="rounded-md border border-line bg-white p-3">
              <dt className="text-xs font-medium uppercase tracking-wide text-muted">{row.label}</dt>
              <dd className="mt-1 text-sm font-semibold text-ink">
                {row.tone ? <Badge tone={row.tone}>{row.value}</Badge> : row.value}
              </dd>
            </div>
          ))}
        </dl>
      ) : null}
    </Panel>
  );
}

function LoadingRows() {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {["one", "two", "three", "four"].map((key) => (
        <div key={key} className="rounded-md border border-line bg-white p-3">
          <div className="h-3 w-24 animate-pulse rounded bg-slate-200" />
          <div className="mt-3 h-5 w-32 animate-pulse rounded bg-slate-200" />
        </div>
      ))}
    </div>
  );
}

function StatusText({ value }: { value: string }) {
  const normalized = value.toLowerCase();
  const tone = normalized === "up" || normalized === "healthy" || normalized === "available" || normalized === "0 active" ? "good" : normalized === "loading" ? "info" : normalized.includes("active") ? "warn" : "neutral";
  return <Badge tone={tone}>{value}</Badge>;
}

function statusText(data: unknown, isLoading: boolean, fallbackWhenPresent = "available") {
  if (isLoading) return "loading";
  const status = readPath(data, "status");
  if (status !== undefined) return String(status);
  return data ? fallbackWhenPresent : "unknown";
}

function getAlertSummary(data: unknown): MetricRow {
  if (!data) return { label: "unknown", value: "unknown", tone: "neutral" };
  if (readPath(data, "alertingEnabled") === false) return { label: "disabled", value: "disabled", tone: "neutral" };
  const active = ["criticalAlerts", "warningAlerts", "infoAlerts"].reduce((total, key) => total + toNumber(readPath(data, key)), 0);
  return active === 0 ? { label: "0 active", value: "0 active", tone: "good" } : { label: `${formatValue(active)} active`, value: `${formatValue(active)} active`, tone: "warn" };
}

function metricRow(label: string, data: unknown, path: string, suffix = ""): MetricRow {
  const value = readPath(data, path);
  return { label, value: formatValue(value, suffix) };
}

function readPath(data: unknown, path: string) {
  if (!data || typeof data !== "object") return undefined;
  return path.split(".").reduce<unknown>((value, key) => {
    if (Array.isArray(value) && key === "length") return value.length;
    if (!value || typeof value !== "object") return undefined;
    return (value as Record<string, unknown>)[key];
  }, data);
}

function toNumber(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function formatValue(value: unknown, suffix = "") {
  if (value === undefined || value === null || value === "") return "n/a";
  if (typeof value === "number") {
    const formatted = Number.isInteger(value) ? String(value) : value.toFixed(2);
    return suffix ? `${formatted} ${suffix}` : formatted;
  }
  return suffix ? `${String(value)} ${suffix}` : String(value);
}
