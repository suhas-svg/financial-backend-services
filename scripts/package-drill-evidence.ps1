# Bundles a drill's raw evidence into one archive and writes the manifest entry the
# real-money readiness gate needs (docs/operations/production-readiness-evidence.schema.json):
# the run URL as "reference" and the archive's SHA-256 as "evidenceSha256".
param(
    [Parameter(Mandatory = $true)][ValidateSet("LOAD_SOAK", "BACKUP_RESTORE")][string]$Gate,
    [Parameter(Mandatory = $true)][string]$Drill,
    [Parameter(Mandatory = $true)][string]$EvidenceDirectory,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [Parameter(Mandatory = $true)][bool]$Passed,
    [string]$Summary = ""
)
$ErrorActionPreference = "Stop"

$source = (Resolve-Path -LiteralPath $EvidenceDirectory).Path
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$output = (Resolve-Path -LiteralPath $OutputDirectory).Path
$runUrl = if ($env:GITHUB_RUN_ID) {
    "$($env:GITHUB_SERVER_URL)/$($env:GITHUB_REPOSITORY)/actions/runs/$($env:GITHUB_RUN_ID)"
} else { "local" }
$stamp = [DateTimeOffset]::UtcNow.ToString("yyyyMMddTHHmmssZ")
$archive = Join-Path $output "$($Drill.ToLowerInvariant())-evidence-$stamp.tar.gz"

tar -czf $archive -C $source .
if ($LASTEXITCODE -ne 0) { throw "Unable to archive $source" }
$sha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()

$manifest = [ordered]@{
    gate = $Gate
    drill = $Drill
    classification = "SYNTHETIC_NO_REAL_MONEY"
    passed = $Passed
    reference = $runUrl
    commit = $env:GITHUB_SHA
    archive = (Split-Path -Leaf $archive)
    evidenceSha256 = $sha256
    collectedAt = [DateTimeOffset]::UtcNow.ToString("o")
    summary = $Summary
}
$manifestPath = Join-Path $output "evidence-manifest.json"
$manifest | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 $manifestPath

if ($env:GITHUB_STEP_SUMMARY) {
    @(
        "### $Drill drill: $(if ($Passed) { 'passed' } else { 'FAILED' })",
        "",
        "| Readiness gate | Reference | evidenceSha256 |",
        "| --- | --- | --- |",
        "| ``$Gate`` | $runUrl | ``$sha256`` |",
        "",
        $Summary
    ) | Add-Content -Encoding utf8 $env:GITHUB_STEP_SUMMARY
}
Write-Output $manifestPath
