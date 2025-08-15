param(
  [string]$BaseUrl = 'http://localhost:8080',
  [string]$Username = 'suhas123',
  [string]$Password = 'Ambident'
)

# 1) Start your app in another window with logs redirected:
#    cd .\account-service
#    mvn spring-boot:run > .\logs.txt 2>&1
# Then in THIS window you can tail:
Start-Job { Get-Content .\logs.txt -Tail 20 -Wait } | Out-Null

# 2) Authenticate and extract token
$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
Write-Host "`n→ Logging in..."
$loginResp = Invoke-RestMethod `
  -Uri "$BaseUrl/api/auth/login" `
  -Method Post `
  -ContentType 'application/json' `
  -Body $loginBody
$Token = $loginResp.accessToken
Write-Host "→ Got token (length $($Token.Length))`n"

# 3) Create two accounts
$accounts = @(
  @{ ownerId = 'alice'; accountType = 'CHECKING'; balance = 1000 },
  @{ ownerId = 'bob';   accountType = 'SAVINGS';  balance =  500 }
)
foreach($acct in $accounts) {
  $body = $acct | ConvertTo-Json
  Write-Host "→ Creating account: $($acct.ownerId)/$($acct.accountType)..."
  Invoke-RestMethod `
    -Uri "$BaseUrl/api/accounts" `
    -Method Post `
    -Headers @{ Authorization = "Bearer $Token" } `
    -ContentType 'application/json' `
    -Body $body `
    -Verbose
}

# 4) Give the app a second to process…
Start-Sleep -Seconds 2

# 5) Scrape Prometheus and filter for your gauge
Write-Host "`n→ Prometheus metrics (account_total_count):"
Invoke-RestMethod -Uri "$BaseUrl/actuator/prometheus" |
  Select-String 'account_total_count'

# 6) (Optional) Test an error case to see your structured error response
Write-Host "`n→ Triggering BadRequest (missing balance)…"
try {
  Invoke-WebRequest `
    -Uri "$BaseUrl/api/accounts" `
    -Method Post `
    -Headers @{ Authorization = "Bearer $Token"; Accept = 'application/json' } `
    -ContentType 'application/json' `
    -Body '{}' `
    -UseBasicParsing `
    -ErrorAction Stop
} catch [System.Net.WebException] {
  $resp   = $_.Exception.Response
  $status = $resp.StatusCode.value__
  $body   = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
  $err    = $body | ConvertFrom-Json

  Write-Host "→ Error $status"
  Write-Host "   • error:   $($err.error)"
  Write-Host "   • message: $($err.message)"
}
