[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$verifier = Join-Path $PSScriptRoot "verify-production-readiness-evidence.ps1"
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("production-readiness-evidence-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null

function Write-Manifest([string]$Path, [string[]]$Gates, [string]$ExpiresAt) {
    $entries = @()
    foreach ($gate in $Gates) {
        $slug = $gate.ToLowerInvariant().Replace("_", "-")
        $entries += [ordered]@{
            gate = $gate
            status = "APPROVED"
            reference = "evidence://external-control/$slug/7f39b581"
            evidenceSha256 = ("a" * 64)
            owner = "owner-$slug"
            reviewer = "reviewer-$slug"
            approvedAt = "2026-07-01T00:00:00Z"
            expiresAt = $ExpiresAt
        }
    }
    [ordered]@{
        manifestVersion = 1
        manifestId = "readiness-7f39b581"
        scope = "REAL_MONEY_PRODUCTION"
        changeReference = "ticket://change/7f39b581"
        gates = $entries
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Path
}

$allGates = @(
    "PROVIDERS_PAYMENT_RAILS", "IDP_KMS", "WEBHOOK_VERIFICATION",
    "LEGAL_PRIVACY_JURISDICTION", "BACKUP_RESTORE", "ALERTING_INCIDENT",
    "LOAD_SOAK", "CHANGE_GOVERNANCE"
)

try {
    $valid = Join-Path $fixtureRoot "valid.json"
    Write-Manifest $valid $allGates "2027-07-01T00:00:00Z"
    $digest = (Get-FileHash -LiteralPath $valid -Algorithm SHA256).Hash
    & $verifier -EvidencePath $valid -ExpectedSha256 $digest -AsOf "2026-07-29T00:00:00Z"

    $repositoryLocalRejected = $false
    $repositorySchema = Join-Path $PSScriptRoot "..\docs\operations\production-readiness-evidence.schema.json"
    try {
        & $verifier -EvidencePath $repositorySchema `
            -ExpectedSha256 (Get-FileHash $repositorySchema -Algorithm SHA256).Hash `
            -AsOf "2026-07-29T00:00:00Z"
    } catch {
        $repositoryLocalRejected = $true
    }
    if (-not $repositoryLocalRejected) {
        throw "Repository-local production-readiness evidence was accepted."
    }
    $wrongDigestRejected = $false
    try {
        & $verifier -EvidencePath $valid -ExpectedSha256 ("b" * 64) -AsOf "2026-07-29T00:00:00Z"
    } catch {
        $wrongDigestRejected = $true
    }
    if (-not $wrongDigestRejected) {
        throw "A mismatched production-readiness manifest digest was accepted."
    }

    $incomplete = Join-Path $fixtureRoot "incomplete.json"
    Write-Manifest $incomplete $allGates[0..6] "2027-07-01T00:00:00Z"
    $incompleteRejected = $false
    try {
        & $verifier -EvidencePath $incomplete `
            -ExpectedSha256 (Get-FileHash $incomplete -Algorithm SHA256).Hash `
            -AsOf "2026-07-29T00:00:00Z"
    } catch {
        $incompleteRejected = $true
    }
    if (-not $incompleteRejected) {
        throw "An incomplete production-readiness manifest was accepted."
    }

    $expired = Join-Path $fixtureRoot "expired.json"
    Write-Manifest $expired $allGates "2026-07-28T00:00:00Z"
    $expiredRejected = $false
    try {
        & $verifier -EvidencePath $expired `
            -ExpectedSha256 (Get-FileHash $expired -Algorithm SHA256).Hash `
            -AsOf "2026-07-29T00:00:00Z"
    } catch {
        $expiredRejected = $true
    }
    if (-not $expiredRejected) {
        throw "Expired production-readiness evidence was accepted."
    }
} finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
}

Write-Host "Production-readiness evidence fail-closed self-test passed."
