param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference="Stop"
if($env:SANDBOX_PROFILE -ne "synthetic-sandbox"){throw "Backup requires SANDBOX_PROFILE=synthetic-sandbox"}
$root=(Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$target=[IO.Path]::GetFullPath($OutputDirectory)
if($target -eq $root -or $target -eq [IO.Path]::GetPathRoot($target)){throw "Backup target must be a bounded subdirectory"}
New-Item -ItemType Directory -Force -Path $target | Out-Null
$compose=Join-Path $root "docker-compose.synthetic-sandbox.yml"; $project="financial-synthetic-sandbox"
$stamp=[DateTimeOffset]::UtcNow.ToString("yyyyMMddTHHmmssZ"); $bundle=Join-Path $target "synthetic-backup-$stamp"
New-Item -ItemType Directory -Path $bundle | Out-Null
foreach($spec in @(@("account-postgres","account_app","account_sandbox","account.dump"),@("transaction-postgres","transaction_app","transaction_sandbox","transaction.dump"))){
  $service,$user,$database,$name=$spec
  docker compose --project-name $project -f $compose exec -T $service pg_dump -U $user -d $database -Fc -f "/tmp/$name"
  if($LASTEXITCODE -ne 0){throw "pg_dump failed for $service"}
  $container=docker compose --project-name $project -f $compose ps -q $service
  docker cp "${container}:/tmp/$name" (Join-Path $bundle $name)
  docker compose --project-name $project -f $compose exec -T $service rm -f "/tmp/$name" | Out-Null
}
$files=Get-ChildItem $bundle -File | ForEach-Object{[ordered]@{name=$_.Name;bytes=$_.Length;sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}}
$receipt=[ordered]@{classification="SYNTHETIC_NO_REAL_MONEY";createdAt=[DateTimeOffset]::UtcNow.ToString("o");composeProject=$project;files=$files}
$receipt|ConvertTo-Json -Depth 5|Set-Content -Encoding utf8 (Join-Path $bundle "backup-receipt.json")
Write-Output $bundle
