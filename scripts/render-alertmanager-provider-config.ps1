param(
 [Parameter(Mandatory=$true)][string]$ProviderWebhookSecretReferenceFile,
 [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference="Stop"
$root=(Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$reference=(Resolve-Path -LiteralPath $ProviderWebhookSecretReferenceFile).Path
$output=[IO.Path]::GetFullPath($OutputFile)
if($reference.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)){throw "Provider secret reference must be supplied outside the repository"}
if($output.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)){throw "Rendered provider config must remain outside the repository"}
$url=(Get-Content -Raw -LiteralPath $reference).Trim()
$uri=$null
if(-not [Uri]::TryCreate($url,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -ne 'https'){throw "Provider webhook reference must resolve to HTTPS"}
if($uri.IsLoopback -or $uri.Host -match 'example|placeholder|company\.com'){throw "Placeholder or local provider webhook rejected"}
$parent=Split-Path -Parent $output;if(-not(Test-Path $parent)){New-Item -ItemType Directory -Path $parent|Out-Null}
("global:","  resolve_timeout: 5m","route:","  receiver: external-provider","  group_by: [alertname, service, severity]","receivers:","  - name: external-provider","    webhook_configs:","      - url: $url","        send_resolved: true") | Set-Content -Encoding utf8 -LiteralPath $output
Write-Output "Rendered provider-neutral Alertmanager config to the approved external path; secret value was not printed."
