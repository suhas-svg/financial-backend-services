param([switch]$RequireDocker)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$alertmanager = Join-Path $root "transaction-service\monitoring\alertmanager\alertmanager.yml"
$rules = @(
    (Join-Path $root "transaction-service\src\main\resources\transaction_service_alerts.yml"),
    (Join-Path $root "transaction-service\src\main\resources\phase4_operational_alerts.yml"),
    (Join-Path $root "account-service\monitoring\alert_rules.yml")
)
$forbidden = 'hooks\.slack\.com|pagerduty|company\.com|smtp_auth_password|YOUR[_/]|password\s*:'
if ((Get-Content -Raw $alertmanager) -match $forbidden) { throw "Alertmanager config contains a placeholder destination or secret-shaped value" }
if ((Get-Content -Raw $alertmanager) -notmatch 'send_resolved:\s*true') { throw "Resolution delivery is required" }
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    if ($RequireDocker) { throw "Docker is required for authoritative alert config validation" }
    Write-Warning "Docker unavailable; completed structural checks only"
    return
}
docker run --rm --entrypoint amtool -v "${alertmanager}:/etc/alertmanager/alertmanager.yml:ro" prom/alertmanager:v0.28.1 check-config /etc/alertmanager/alertmanager.yml
if ($LASTEXITCODE -ne 0) { throw "Alertmanager validation failed" }
foreach ($rule in $rules) {
    docker run --rm --entrypoint promtool -v "${rule}:/rules.yml:ro" prom/prometheus:v3.4.1 check rules /rules.yml
    if ($LASTEXITCODE -ne 0) { throw "Prometheus rule validation failed: $rule" }
}
Write-Output "Alert configuration valid: fail-closed local receiver, resolution delivery, and all rule files parsed."
