param([string]$ComposeFile = "docker-compose.synthetic-sandbox.yml")

$ErrorActionPreference = "Stop"
$required = @(
    "SANDBOX_IMAGE_TAG", "ACCOUNT_DB_PASSWORD", "TRANSACTION_DB_PASSWORD", "REDIS_PASSWORD",
    "JWT_SECRET", "INTERNAL_JWT_SECRET", "MFA_ENCRYPTION_KEY", "SANDBOX_BOOTSTRAP_TOKEN"
)
$missing = @($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
if ($missing.Count -gt 0) { throw "Missing required variables: $($missing -join ', ')" }

docker compose -f $ComposeFile config --quiet
if ($LASTEXITCODE -ne 0) { throw "Compose configuration validation failed" }

$config = docker compose -f $ComposeFile config --format json | ConvertFrom-Json
$expected = @("redis", "account-postgres", "transaction-postgres", "account-service", "transaction-service", "frontend", "gateway")
$actual = @($config.services.PSObject.Properties.Name)
foreach ($service in $expected) {
    if ($actual -notcontains $service) { throw "Missing required service: $service" }
}
if ($actual -contains "financial-mcp-server") { throw "financial-mcp-server must remain outside sandbox runtime scope" }

$published = @($config.services.PSObject.Properties | ForEach-Object {
    $name = $_.Name
    @($_.Value.ports) | Where-Object { $_ } | ForEach-Object { "${name}:$($_.published):$($_.target)" }
})
if ($published.Count -ne 1 -or $published[0] -notmatch '^gateway:\d+:8443$') {
    throw "Only the gateway TLS port may be published; found: $($published -join ', ')"
}

Write-Output "Synthetic sandbox configuration valid: 7 services, 1 loopback gateway port, 0 internal ports published."
