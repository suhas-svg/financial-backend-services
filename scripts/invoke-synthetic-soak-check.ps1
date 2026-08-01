param(
    [Parameter(Mandatory = $true)][string]$EnvironmentFile,
    [Parameter(Mandatory = $true)][string]$EvidenceDirectory,
    [ValidateRange(1, 60)][int]$IntervalMinutes = 5
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$environmentPath = (Resolve-Path $EnvironmentFile).Path
$evidencePath = [System.IO.Path]::GetFullPath($EvidenceDirectory)
$rootPrefix = $root.TrimEnd('\') + '\'
if ($environmentPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The synthetic environment file must remain outside the repository"
}
if ($evidencePath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Soak evidence must be written outside the repository"
}

foreach ($line in Get-Content $environmentPath) {
    if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)) {
        continue
    }
    if ($line -notmatch '^([^=]+)=(.*)$') {
        throw "The synthetic environment file contains an invalid line"
    }
    [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2], "Process")
}
$env:SANDBOX_PROFILE = "synthetic-sandbox"

$statePath = Join-Path $evidencePath "state.json"
if (Test-Path $statePath) {
    $state = Get-Content $statePath -Raw | ConvertFrom-Json
    if ($state.completed -eq $true) {
        Write-Output "Synthetic soak is already complete; no new checkpoint was written."
        return
    }
}

& (Join-Path $PSScriptRoot "run-synthetic-soak.ps1") `
    -EvidenceDirectory $evidencePath `
    -IntervalMinutes $IntervalMinutes `
    -SingleCheck