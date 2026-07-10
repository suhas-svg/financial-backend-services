param(
    [string]$AccountServiceUrl = "http://127.0.0.1:18080",
    [string]$TransactionServiceUrl = "http://127.0.0.1:18081"
)

$ErrorActionPreference = "Stop"

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body,
        [string]$Token,
        [hashtable]$ExtraHeaders = @{}
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    foreach ($entry in $ExtraHeaders.GetEnumerator()) { $headers[$entry.Key] = $entry.Value }
    $parameters = @{ Method = $Method; Uri = $Uri; Headers = $headers }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    Invoke-RestMethod @parameters
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Get-TotpCode([string]$Secret) {
    $alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    $buffer = 0
    $bits = 0
    $bytes = [System.Collections.Generic.List[byte]]::new()
    foreach ($character in $Secret.TrimEnd("=").ToUpperInvariant().ToCharArray()) {
        $index = $alphabet.IndexOf($character)
        if ($index -lt 0) { throw "Invalid Base32 secret" }
        $buffer = ($buffer -shl 5) -bor $index
        $bits += 5
        while ($bits -ge 8) {
            $bits -= 8
            $bytes.Add([byte](($buffer -shr $bits) -band 0xff))
        }
    }
    $counter = [long][Math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() / 30)
    $counterBytes = [BitConverter]::GetBytes($counter)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($counterBytes) }
    $hmac = [System.Security.Cryptography.HMACSHA1]::new($bytes.ToArray())
    try { $hash = $hmac.ComputeHash($counterBytes) } finally { $hmac.Dispose() }
    $offset = $hash[$hash.Length - 1] -band 0x0f
    $binary = (($hash[$offset] -band 0x7f) -shl 24) -bor
              (($hash[$offset + 1] -band 0xff) -shl 16) -bor
              (($hash[$offset + 2] -band 0xff) -shl 8) -bor
              ($hash[$offset + 3] -band 0xff)
    ($binary % 1000000).ToString("D6")
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$sender = "stepup_sender_$suffix"
$recipient = "stepup_recipient_$suffix"
$password = "LiveTest-$suffix!"

Invoke-Json POST "$AccountServiceUrl/api/auth/register" @{ username = $sender; password = $password } $null | Out-Null
Invoke-Json POST "$AccountServiceUrl/api/auth/register" @{ username = $recipient; password = $password } $null | Out-Null
$senderToken = (Invoke-Json POST "$AccountServiceUrl/api/auth/login" @{ username = $sender; password = $password } $null).accessToken
$recipientToken = (Invoke-Json POST "$AccountServiceUrl/api/auth/login" @{ username = $recipient; password = $password } $null).accessToken
Assert-True (-not [string]::IsNullOrWhiteSpace($senderToken)) "Sender login did not return a token"

$source = Invoke-Json POST "$AccountServiceUrl/api/accounts" @{
    accountType = "CREDIT"; ownerId = $sender; balance = 10000; currency = "USD"
    creditLimit = 10000; dueDate = [DateTime]::UtcNow.AddYears(1).ToString("yyyy-MM-dd")
} $senderToken
$destination = Invoke-Json POST "$AccountServiceUrl/api/accounts" @{
    accountType = "CHECKING"; ownerId = $recipient; balance = 0; currency = "USD"
} $recipientToken

$enrollment = Invoke-Json POST "$AccountServiceUrl/api/security/mfa/totp/enroll" @{
    currentPassword = $password
} $senderToken
$confirmation = Invoke-Json POST "$AccountServiceUrl/api/security/mfa/totp/confirm" @{
    code = Get-TotpCode $enrollment.secret
} $senderToken
Assert-True ($confirmation.active -eq $true) "TOTP enrollment was not activated"
Assert-True ($confirmation.recoveryCodes.Count -eq 8) "Expected eight recovery codes"

$idempotencyKey = "step-up-live-$suffix"
$pending = Invoke-Json POST "$TransactionServiceUrl/api/transactions/transfer" @{
    fromAccountId = [string]$source.id
    toAccountId = [string]$destination.id
    amount = 5000
    currency = "USD"
    description = "Step-up live verification"
    reference = "STEPUP-$suffix"
} $senderToken @{ "Idempotency-Key" = $idempotencyKey }
Assert-True ($pending.authorizationRequired -eq $true) "High-value transfer did not require authorization"
Assert-True ($pending.status -eq "PENDING") "Challenged transfer was not pending"

$sourceBeforeAuthorization = Invoke-Json GET "$AccountServiceUrl/api/accounts/$($source.id)" $null $senderToken
Assert-True ([decimal]$sourceBeforeAuthorization.balance -eq 10000) "Balance changed before authorization"

$verification = Invoke-Json POST "$AccountServiceUrl/api/security/challenges/$($pending.authorizationChallengeId)/verify" @{
    credential = $confirmation.recoveryCodes[0]
} $senderToken
$completed = Invoke-Json POST "$TransactionServiceUrl/api/transactions/$($pending.transactionId)/authorize" @{
    proof = $verification.proof
} $senderToken
Assert-True ($completed.status -eq "COMPLETED") "Authorized transfer did not complete"
Assert-True (-not [string]::IsNullOrWhiteSpace($completed.journalId)) "Authorized transfer has no ledger journal"

$replayed = Invoke-Json POST "$TransactionServiceUrl/api/transactions/$($pending.transactionId)/authorize" @{
    proof = $verification.proof
} $senderToken
Assert-True ($replayed.transactionId -eq $completed.transactionId) "Authorization retry was not idempotent"

$mfaStatus = Invoke-Json GET "$AccountServiceUrl/api/security/mfa" $null $senderToken
Assert-True ($mfaStatus.recoveryCodesRemaining -eq 7) "Recovery code was not consumed exactly once"

[pscustomobject]@{
    result = "PASS"
    authorizationId = $pending.transactionId
    transactionId = $completed.transactionId
    journalId = $completed.journalId
    reasons = $pending.authorizationReasons
    recoveryCodesRemaining = $mfaStatus.recoveryCodesRemaining
} | ConvertTo-Json -Depth 4
