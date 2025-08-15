# param(
#   [Parameter(Mandatory=$true)]
#   [string]$Token
# )

# try {
#     # 1) This will throw on 4xx/5xx because of -ErrorAction Stop
#     $resp = Invoke-WebRequest `
#         -Uri           'http://localhost:8080/api/accounts' `
#         -Method        Post `
#         -Body          '{}' `
#         -ContentType   'application/json' `
#         -Headers       @{ Authorization = "Bearer $Token"; Accept="application/json" } `
#         -UseBasicParsing `
#         -ErrorAction   Stop

#     # 2) If it succeeds (2xx), show the body
#     Write-Host "✅ Success ($($resp.StatusCode))"
#     Write-Host $resp.Content
# }
# catch [System.Net.WebException] {
#     # 3) On error, pull out the raw HTTP response
#     $webResp = $_.Exception.Response
#     $status  = $webResp.StatusCode.value__
#     $stream  = $webResp.GetResponseStream()
#     $reader  = [System.IO.StreamReader]::new($stream)
#     $body    = $reader.ReadToEnd()

#     # 4) Convert the JSON into an object
#     $errorObj = $body | ConvertFrom-Json

#     # 5) Nicely print each field
#     Write-Host "`n❌ Error $status"
#     Write-Host " • error:   $($errorObj.error)"
#     Write-Host " • message: $($errorObj.message)"
#     Write-Host " • path:    $($errorObj.path)"
#     Write-Host " • status:  $($errorObj.status)`n"
# }
try {
    $resp = Invoke-WebRequest `
        -Uri  'http://localhost:8080/api/accounts' `
        -Method Post `
        -Body '{}' `
        -ContentType 'application/json' `
        -Headers @{ Authorization = "Bearer $Token"; Accept="application/json" } `
        -UseBasicParsing `
        -ErrorAction Stop

    Write-Host "`nSUCCESS ($($resp.StatusCode))`n"
    Write-Host $resp.Content
}
catch [System.Net.WebException] {
    $webResp = $_.Exception.Response
    $status  = $webResp.StatusCode.value__
    $reader  = [System.IO.StreamReader]::new($webResp.GetResponseStream())
    $body    = $reader.ReadToEnd()

    $errorObj = $body | ConvertFrom-Json

    Write-Host "`nERROR $status`n"
    Write-Host "error:   $($errorObj.error)"
    Write-Host "message: $($errorObj.message)"
    Write-Host "path:    $($errorObj.path)"
    Write-Host "status:  $($errorObj.status)`n"
}
