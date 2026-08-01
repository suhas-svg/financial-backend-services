param(
    [Parameter(Mandatory = $true)][ValidateSet("Install", "Remove")][string]$Action,
    [Parameter(Mandatory = $true)]
    [ValidateSet("INSTALL SYNTHETIC SOAK TASK", "REMOVE SYNTHETIC SOAK TASK")]
    [string]$Confirmation,
    [string]$EnvironmentFile,
    [string]$EvidenceDirectory,
    [ValidateRange(1, 60)][int]$IntervalMinutes = 5,
    [string]$TaskName = "FinancialSyntheticBeta-Phase4-Soak"
)

$ErrorActionPreference = "Stop"
if ($Action -eq "Remove") {
    if ($Confirmation -ne "REMOVE SYNTHETIC SOAK TASK") {
        throw "Removal requires the exact removal confirmation"
    }
    $existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($null -ne $existing) {
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
    }
    Write-Output "Scheduled task removed. Existing soak evidence was preserved."
    return
}

if ($Confirmation -ne "INSTALL SYNTHETIC SOAK TASK") {
    throw "Installation requires the exact installation confirmation"
}
if ([string]::IsNullOrWhiteSpace($EnvironmentFile) -or
    [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    throw "EnvironmentFile and EvidenceDirectory are required for installation"
}

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$wrapper = (Resolve-Path (Join-Path $PSScriptRoot "invoke-synthetic-soak-check.ps1")).Path
$runner = (Resolve-Path (Join-Path $PSScriptRoot "run-synthetic-soak.ps1")).Path
$compose = (Resolve-Path (Join-Path $PSScriptRoot "..\docker-compose.synthetic-sandbox.yml")).Path
$environmentPath = (Resolve-Path $EnvironmentFile).Path
$evidencePath = [System.IO.Path]::GetFullPath($EvidenceDirectory)
$rootPrefix = $root.TrimEnd('\') + '\'
if ($environmentPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The synthetic environment file must remain outside the repository"
}
if ($evidencePath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Soak evidence must be written outside the repository"
}
New-Item -ItemType Directory -Force -Path $evidencePath | Out-Null

$arguments = @(
    "-NoProfile",
    "-NonInteractive",
    "-ExecutionPolicy Bypass",
    "-File `"$wrapper`"",
    "-EnvironmentFile `"$environmentPath`"",
    "-EvidenceDirectory `"$evidencePath`"",
    "-IntervalMinutes $IntervalMinutes"
) -join " "
$taskAction = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument $arguments `
    -WorkingDirectory $root
$taskTrigger = New-ScheduledTaskTrigger `
    -Once `
    -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes $IntervalMinutes) `
    -RepetitionDuration (New-TimeSpan -Days 8)
$taskSettings = New-ScheduledTaskSettingsSet `
    -MultipleInstances IgnoreNew `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Minutes ([Math]::Max(4, $IntervalMinutes - 1))) `
    -RestartCount 2 `
    -RestartInterval (New-TimeSpan -Minutes 1)
$userId = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$taskPrincipal = New-ScheduledTaskPrincipal `
    -UserId $userId `
    -LogonType Interactive `
    -RunLevel Limited
$task = New-ScheduledTask `
    -Action $taskAction `
    -Trigger $taskTrigger `
    -Settings $taskSettings `
    -Principal $taskPrincipal
Register-ScheduledTask -TaskName $TaskName -InputObject $task -Force | Out-Null
Start-ScheduledTask -TaskName $TaskName

$receipt = [ordered]@{
    classification = "SYNTHETIC_NO_REAL_MONEY"
    taskName = $TaskName
    installedAt = [DateTimeOffset]::UtcNow.ToString("o")
    intervalMinutes = $IntervalMinutes
    durationDays = 8
    user = $userId
    wrapperSha256 = (Get-FileHash $wrapper -Algorithm SHA256).Hash.ToLowerInvariant()
    runnerSha256 = (Get-FileHash $runner -Algorithm SHA256).Hash.ToLowerInvariant()
    composeSha256 = (Get-FileHash $compose -Algorithm SHA256).Hash.ToLowerInvariant()
    environmentFileSha256 = (Get-FileHash $environmentPath -Algorithm SHA256).Hash.ToLowerInvariant()
}
$receipt | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $evidencePath "scheduler.json")
Write-Output "Scheduled soak task installed and started: $TaskName"