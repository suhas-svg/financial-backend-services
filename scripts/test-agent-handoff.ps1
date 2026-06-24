[CmdletBinding()]
param(
    [switch]$CheckpointOnly,
    [switch]$ResumeOnly
)

$ErrorActionPreference = 'Stop'
$script:Passed = 0
$script:Failed = 0
$sourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$createdDirectories = [System.Collections.Generic.List[string]]::new()

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Assertion failed: $Message"
    }
    $script:Passed++
}

function Invoke-Git {
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
    if ($exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function New-TestRepository {
    $path = Join-Path $tempRoot ("agent handoff test " + [Guid]::NewGuid().ToString('N'))
    $resolvedCandidate = [System.IO.Path]::GetFullPath($path)
    if (-not $resolvedCandidate.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe test path: $resolvedCandidate"
    }

    New-Item -ItemType Directory -Path $resolvedCandidate | Out-Null
    $createdDirectories.Add($resolvedCandidate)
    Invoke-Git $resolvedCandidate @('init', '-b', 'main') | Out-Null
    Invoke-Git $resolvedCandidate @('config', 'user.email', 'agent-handoff@example.invalid') | Out-Null
    Invoke-Git $resolvedCandidate @('config', 'user.name', 'Agent Handoff Test') | Out-Null
    Invoke-Git $resolvedCandidate @('remote', 'add', 'origin', 'https://github.com/suhas-svg/financial-backend-services.git') | Out-Null

    Copy-Item -LiteralPath (Join-Path $sourceRoot '.agent') -Destination $resolvedCandidate -Recurse
    New-Item -ItemType Directory -Path (Join-Path $resolvedCandidate 'scripts') | Out-Null
    foreach ($scriptName in @('agent-checkpoint.ps1', 'agent-resume.ps1')) {
        $candidate = Join-Path $sourceRoot "scripts\$scriptName"
        if (Test-Path -LiteralPath $candidate) {
            Copy-Item -LiteralPath $candidate -Destination (Join-Path $resolvedCandidate "scripts\$scriptName")
        }
    }
    Set-Content -LiteralPath (Join-Path $resolvedCandidate 'README.md') -Value '# Test repository' -Encoding UTF8
    Invoke-Git $resolvedCandidate @('add', '.') | Out-Null
    Invoke-Git $resolvedCandidate @('commit', '-m', 'test baseline') | Out-Null
    Invoke-Git $resolvedCandidate @('switch', '-c', 'codex/handoff-test') | Out-Null
    return $resolvedCandidate
}

function Invoke-AgentScript {
    param([string]$Repository, [string]$ScriptName, [string[]]$Arguments)
    $scriptPath = Join-Path $Repository "scripts\$ScriptName"
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        Push-Location -LiteralPath $Repository
        $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output -join [Environment]::NewLine)
    }
}

function Get-CheckpointArguments {
    param(
        [string]$Objective = 'Implement test handoff',
        [string]$NextAction = 'Continue the test task'
    )
    return @(
        '-SourceAgent', 'codex',
        '-TargetAgent', 'antigravity',
        '-TaskId', 'handoff-test',
        '-Objective', $Objective,
        '-Completed', 'Created baseline',
        '-Remaining', 'Continue implementation',
        '-NextAction', $NextAction
    )
}

