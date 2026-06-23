[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('codex', 'antigravity')][string]$SourceAgent,
    [Parameter(Mandatory)][ValidateSet('codex', 'antigravity')][string]$TargetAgent,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$TaskId,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Objective,
    [string[]]$Completed = @(),
    [string[]]$Remaining = @(),
    [string[]]$Blockers = @(),
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$NextAction,
    [string]$BaseBranch = 'main',
    [string]$ValidationJson = '[]'
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([string]$WorkingDirectory, [string[]]$Arguments)
    $output = & git -C $WorkingDirectory @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Write-JsonAtomically {
    param([Parameter(Mandatory)][object]$Value, [Parameter(Mandatory)][string]$Path)
    $directory = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }
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

function Assert-RequiredText {
    param([string]$Value, [string]$Name)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name is required"
    }
}

function Assert-NoSecretShape {
    param([string[]]$Values)
    $secretPattern = '(?i)(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|(?:password|token|secret|api[_-]?key)\s*[:=]\s*\S+)'
    foreach ($value in $Values) {
        if ($null -ne $value -and $value -match $secretPattern) {
            throw 'Refusing to store a secret-shaped value in the handoff'
        }
    }
}

function Convert-OriginToRepository {
    param([string]$Origin)
    if ($Origin -match '^(?:https?://github\.com/|git@github\.com:)([^/]+/[^/]+?)(?:\.git)?$') {
        return $Matches[1]
    }
    return $null
}

Assert-RequiredText $TaskId 'TaskId'
Assert-RequiredText $Objective 'Objective'
Assert-RequiredText $NextAction 'NextAction'
Assert-RequiredText $BaseBranch 'BaseBranch'
if ($SourceAgent -eq $TargetAgent) {
    throw 'SourceAgent and TargetAgent must be different'
}

$validation = @()
try {
    $parsedValidation = $ValidationJson | ConvertFrom-Json
    if ($null -ne $parsedValidation) {
        $validation = @($parsedValidation)
    }
}
catch {
    throw "ValidationJson is invalid JSON: $($_.Exception.Message)"
}

foreach ($entry in $validation) {
    foreach ($field in @('command', 'workingDirectory', 'result', 'summary', 'finishedAt')) {
        if ($entry.PSObject.Properties.Name -notcontains $field) {
            throw "Validation entry is missing $field"
        }
    }
    if ($entry.result -notin @('passed', 'failed', 'skipped')) {
        throw 'Validation result must be passed, failed, or skipped'
    }
    [DateTimeOffset]::Parse([string]$entry.finishedAt) | Out-Null
}

$secretCandidates = @($TaskId, $Objective, $NextAction, $BaseBranch) +
    @($Completed) + @($Remaining) + @($Blockers) + @($ValidationJson)
Assert-NoSecretShape $secretCandidates

$currentDirectory = (Get-Location).Path
$repoRoot = Invoke-Git $currentDirectory @('rev-parse', '--show-toplevel')
$branch = Invoke-Git $repoRoot @('branch', '--show-current')
Assert-RequiredText $branch 'Current Git branch'
$headCommit = Invoke-Git $repoRoot @('rev-parse', 'HEAD')

$originOutput = & git -C $repoRoot remote get-url origin 2>&1
$originExit = $LASTEXITCODE
$repository = $null
$remoteMissing = $originExit -ne 0
if (-not $remoteMissing) {
    $repository = Convert-OriginToRepository (($originOutput -join [Environment]::NewLine).Trim())
}
if ([string]::IsNullOrWhiteSpace($repository)) {
    $repository = 'local/' + (Split-Path -Leaf $repoRoot)
    $remoteMissing = $true
}

$porcelain = & git -C $repoRoot status --porcelain=v1 --untracked-files=all 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "git status failed: $($porcelain -join [Environment]::NewLine)"
}
$changedFiles = @(
    foreach ($line in $porcelain) {
        if ($line.Length -ge 4) {
            $path = $line.Substring(3).Trim()
            if ($path -match ' -> (.+)$') {
                $path = $Matches[1]
            }
            $path.Trim('"').Replace('\', '/')
        }
    }
)

$now = [DateTimeOffset]::UtcNow
$status = if ($changedFiles.Count -eq 0 -and -not $remoteMissing) {
    'ready_for_handoff'
}
else {
    'local_only'
}

$handoff = [ordered]@{
    schemaVersion = 1
    taskId = $TaskId.Trim()
    objective = $Objective.Trim()
    sourceAgent = $SourceAgent
    targetAgent = $TargetAgent
    repository = $repository
    branch = $branch
    baseBranch = $BaseBranch.Trim()
    headCommit = $headCommit
    worktree = $repoRoot.Replace('\', '/')
    status = $status
    completed = @($Completed)
    remaining = @($Remaining)
    blockers = @($Blockers)
    changedFiles = @($changedFiles)
    validation = @($validation)
    nextAction = $NextAction.Trim()
    updatedAt = $now.ToString('o')
}

$agentDirectory = Join-Path $repoRoot '.agent'
$handoffPath = Join-Path $agentDirectory 'active-handoff.json'
$leasePath = Join-Path $agentDirectory 'lease.json'
Write-JsonAtomically $handoff $handoffPath

$acquiredAt = $now
if (Test-Path -LiteralPath $leasePath) {
    try {
        $existingLease = Get-Content -LiteralPath $leasePath -Raw | ConvertFrom-Json
        if ($existingLease.acquiredAt) {
            $acquiredAt = [DateTimeOffset]::Parse([string]$existingLease.acquiredAt)
        }
    }
    catch {
        throw "Existing lease is invalid JSON: $($_.Exception.Message)"
    }
}
$releasedLease = [ordered]@{
    schemaVersion = 1
    taskId = $TaskId.Trim()
    agent = $SourceAgent
    branch = $branch
    status = 'released'
    acquiredAt = $acquiredAt.ToString('o')
    expiresAt = $now.ToString('o')
    updatedAt = $now.ToString('o')
}
Write-JsonAtomically $releasedLease $leasePath

Write-Host "Checkpoint status: $status"
Write-Host "Repository: $repository"
Write-Host "Branch: $branch"
Write-Host "HEAD: $headCommit"
if ($changedFiles.Count -gt 0) {
    Write-Warning 'Worktree has uncommitted changes; this checkpoint is local_only.'
    $changedFiles | ForEach-Object { Write-Host "  changed: $_" }
}
if ($remoteMissing) {
    Write-Warning 'A supported GitHub origin was not found; this checkpoint is local_only.'
}
Write-Host ''
Write-Host 'Review the changes, then publish intentionally if appropriate:'
Write-Host '  git status --short'
Write-Host '  git add <reviewed-paths>'
Write-Host '  git commit -m "chore: checkpoint agent handoff"'
Write-Host "  git push -u origin $branch"
