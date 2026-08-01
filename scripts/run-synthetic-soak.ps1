param(
    [string]$EvidenceDirectory = (Join-Path $PSScriptRoot "..\artifacts\synthetic-soak"),
    [ValidateRange(1, 10090)][int]$RunMinutes = 60,
    [ValidateRange(1, 60)][int]$IntervalMinutes = 5,
    [switch]$SingleCheck
)

$ErrorActionPreference = "Stop"
if ($env:SANDBOX_PROFILE -ne "synthetic-sandbox") {
    throw "Soak requires SANDBOX_PROFILE=synthetic-sandbox"
}

$targetHours = 168
$project = "financial-synthetic-sandbox"
$compose = Join-Path $PSScriptRoot "..\docker-compose.synthetic-sandbox.yml"
$maximumGapSeconds = ($IntervalMinutes * 60 * 2) + 30
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$statePath = Join-Path $EvidenceDirectory "state.json"
$reportPath = Join-Path $EvidenceDirectory "checks.ndjson"
$lockPath = Join-Path $EvidenceDirectory "runner.lock"

try {
    $runnerLock = [System.IO.File]::Open(
        $lockPath,
        [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None
    )
} catch {
    throw "Another soak runner already owns $lockPath"
}

function Invoke-CountQuery(
    [string]$Container,
    [string]$Database,
    [string]$User,
    [string]$Sql
) {
    $value = docker exec $Container psql -U $User -d $Database -tAc $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "Invariant query failed for $Database"
    }
    return [int]$value.Trim()
}

function Get-FinancialInvariants {
    $transactionDatabase = (
        docker compose --project-name $project -f $compose ps -q transaction-postgres
    ).Trim()
    $accountDatabase = (
        docker compose --project-name $project -f $compose ps -q account-postgres
    ).Trim()
    if ([string]::IsNullOrWhiteSpace($transactionDatabase) -or
        [string]::IsNullOrWhiteSpace($accountDatabase)) {
        throw "Synthetic database containers are unavailable"
    }

    return [ordered]@{
        unbalancedJournals = Invoke-CountQuery $transactionDatabase transaction_sandbox transaction_app @"
SELECT COUNT(*) FROM (
    SELECT journal.journal_id
      FROM journal_transactions journal
      LEFT JOIN journal_postings posting ON posting.journal_id=journal.journal_id
     GROUP BY journal.journal_id
    HAVING COUNT(posting.posting_id)<2
        OR COALESCE(SUM(posting.amount) FILTER (WHERE posting.direction='DEBIT'),0)
           <> COALESCE(SUM(posting.amount) FILTER (WHERE posting.direction='CREDIT'),0)
        OR COUNT(DISTINCT posting.currency)>1
) invalid
"@
        completedTransactionsWithoutJournal = Invoke-CountQuery $transactionDatabase transaction_sandbox transaction_app @"
SELECT COUNT(*) FROM transactions WHERE status='COMPLETED' AND journal_id IS NULL
"@
        duplicateTransactionIdempotency = Invoke-CountQuery $transactionDatabase transaction_sandbox transaction_app @"
SELECT COUNT(*) FROM (
    SELECT created_by,type,idempotency_key
      FROM transactions
     WHERE idempotency_key IS NOT NULL
     GROUP BY created_by,type,idempotency_key
    HAVING COUNT(*)>1
) duplicate
"@
        stuckScheduledTransferRuns = Invoke-CountQuery $transactionDatabase transaction_sandbox transaction_app @"
SELECT COUNT(*) FROM scheduled_transfer_runs
 WHERE status='PROCESSING' AND started_at<CURRENT_TIMESTAMP-INTERVAL '300 seconds'
"@
        staleFinancialOperationClaims = Invoke-CountQuery $transactionDatabase transaction_sandbox transaction_app @"
SELECT COUNT(*) FROM financial_operation_runs
 WHERE status='RUNNING' AND claim_until<CURRENT_TIMESTAMP
"@
        failedFinancialOperations = Invoke-CountQuery $transactionDatabase transaction_sandbox transaction_app @"
SELECT COUNT(*) FROM financial_operation_runs WHERE status='FAILED'
"@
        terminalLedgerOutbox = Invoke-CountQuery $transactionDatabase transaction_sandbox transaction_app @"
SELECT COUNT(*) FROM ledger_projection_outbox
 WHERE delivered_at IS NULL AND last_error LIKE 'TERMINAL:%'
"@
        terminalNotificationReceipts = Invoke-CountQuery $accountDatabase account_sandbox account_app @"
SELECT COUNT(*) FROM notification_provider_receipts
 WHERE terminal_at IS NOT NULL AND reconciled_at IS NULL
"@
    }
}