function Test-CleanCheckpoint {
    $repo = New-TestRepository
    $result = Invoke-AgentScript $repo 'agent-checkpoint.ps1' (Get-CheckpointArguments)
    Assert-True ($result.ExitCode -eq 0) "Clean checkpoint failed: $($result.Output)"
    $handoff = Get-Content (Join-Path $repo '.agent\active-handoff.json') -Raw | ConvertFrom-Json
    Assert-True ($handoff.status -eq 'ready_for_handoff') "Clean checkpoint was not ready_for_handoff: status=$($handoff.status), changed=$(@($handoff.changedFiles) -join ', '), output=$($result.Output)"
    Assert-True ($handoff.branch -eq 'codex/handoff-test') 'Checkpoint branch was incorrect'
    Assert-True ($handoff.headCommit -eq (Invoke-Git $repo @('rev-parse', 'HEAD'))) 'Checkpoint HEAD was incorrect'
    Assert-True ($handoff.repository -eq 'suhas-svg/financial-backend-services') 'Repository was not normalized'
    Assert-True (@($handoff.changedFiles).Count -eq 0) 'Clean checkpoint reported changed files'
    Assert-True (-not (Get-ChildItem (Join-Path $repo '.agent') -Filter '*.tmp.*')) 'Atomic temporary file was left behind'
}

function Test-DirtyCheckpoint {
    $repo = New-TestRepository
    Add-Content -LiteralPath (Join-Path $repo 'README.md') -Value 'dirty'
    $result = Invoke-AgentScript $repo 'agent-checkpoint.ps1' (Get-CheckpointArguments)
    Assert-True ($result.ExitCode -eq 0) "Dirty checkpoint failed: $($result.Output)"
    $handoff = Get-Content (Join-Path $repo '.agent\active-handoff.json') -Raw | ConvertFrom-Json
    Assert-True ($handoff.status -eq 'local_only') 'Dirty checkpoint was not local_only'
    Assert-True (@($handoff.changedFiles) -contains 'README.md') 'Dirty file was not recorded'
}

function Test-RequiredObjective {
    $repo = New-TestRepository
    $result = Invoke-AgentScript $repo 'agent-checkpoint.ps1' (Get-CheckpointArguments -Objective ' ')
    Assert-True ($result.ExitCode -ne 0) 'Blank objective was accepted'
    Assert-True ($result.Output -match 'Objective is required') 'Blank objective error was unclear'
}

function Test-SecretRejection {
    $repo = New-TestRepository
    $result = Invoke-AgentScript $repo 'agent-checkpoint.ps1' (Get-CheckpointArguments -NextAction 'token=ghp_abcdefghijklmnopqrstuvwxyz123456')
    Assert-True ($result.ExitCode -ne 0) 'Secret-shaped value was accepted'
    Assert-True ($result.Output -match 'secret-shaped value') 'Secret rejection error was unclear'
}

