[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$checker = Join-Path $PSScriptRoot "check-surefire-retries.ps1"
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("surefire-retry-governance-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null

try {
    $clean = Join-Path $fixtureRoot "clean"
    $flaky = Join-Path $fixtureRoot "flaky"
    New-Item -ItemType Directory -Path $clean, $flaky | Out-Null
    @'
<testsuite tests="1" failures="0" errors="0">
  <testcase classname="example.StableTest" name="passesFirstTime"/>
</testsuite>
'@ | Set-Content -LiteralPath (Join-Path $clean "TEST-stable.xml")
    @'
<testsuite tests="1" failures="0" errors="0">
  <testcase classname="example.FlakyTest" name="passesOnRetry">
    <flakyFailure message="first attempt failed" type="java.lang.AssertionError"/>
  </testcase>
</testsuite>
'@ | Set-Content -LiteralPath (Join-Path $flaky "TEST-flaky.xml")

    & $checker -ReportRoots $clean -EnforcementDate "2026-01-01T00:00:00Z" -AsOf "2026-07-29T00:00:00Z"
    & $checker -ReportRoots $flaky -EnforcementDate "2026-08-12T00:00:00Z" -AsOf "2026-07-29T00:00:00Z"

    $failedAfterWindow = $false
    try {
        & $checker -ReportRoots $flaky -EnforcementDate "2026-08-12T00:00:00Z" -AsOf "2026-08-12T00:00:00Z"
    } catch {
        $failedAfterWindow = $true
    }
    if (-not $failedAfterWindow) {
        throw "Retry-only success did not fail after the adoption window."
    }
} finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
}

Write-Host "Surefire retry governance self-test passed."
