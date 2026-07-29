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

    & $checker -ReportRoots $clean

    $failedImmediately = $false
    try {
        & $checker -ReportRoots $flaky
    } catch {
        $failedImmediately = $true
    }
    if (-not $failedImmediately) {
        throw "Retry-only success did not fail immediately."
    }
} finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
}

Write-Host "Surefire retry governance self-test passed."
