[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$EvidencePath,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$ExpectedSha256,
    [datetime]$AsOf = [datetime]::UtcNow
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$resolved = [System.IO.Path]::GetFullPath($EvidencePath)
$repoPrefix = $repoRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
if ($resolved.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Production-readiness evidence must be supplied from an external evidence store, not the repository."
}
if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
    throw "Production-readiness evidence file was not found."
}

$actualSha256 = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
$actualBytes = [System.Text.Encoding]::ASCII.GetBytes($actualSha256)
$expectedBytes = [System.Text.Encoding]::ASCII.GetBytes($ExpectedSha256.ToLowerInvariant())
$difference = 0
for ($index = 0; $index -lt $actualBytes.Length; $index++) {
    $difference = $difference -bor ($actualBytes[$index] -bxor $expectedBytes[$index])
}
if ($difference -ne 0) {
    throw "Production-readiness evidence digest does not match the approved digest."
}

$raw = Get-Content -LiteralPath $resolved -Raw
if ($raw -match '(?i)-----BEGIN|bearer\s+[a-z0-9._-]+|password\s*[:=]|api[_-]?key\s*[:=]') {
    throw "Production-readiness evidence must contain references only, never secret material."
}
$manifest = $raw | ConvertFrom-Json

if ($manifest.manifestVersion -ne 1 -or $manifest.scope -ne "REAL_MONEY_PRODUCTION") {
    throw "Production-readiness evidence has an unsupported version or scope."
}
foreach ($field in @("manifestId", "changeReference")) {
    $value = [string]$manifest.$field
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Production-readiness evidence is missing $field."
    }
}

$requiredGates = @(
    "PROVIDERS_PAYMENT_RAILS",
    "IDP_KMS",
    "WEBHOOK_VERIFICATION",
    "LEGAL_PRIVACY_JURISDICTION",
    "BACKUP_RESTORE",
    "ALERTING_INCIDENT",
    "LOAD_SOAK",
    "CHANGE_GOVERNANCE"
)
$entries = @($manifest.gates)
if ($entries.Count -ne $requiredGates.Count) {
    throw "Production-readiness evidence must contain exactly $($requiredGates.Count) exit gates."
}

$seen = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)
$now = $AsOf.ToUniversalTime()
foreach ($entry in $entries) {
    $gate = ([string]$entry.gate).ToUpperInvariant()
    if ($gate -notin $requiredGates -or -not $seen.Add($gate)) {
        throw "Production-readiness evidence contains an unknown or duplicate gate: $gate"
    }
    if ([string]$entry.status -ne "APPROVED") {
        throw "Production-readiness gate $gate is not approved."
    }
    $owner = [string]$entry.owner
    $reviewer = [string]$entry.reviewer
    if ([string]::IsNullOrWhiteSpace($owner) -or
        [string]::IsNullOrWhiteSpace($reviewer) -or
        $owner.Equals($reviewer, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Production-readiness gate $gate requires distinct owner and reviewer identities."
    }
    $reference = [string]$entry.reference
    if ($reference -notmatch '^(https|evidence|ticket|vault|kms|secret)://\S+$' -or
        $reference -match '(?i)(^|[/_.-])(local|test|demo|example|placeholder)([/_.-]|$)') {
        throw "Production-readiness gate $gate has an invalid or non-production evidence reference."
    }
    if ([string]$entry.evidenceSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Production-readiness gate $gate requires a SHA-256 evidence digest."
    }
    $approvedAt = ([datetime]$entry.approvedAt).ToUniversalTime()
    $expiresAt = ([datetime]$entry.expiresAt).ToUniversalTime()
    if ($approvedAt -gt $now -or $expiresAt -le $now) {
        throw "Production-readiness gate $gate is not currently effective."
    }
}

$missing = @($requiredGates | Where-Object { -not $seen.Contains($_) })
if ($missing.Count -gt 0) {
    throw "Production-readiness evidence is missing gates: $($missing -join ', ')"
}

[pscustomobject]@{
    ready = $true
    manifestId = [string]$manifest.manifestId
    scope = [string]$manifest.scope
    changeReference = [string]$manifest.changeReference
    gateCount = $seen.Count
    verifiedAt = $now.ToString("o")
    manifestSha256 = $actualSha256
} | ConvertTo-Json
