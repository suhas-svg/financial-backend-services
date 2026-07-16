[CmdletBinding()]
param(
    [ValidateSet('Preflight', 'Run')]
    [string]$Action = 'Preflight',
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$BusinessDate = (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd'),
    [switch]$MaintenanceConfirmed,
    [string]$RequestId = "ledger-bootstrap-$([guid]::NewGuid())"
)

$ErrorActionPreference = 'Stop'

if (-not $MaintenanceConfirmed) {
    throw 'MaintenanceConfirmed is required. This script never weakens the maintenance gate.'
}
if ($Action -eq 'Run' -and $env:LEDGER_BOOTSTRAP_CONFIRM_RUN -ne 'RUN') {
    throw 'Set LEDGER_BOOTSTRAP_CONFIRM_RUN=RUN for the explicit write step. Preflight remains read-only.'
}
if ([string]::IsNullOrWhiteSpace($env:LEDGER_BOOTSTRAP_OPERATOR_TOKEN)) {
    throw 'LEDGER_BOOTSTRAP_OPERATOR_TOKEN is required and must contain a short-lived ROLE_ADMIN JWT.'
}
if ([string]::IsNullOrWhiteSpace($RequestId) -or $RequestId.Length -gt 128) {
    throw 'RequestId must contain 1 to 128 characters.'
}

$token = $env:LEDGER_BOOTSTRAP_OPERATOR_TOKEN
$headers = @{
    Authorization = "Bearer $token"
    'X-Operator-Request-Id' = $RequestId
}
$preflightUri = "$($BaseUrl.TrimEnd('/'))/api/admin/ledger/bootstrap/preflight?maintenanceMode=true"
$preflight = Invoke-RestMethod -Method Get -Uri $preflightUri -Headers $headers

if (-not $preflight.operatorAuthorized -or $preflight.operatorRole -ne 'ROLE_ADMIN') {
    throw 'Server did not confirm explicit ROLE_ADMIN operator authorization.'
}
if ($preflight.requestId -ne $RequestId) {
    throw 'Server preflight request evidence does not match this deployment request.'
}
if (-not $preflight.ready) {
    $reasons = @($preflight.blockers) -join '; '
    throw "Ledger bootstrap preflight failed closed: $reasons"
}

[pscustomobject]@{
    action = 'Preflight'
    requestId = $RequestId
    operator = $preflight.operatorId
    operatorRole = $preflight.operatorRole
    ready = $preflight.ready
    freshDatabase = $preflight.freshDatabase
    missingSystemAccounts = @($preflight.missingSystemAccounts)
    requiredCurrencies = @($preflight.requiredCurrencies)
} | ConvertTo-Json -Depth 5

if ($Action -eq 'Preflight') { return }

$body = @{
    enabled = $true
    maintenanceMode = $true
    businessDate = $BusinessDate
} | ConvertTo-Json
$result = Invoke-RestMethod -Method Post -Uri "$($BaseUrl.TrimEnd('/'))/api/admin/ledger/bootstrap" `
    -Headers $headers -ContentType 'application/json' -Body $body

[pscustomobject]@{
    action = 'Run'
    requestId = $RequestId
    runId = $result.runId
    importedAccounts = $result.importedAccounts
    reusedAccounts = $result.reusedAccounts
    seededSystemAccounts = $result.seededSystemAccounts
    openingJournals = $result.openingJournals
    currencies = @($result.currencies)
    nextRequiredGate = 'Run reconciliation and require zero unexplained critical exceptions before ledger authority.'
} | ConvertTo-Json -Depth 5