function Test-LeaseRelease {
    $repo = New-TestRepository
    $leasePath = Join-Path $repo '.agent\lease.json'
    @{
        schemaVersion = 1
        taskId = 'handoff-test'
        agent = 'codex'
        branch = 'codex/handoff-test'
        status = 'active'
        acquiredAt = [DateTimeOffset]::UtcNow.AddMinutes(-5).ToString('o')
        expiresAt = [DateTimeOffset]::UtcNow.AddMinutes(115).ToString('o')
        updatedAt = [DateTimeOffset]::UtcNow.AddMinutes(-5).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath $leasePath -Encoding UTF8
    $result = Invoke-AgentScript $repo 'agent-checkpoint.ps1' (Get-CheckpointArguments)
    Assert-True ($result.ExitCode -eq 0) "Checkpoint with lease failed: $($result.Output)"
    $lease = Get-Content $leasePath -Raw | ConvertFrom-Json
    Assert-True ($lease.status -eq 'released') 'Checkpoint did not release the lease'
    Assert-True ($lease.agent -eq 'codex') 'Released lease lost outgoing agent identity'
}

function Invoke-CheckpointTests {
    $checkpointScript = Join-Path $sourceRoot 'scripts\agent-checkpoint.ps1'
    if (-not (Test-Path -LiteralPath $checkpointScript)) {
        throw 'agent-checkpoint.ps1 does not exist'
    }
    Test-CleanCheckpoint
    Test-DirtyCheckpoint
    Test-RequiredObjective
    Test-SecretRejection
    Test-LeaseRelease
}

function New-CheckpointedRepository {
    $repo = New-TestRepository
    $checkpoint = Invoke-AgentScript $repo 'agent-checkpoint.ps1' (Get-CheckpointArguments)
    Assert-True ($checkpoint.ExitCode -eq 0) "Checkpoint setup failed: $($checkpoint.Output)"
    Invoke-Git $repo @('add', '.agent') | Out-Null
    Invoke-Git $repo @('commit', '-m', 'record handoff') | Out-Null
    return $repo
}

function Write-TestJson {
    param([string]$Path, [object]$Value)
    $Value | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Test-ValidResume {
    $repo = New-CheckpointedRepository
    $result = Invoke-AgentScript $repo 'agent-resume.ps1' @('-Agent', 'antigravity')
    Assert-True ($result.ExitCode -eq 0) "Valid resume failed: $($result.Output)"
    foreach ($expected in @(
        'Implement test handoff',
        'Created baseline',
        'Continue implementation',
        'Continue the test task'
    )) {
        Assert-True ($result.Output -match [regex]::Escape($expected)) "Resume output omitted: $expected"
    }
    $lease = Get-Content (Join-Path $repo '.agent\lease.json') -Raw | ConvertFrom-Json
    Assert-True ($lease.status -eq 'active') 'Resume did not activate the lease'
    Assert-True ($lease.agent -eq 'antigravity') 'Resume lease has the wrong agent'
    $duration = [DateTimeOffset]::Parse($lease.expiresAt) - [DateTimeOffset]::Parse($lease.acquiredAt)
    Assert-True ([Math]::Abs($duration.TotalMinutes - 120) -lt 0.1) 'Resume lease was not two hours'
}

function Test-WrongBranchRefusal {
    $repo = New-CheckpointedRepository
    Invoke-Git $repo @('switch', '-c', 'codex/wrong-branch') | Out-Null
    $result = Invoke-AgentScript $repo 'agent-resume.ps1' @('-Agent', 'antigravity')
    Assert-True ($result.ExitCode -ne 0) 'Resume accepted the wrong branch'
    Assert-True ($result.Output -match 'Branch mismatch') 'Wrong branch error was unclear'
}

function Test-UnreachableCommitRefusal {
    $repo = New-CheckpointedRepository
    $handoffPath = Join-Path $repo '.agent\active-handoff.json'
    $handoff = Get-Content $handoffPath -Raw | ConvertFrom-Json
    $handoff.headCommit = 'ffffffffffffffffffffffffffffffffffffffff'
    Write-TestJson $handoffPath $handoff
    $result = Invoke-AgentScript $repo 'agent-resume.ps1' @('-Agent', 'antigravity')
    Assert-True ($result.ExitCode -ne 0) 'Resume accepted an unreachable commit'
    Assert-True ($result.Output -match 'not reachable') 'Unreachable commit error was unclear'
}

function Set-TestLease {
    param(
        [string]$Repository,
        [string]$Agent,
        [DateTimeOffset]$ExpiresAt
    )
    $now = [DateTimeOffset]::UtcNow
    Write-TestJson (Join-Path $Repository '.agent\lease.json') ([ordered]@{
        schemaVersion = 1
        taskId = 'handoff-test'
        agent = $Agent
        branch = 'codex/handoff-test'
        status = 'active'
        acquiredAt = $now.AddMinutes(-5).ToString('o')
        expiresAt = $ExpiresAt.ToString('o')
        updatedAt = $now.AddMinutes(-5).ToString('o')
    })
}

function Test-ActiveLeaseRefusalAndForce {
    $repo = New-CheckpointedRepository
    Set-TestLease $repo 'codex' ([DateTimeOffset]::UtcNow.AddMinutes(60))
    $blocked = Invoke-AgentScript $repo 'agent-resume.ps1' @('-Agent', 'antigravity')
    Assert-True ($blocked.ExitCode -ne 0) 'Resume ignored an active foreign lease'
    Assert-True ($blocked.Output -match 'Lease is active') 'Active lease error was unclear'

    $forced = Invoke-AgentScript $repo 'agent-resume.ps1' @('-Agent', 'antigravity', '-Force')
    Assert-True ($forced.ExitCode -eq 0) "Forced takeover failed: $($forced.Output)"
    Assert-True ($forced.Output -match 'FORCED LEASE TAKEOVER') 'Forced takeover warning was missing'
    $lease = Get-Content (Join-Path $repo '.agent\lease.json') -Raw | ConvertFrom-Json
    Assert-True ($lease.agent -eq 'antigravity') 'Forced takeover did not change lease owner'
}

function Test-ExpiredLeaseTakeover {
    $repo = New-CheckpointedRepository
    Set-TestLease $repo 'codex' ([DateTimeOffset]::UtcNow.AddMinutes(-1))
    $result = Invoke-AgentScript $repo 'agent-resume.ps1' @('-Agent', 'antigravity')
    Assert-True ($result.ExitCode -eq 0) "Expired lease takeover failed: $($result.Output)"
    $lease = Get-Content (Join-Path $repo '.agent\lease.json') -Raw | ConvertFrom-Json
    Assert-True ($lease.agent -eq 'antigravity') 'Expired lease takeover has wrong owner'
}

function Test-UnsupportedSchemaRefusal {
    $repo = New-CheckpointedRepository
    $handoffPath = Join-Path $repo '.agent\active-handoff.json'
    $handoff = Get-Content $handoffPath -Raw | ConvertFrom-Json
    $handoff.schemaVersion = 2
    Write-TestJson $handoffPath $handoff
    $result = Invoke-AgentScript $repo 'agent-resume.ps1' @('-Agent', 'antigravity')
    Assert-True ($result.ExitCode -ne 0) 'Resume accepted an unsupported schema'
    Assert-True ($result.Output -match 'Unsupported schemaVersion') 'Schema error was unclear'
}

function Test-DirtyWorktreeWarning {
    $repo = New-CheckpointedRepository
    Add-Content -LiteralPath (Join-Path $repo 'README.md') -Value 'preserve this dirty change'
    $before = Get-Content -LiteralPath (Join-Path $repo 'README.md') -Raw
    $result = Invoke-AgentScript $repo 'agent-resume.ps1' @('-Agent', 'antigravity')
    Assert-True ($result.ExitCode -eq 0) "Dirty worktree resume failed: $($result.Output)"
    Assert-True ($result.Output -match 'WORKTREE IS DIRTY') 'Dirty worktree warning was missing'
    $after = Get-Content -LiteralPath (Join-Path $repo 'README.md') -Raw
    Assert-True ($after -eq $before) 'Resume modified a dirty application file'
}

function Invoke-ResumeTests {
    $resumeScript = Join-Path $sourceRoot 'scripts\agent-resume.ps1'
    if (-not (Test-Path -LiteralPath $resumeScript)) {
        throw 'agent-resume.ps1 does not exist'
    }
    Test-ValidResume
    Test-WrongBranchRefusal
    Test-UnreachableCommitRefusal
    Test-ActiveLeaseRefusalAndForce
    Test-ExpiredLeaseTakeover
    Test-UnsupportedSchemaRefusal
    Test-DirtyWorktreeWarning
}

try {
    if (-not $ResumeOnly) {
        Invoke-CheckpointTests
    }
    if (-not $CheckpointOnly) {
        Invoke-ResumeTests
    }
    Write-Host "Agent handoff tests passed: $script:Passed assertions, $script:Failed failures"
}
catch {
    $script:Failed++
    Write-Error $_
    exit 1
}
finally {
    foreach ($directory in $createdDirectories) {
        $resolved = [System.IO.Path]::GetFullPath($directory)
        if (-not $resolved.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing unsafe cleanup: $resolved"
        }
        if (Test-Path -LiteralPath $resolved) {
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}
