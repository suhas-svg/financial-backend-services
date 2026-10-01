# Observability: tracing and SLOs

## Distributed tracing

Both services use Micrometer Tracing with the OpenTelemetry bridge and export spans
over OTLP/HTTP. A transfer is one trace: the inbound request to transaction-service,
its call to account-service (W3C `traceparent` is propagated by the observed
`WebClient`/`RestClient` builders), and a span for every SQL statement in each service
(`datasource-micrometer`, statement text only — bound values are never recorded).

| Setting | Env var | Default |
| --- | --- | --- |
| OTLP endpoint | `MANAGEMENT_OTLP_TRACING_ENDPOINT` | unset = no export |
| Sampling rate | `TRACING_SAMPLING_PROBABILITY` | `0.1` |

Every log line carries `traceId` and `spanId`, and transaction-service returns the
trace ID in `X-Trace-ID`, so a support ticket can point straight at a trace.

**Local sandbox (Jaeger):**

```bash
docker compose -f docker-compose.synthetic-sandbox.yml -f docker-compose.synthetic-tracing.yml up -d --build --wait
```

Make a transfer in the UI, then open <http://127.0.0.1:16686> and search for service
`transaction-service`, operation `http post /api/transactions/transfer`.

**Kubernetes:** set `tracing.otlpEndpoint` in either chart (for example
`http://otel-collector.monitoring:4318/v1/traces` for an OpenTelemetry Collector that
forwards to Tempo or Jaeger). The chart then also opens NetworkPolicy egress to the
collector (`tracing.collectorNamespace`, `tracing.collectorPort`).

## Service level objectives

Rules: [`infrastructure/observability/prometheus/slo-rules.yml`](../../infrastructure/observability/prometheus/slo-rules.yml),
unit-tested in CI with `promtool test rules`.

| SLO | Objective | SLI |
| --- | --- | --- |
| Transfer success | 99.9% of `POST /api/transactions/transfer` do not return 5xx | declines (4xx) are correct answers and do not spend budget |
| Transfer latency | p99 ≤ 1 s (99% of transfers complete within 1 s) | `http_server_requests_seconds_bucket{le="1.0"}` |
| Service availability | 99.9% of non-actuator requests per service do not return 5xx | |
| Service latency | 99% of non-actuator requests per service complete within 500 ms | |

All windows are 30 days. Alerts use multi-window, multi-burn-rate rules:

| Severity | Fires when | Budget gone in |
| --- | --- | --- |
| `page` | 14.4× burn over 1 h (and 5 m), or 6× over 6 h (and 30 m) | ~2–5 days |
| `ticket` | 3× burn over 1 d (and 2 h), or 1× over 3 d (and 6 h) | ~10–30 days |

A minimum request rate keeps idle services from paging on a single failed request.

**When a transfer alert fires:** open Jaeger/Tempo, filter on the transfer operation
with `error=true` (success SLO) or `minDuration=1s` (latency SLO), and follow the
trace to the hop that failed or grew: transaction-service, account-service, or a
specific SQL statement.

The sandbox Prometheus (`docker-compose.synthetic-alerting.yml`) loads these rules.
In Kubernetes, load the same file through a `PrometheusRule` or your rule sidecar.
Prometheus scrapes `/actuator/prometheus` with the bearer token in
`monitoring.serviceMonitor.scrapeToken` (`METRICS_SCRAPE_TOKEN` in the pod).
