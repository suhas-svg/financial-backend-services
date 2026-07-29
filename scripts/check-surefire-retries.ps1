[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string[]]$ReportRoots,
    [datetime]$EnforcementDate = [datetime]"2026-08-12T00:00:00Z",
    [datetime]$AsOf = [datetime]::UtcNow
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$retryOnlySuccesses = [System.Collections.Generic.List[object]]::new()
$reportsScanned = 0

foreach ($root in $ReportRoots) {
    if (-not (Test-Path -LiteralPath $root)) {
        continue
    }
    foreach ($report in Get-ChildItem -LiteralPath $root -Filter "TEST-*.xml" -File) {
        $reportsScanned++
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        foreach ($testCase in @($document.SelectNodes("//testcase"))) {
            $retryEvidence = @($testCase.SelectNodes("./flakyFailure | ./flakyError"))
            if ($retryEvidence.Count -gt 0) {
                $retryOnlySuccesses.Add([pscustomobject]@{
                    report = $report.Name
                    class = [string]$testCase.classname
                    test = [string]$testCase.name
                    retries = $retryEvidence.Count
                })
            }
        }
    }
}

$summary = [pscustomobject]@{
    reportsScanned = $reportsScanned
    retryOnlySuccessCount = $retryOnlySuccesses.Count
    enforcementDateUtc = $EnforcementDate.ToUniversalTime().ToString("o")
    asOfUtc = $AsOf.ToUniversalTime().ToString("o")
    retryOnlySuccesses = @($retryOnlySuccesses)
}
$json = $summary | ConvertTo-Json -Depth 5
Write-Host $json

if ($env:GITHUB_STEP_SUMMARY) {
    @(
        "### Surefire retry governance"
        ""
        "- Reports scanned: $reportsScanned"
        "- Retry-only successes: $($retryOnlySuccesses.Count)"
        "- Enforcement begins: $($EnforcementDate.ToUniversalTime().ToString('yyyy-MM-dd')) UTC"
    ) | Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY
    foreach ($item in $retryOnlySuccesses) {
        "- ``$($item.class).$($item.test)`` passed only after $($item.retries) retry attempt(s)." |
            Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY
    }
}

if ($retryOnlySuccesses.Count -eq 0) {
    Write-Host "Surefire retry governance passed: no retry-only test success was reported."
    return
}

if ($AsOf.ToUniversalTime() -ge $EnforcementDate.ToUniversalTime()) {
    throw "$($retryOnlySuccesses.Count) test(s) passed only after Surefire retry; retry-only success is forbidden after the adoption window."
}

Write-Warning "$($retryOnlySuccesses.Count) test(s) passed only after Surefire retry. Reporting-only adoption window remains open."