try {
    if (Test-Path $statePath) {
        $state = Get-Content -Raw $statePath | ConvertFrom-Json
        if ([int]$state.intervalMinutes -ne $IntervalMinutes) {
            throw "Existing soak interval does not match the requested interval"
        }
    } else {
        $state = [ordered]@{
            startedAt = [DateTimeOffset]::UtcNow.ToString("o")
            targetHours = $targetHours
            intervalMinutes = $IntervalMinutes
            maximumGapSeconds = $maximumGapSeconds
            checks = 0
            failures = 0
            coverageGaps = 0
            lastCheckedAt = $null
            completed = $false
        }
    }

    $deadline = [DateTimeOffset]::UtcNow.AddMinutes($(if ($SingleCheck) { 0 } else { $RunMinutes }))
    do {
        $now = [DateTimeOffset]::UtcNow
        $errors = @()
        $unhealthy = @()
        $services = @()
        $invariants = $null
        try {
            $serviceOutput = docker compose --project-name $project -f $compose ps --format json 2>&1
            if ($LASTEXITCODE -ne 0) {
                throw "Synthetic service discovery failed"
            }
            $services = @($serviceOutput | ConvertFrom-Json)
            $unhealthy = @(
                $services |
                    Where-Object {
                        $_.State -ne "running" -or ($_.Health -and $_.Health -ne "healthy")
                    } |
                    ForEach-Object { $_.Service }
            )
            if ($services.Count -ne 7) {
                $errors += "Expected seven services but observed $($services.Count)"
            }
            if ($unhealthy.Count -eq 0 -and $errors.Count -eq 0) {
                $invariants = Get-FinancialInvariants
                foreach ($invariant in $invariants.GetEnumerator()) {
                    if ([int]$invariant.Value -ne 0) {
                        $errors += "$($invariant.Key)=$($invariant.Value)"
                    }
                }
            }
        } catch {
            $errors += $_.Exception.Message
        }

        $gapSeconds = 0
        if ($state.lastCheckedAt) {
            $gapSeconds = [Math]::Floor(
                ($now - [DateTimeOffset]::Parse($state.lastCheckedAt)).TotalSeconds
            )
            if ($gapSeconds -gt [int]$state.maximumGapSeconds) {
                $state.coverageGaps = [int]$state.coverageGaps + 1
                $errors += "Evidence coverage gap was $gapSeconds seconds"
            }
        }

        $healthy = $unhealthy.Count -eq 0 -and $errors.Count -eq 0
        $entry = [ordered]@{
            checkedAt = $now.ToString("o")
            healthy = $healthy
            serviceCount = $services.Count
            unhealthyServices = $unhealthy
            invariants = $invariants
            gapSeconds = $gapSeconds
            errors = $errors
        }
        Add-Content -Encoding utf8 $reportPath ($entry | ConvertTo-Json -Compress -Depth 5)

        $state.checks = [int]$state.checks + 1
        if (-not $healthy) {
            $state.failures = [int]$state.failures + 1
        }
        $state.lastCheckedAt = $now.ToString("o")
        $elapsed = $now - [DateTimeOffset]::Parse($state.startedAt)
        $minimumChecks = [Math]::Floor(($targetHours * 60) / $IntervalMinutes) + 1
        $state.completed = (
            $elapsed.TotalHours -ge $targetHours -and
            [int]$state.checks -ge $minimumChecks -and
            [int]$state.failures -eq 0 -and
            [int]$state.coverageGaps -eq 0
        )
        $state | ConvertTo-Json | Set-Content -Encoding utf8 $statePath

        if ($SingleCheck -and -not $healthy) {
            throw "Synthetic soak checkpoint failed; review $reportPath"
        }
        if ($SingleCheck -or [DateTimeOffset]::UtcNow -ge $deadline) {
            break
        }
        Start-Sleep -Seconds ([Math]::Max(60, $IntervalMinutes * 60))
    } while ($true)

    Write-Output (
        "Soak evidence: checks=$($state.checks), failures=$($state.failures), " +
        "coverageGaps=$($state.coverageGaps), completed=$($state.completed). " +
        "Seven-day gate is passed only when completed=true."
    )
} finally {
    if ($null -ne $runnerLock) {
        $runnerLock.Dispose()
    }
}