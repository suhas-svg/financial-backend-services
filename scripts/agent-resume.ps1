[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('codex', 'antigravity')][string]$Agent,
    [switch]$Force,
    [ValidateRange(5, 1440)][int]$LeaseMinutes = 120
)

$ErrorActionPreference = 'Stop'

function Invoke-GitRaw {
    param([string]$WorkingDirectory, [string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & git -C $WorkingDirectory @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output -join [Environment]::NewLine).Trim()
    }
}

function Invoke-Git {
    param([string]$WorkingDirectory, [string[]]$Arguments)
    $result = Invoke-GitRaw $WorkingDirectory $Arguments
    if ($result.ExitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed: $($result.Output)"
    }
    return $result.Output
}

function Write-JsonAtomically {
    param([Parameter(Mandatory)][object]$Value, [Parameter(Mandatory)][string]$Path)
    $temporary = "$Path.tmp.$PID"
    try {
        $Value | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $temporary -Encoding UTF8
        Move-Item -LiteralPath $temporary -Destination $Path -Force
    }
    finally {
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

function Read-JsonFile {
    param([string]$Path, [string]$Label)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Label file is missing: $Path"
    }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    }
    catch {
        throw "$Label is invalid JSON: $($_.Exception.Message)"
    }
}

function Assert-Properties {
    param([object]$Value, [string[]]$Names, [string]$Label)
    foreach ($name in $Names) {
        if ($Value.PSObject.Properties.Name -notcontains $name) {
            throw "$Label is missing required field: $name"
        }
    }
}

function Assert-RequiredText {
    param([object]$Value, [string]$Name)
    if ([string]::IsNullOrWhiteSpace([string]$Value)) {
        throw "$Name is required"
    }
}

function Convert-OriginToRepository {
    param([string]$Origin)
    if ($Origin -match '^(?:https?://github\.com/|git@github\.com:)([^/]+/[^/]+?)(?:\.git)?$') {
        return $Matches[1]
    }
    return $null
}

function Assert-NoSecretShape {
    param([string[]]$Values)
    $secretPattern = '(?i)(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|(?:password|token|secret|api[_-]?key)\s*[:=]\s*\S+)'
    foreach ($value in $Values) {
        if ($null -ne $value -and $value -match $secretPattern) {
            throw 'Refusing to resume a handoff containing a secret-shaped value'
        }
    }
}

$repoRoot = Invoke-Git (Get-Location).Path @('rev-parse', '--show-toplevel')
$agentDirectory = Join-Path $repoRoot '.agent'
$handoffPath = Join-Path $agentDirectory 'active-handoff.json'
$leasePath = Join-Path $agentDirectory 'lease.json'
$handoff = Read-JsonFile $handoffPath 'Active handoff'

$handoffFields = @(
    'schemaVersion', 'taskId', 'objective', 'sourceAgent', 'targetAgent',
    'repository', 'branch', 'baseBranch', 'headCommit', 'worktree', 'status',
    'completed', 'remaining', 'blockers', 'changedFiles', 'validation',
    'nextAction', 'updatedAt'
)
Assert-Properties $handoff $handoffFields 'Active handoff'
if ([int]$handoff.schemaVersion -ne 1) {
    throw "Unsupported schemaVersion: $($handoff.schemaVersion)"
}
if ($handoff.status -eq 'idle') {
    throw 'No active handoff is available'
}
if ($handoff.status -notin @('local_only', 'ready_for_handoff')) {
    throw "Unsupported handoff status: $($handoff.status)"
}
foreach ($field in @('taskId', 'objective', 'repository', 'branch', 'baseBranch', 'headCommit', 'nextAction')) {
    Assert-RequiredText $handoff.$field "Active handoff $field"
}
if ($handoff.sourceAgent -notin @('codex', 'antigravity') -or $handoff.targetAgent -notin @('codex', 'antigravity')) {
    throw 'Active handoff contains an unsupported agent'
}
if ($handoff.targetAgent -ne $Agent) {
    throw "Handoff target mismatch: expected $($handoff.targetAgent), received $Agent"
}
if ([string]$handoff.headCommit -notmatch '^[0-9a-fA-F]{40}$') {
    throw 'Active handoff headCommit must be a 40-character Git commit'
}
[DateTimeOffset]::Parse([string]$handoff.updatedAt) | Out-Null

$validationSecretCandidates = @()
foreach ($entry in @($handoff.validation)) {
    Assert-Properties $entry @('command', 'workingDirectory', 'result', 'summary', 'finishedAt') 'Validation entry'
    if ($entry.result -notin @('passed', 'failed', 'skipped')) {
        throw "Unsupported validation result: $($entry.result)"
    }
    [DateTimeOffset]::Parse([string]$entry.finishedAt) | Out-Null
    $validationSecretCandidates += @($entry.command, $entry.workingDirectory, $entry.summary)
}
Assert-NoSecretShape (@(
    $handoff.taskId, $handoff.objective, $handoff.nextAction, $handoff.baseBranch
) + @($handoff.completed) + @($handoff.remaining) + @($handoff.blockers) + $validationSecretCandidates)

$currentBranch = Invoke-Git $repoRoot @('branch', '--show-current')
if ($currentBranch -ne $handoff.branch) {
    throw "Branch mismatch: handoff expects '$($handoff.branch)', current branch is '$currentBranch'"
}

