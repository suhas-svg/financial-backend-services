param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("RESET SYNTHETIC SANDBOX")]
    [string]$Confirmation
)

$ErrorActionPreference = "Stop"
if ($env:SANDBOX_PROFILE -ne "synthetic-sandbox") {
    throw "Reset is technically gated to SANDBOX_PROFILE=synthetic-sandbox"
}
$compose = Join-Path $PSScriptRoot "..\docker-compose.synthetic-sandbox.yml"
$resolved = (Resolve-Path -LiteralPath $compose).Path
if ((Split-Path -Leaf $resolved) -ne "docker-compose.synthetic-sandbox.yml") {
    throw "Unexpected Compose target"
}

docker compose --project-name financial-synthetic-sandbox -f $resolved down --volumes --remove-orphans
if ($LASTEXITCODE -ne 0) { throw "Synthetic sandbox reset failed" }
docker compose --project-name financial-synthetic-sandbox -f $resolved up --build --detach --wait
if ($LASTEXITCODE -ne 0) { throw "Synthetic sandbox restart failed" }

Write-Output "Synthetic volumes were reset and the stack was recreated. Complete one-time operator bootstrap, MFA enrollment, and the idempotent seed API flow from the runbook."