$originResult = Invoke-GitRaw $repoRoot @('remote', 'get-url', 'origin')
if ($originResult.ExitCode -eq 0) {
    $currentRepository = Convert-OriginToRepository $originResult.Output
    if ($currentRepository -and $handoff.repository -ne $currentRepository) {
        throw "Repository mismatch: handoff expects '$($handoff.repository)', current repository is '$currentRepository'"
    }
}

$commitResult = Invoke-GitRaw $repoRoot @('cat-file', '-e', "$($handoff.headCommit)^{commit}")
if ($commitResult.ExitCode -ne 0) {
    throw "Recorded handoff commit is not reachable: $($handoff.headCommit)"
}
$ancestorResult = Invoke-GitRaw $repoRoot @('merge-base', '--is-ancestor', $handoff.headCommit, 'HEAD')
if ($ancestorResult.ExitCode -eq 1) {
    throw "Recorded handoff commit is not reachable from HEAD: $($handoff.headCommit)"
}
if ($ancestorResult.ExitCode -ne 0) {
    throw "Unable to verify handoff commit ancestry: $($ancestorResult.Output)"
}

$lease = Read-JsonFile $leasePath 'Lease'
Assert-Properties $lease @(
    'schemaVersion', 'taskId', 'agent', 'branch', 'status',
    'acquiredAt', 'expiresAt', 'updatedAt'
) 'Lease'
if ([int]$lease.schemaVersion -ne 1) {
    throw "Unsupported lease schemaVersion: $($lease.schemaVersion)"
}
if ($lease.status -notin @('active', 'released')) {
    throw "Unsupported lease status: $($lease.status)"
}

$now = [DateTimeOffset]::UtcNow
$leaseExpiry = [DateTimeOffset]::Parse([string]$lease.expiresAt)
$foreignActiveLease = $lease.status -eq 'active' -and $lease.agent -ne $Agent -and $leaseExpiry -gt $now
if ($foreignActiveLease -and -not $Force) {
    throw "Lease is active for '$($lease.agent)' until $($leaseExpiry.ToString('o'))"
}
if ($foreignActiveLease -and $Force) {
    Write-Warning "FORCED LEASE TAKEOVER from '$($lease.agent)' before expiry $($leaseExpiry.ToString('o'))"
}
elseif ($lease.status -eq 'active' -and $lease.agent -ne $Agent -and $leaseExpiry -le $now) {
    Write-Warning "Taking over expired lease from '$($lease.agent)'"
}

$porcelainResult = Invoke-GitRaw $repoRoot @('status', '--porcelain=v1', '--untracked-files=all')
if ($porcelainResult.ExitCode -ne 0) {
    throw "Unable to inspect worktree: $($porcelainResult.Output)"
}
$dirtyLines = @($porcelainResult.Output -split [Environment]::NewLine | Where-Object { $_ })
if ($dirtyLines.Count -gt 0) {
    Write-Warning 'WORKTREE IS DIRTY; review git status and git diff before editing.'
}

$upstreamResult = Invoke-GitRaw $repoRoot @('rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{upstream}')
if ($upstreamResult.ExitCode -ne 0) {
    Write-Warning 'Current branch has no remote tracking branch.'
}

$newLease = [ordered]@{
    schemaVersion = 1
    taskId = [string]$handoff.taskId
    agent = $Agent
    branch = $currentBranch
    status = 'active'
    acquiredAt = $now.ToString('o')
    expiresAt = $now.AddMinutes($LeaseMinutes).ToString('o')
    updatedAt = $now.ToString('o')
}
Write-JsonAtomically $newLease $leasePath

Write-Host '=== Cross-Agent Continuation Brief ==='
Write-Host "Task: $($handoff.taskId)"
Write-Host "Objective: $($handoff.objective)"
Write-Host "Source agent: $($handoff.sourceAgent)"
Write-Host "Incoming agent: $Agent"
Write-Host "Repository: $($handoff.repository)"
Write-Host "Branch: $currentBranch"
Write-Host "Checkpoint commit: $($handoff.headCommit)"
Write-Host "Checkpoint status: $($handoff.status)"
Write-Host ''
Write-Host 'Completed:'
@($handoff.completed) | ForEach-Object { Write-Host "  - $_" }
Write-Host 'Remaining:'
@($handoff.remaining) | ForEach-Object { Write-Host "  - $_" }
Write-Host 'Blockers:'
if (@($handoff.blockers).Count -eq 0) {
    Write-Host '  - none'
}
else {
    @($handoff.blockers) | ForEach-Object { Write-Host "  - $_" }
}
Write-Host 'Validation:'
if (@($handoff.validation).Count -eq 0) {
    Write-Host '  - none recorded'
}
else {
    @($handoff.validation) | ForEach-Object {
        Write-Host "  - [$($_.result)] $($_.workingDirectory): $($_.command) -- $($_.summary)"
    }
}
Write-Host 'Changed files recorded at checkpoint:'
if (@($handoff.changedFiles).Count -eq 0) {
    Write-Host '  - none'
}
else {
    @($handoff.changedFiles) | ForEach-Object { Write-Host "  - $_" }
}
Write-Host ''
Write-Host "Next action: $($handoff.nextAction)"
Write-Host "Lease expires: $($newLease.expiresAt)"
