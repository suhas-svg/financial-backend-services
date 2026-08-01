param(
 [Parameter(Mandatory=$true)][string]$BackupDirectory,
 [Parameter(Mandatory=$true)][ValidateSet("RESTORE TO ISOLATED SYNTHETIC TARGET")][string]$Confirmation,
 [string]$EvidenceDirectory=(Join-Path $PSScriptRoot "..\artifacts\restore-drills")
)
$ErrorActionPreference="Stop"
$source=(Resolve-Path -LiteralPath $BackupDirectory).Path
$receipt=Get-Content -Raw (Join-Path $source "backup-receipt.json")|ConvertFrom-Json
if($receipt.classification -ne "SYNTHETIC_NO_REAL_MONEY"){throw "Backup is not classified synthetic"}
foreach($file in $receipt.files){$actual=(Get-FileHash (Join-Path $source $file.name) -Algorithm SHA256).Hash.ToLowerInvariant();if($actual -ne $file.sha256){throw "Backup hash mismatch: $($file.name)"}}
$run=[guid]::NewGuid().ToString("N").Substring(0,12);$password=[guid]::NewGuid().ToString("N")
$containers=@();$results=@()
try{
 foreach($spec in @(@("account","account_sandbox","account.dump"),@("transaction","transaction_sandbox","transaction.dump"))){
  $label,$database,$dump=$spec;$name="phase4-restore-$label-$run"
  docker run -d --name $name --network none -e "POSTGRES_PASSWORD=$password" -e "POSTGRES_DB=$database" postgres:15.13-alpine3.21 | Out-Null
  if($LASTEXITCODE -ne 0){throw "Unable to create isolated restore target"};$containers+=$name
  $consecutiveReady=0
  for($i=0;$i-lt 60;$i++){
   $previousPreference=$ErrorActionPreference;$ErrorActionPreference="Continue"
   docker exec $name psql -U postgres -d $database -Atc "select 1;" 2>$null|Out-Null
   $probeExit=$LASTEXITCODE;$ErrorActionPreference=$previousPreference
   if($probeExit -eq 0){$consecutiveReady++}else{$consecutiveReady=0}
   if($consecutiveReady -ge 3){break}
   Start-Sleep 1
  }
  if($consecutiveReady -lt 3){throw "Isolated restore target did not reach stable readiness"}
  docker cp (Join-Path $source $dump) "${name}:/tmp/$dump"
  docker exec $name pg_restore -U postgres -d $database --clean --if-exists --no-owner --no-privileges "/tmp/$dump"
  if($LASTEXITCODE -ne 0){throw "Restore failed for $label"}
  $tables=(docker exec $name psql -U postgres -d $database -Atc "select count(*) from information_schema.tables where table_schema='public';").Trim()
  $flyway=(docker exec $name psql -U postgres -d $database -Atc 'select count(*) from flyway_schema_history where success=true;').Trim()
  if([int]$tables -lt 1 -or [int]$flyway -lt 1){throw "Integrity verification failed for $label"}
  $results+=[ordered]@{database=$label;tables=[int]$tables;successfulMigrations=[int]$flyway}
 }
 New-Item -ItemType Directory -Force -Path $EvidenceDirectory|Out-Null
 $out=Join-Path $EvidenceDirectory "restore-$run.json"
 [ordered]@{classification="SYNTHETIC_NO_REAL_MONEY";isolated=$true;sourceReceiptSha256=(Get-FileHash (Join-Path $source "backup-receipt.json") -Algorithm SHA256).Hash.ToLowerInvariant();completedAt=[DateTimeOffset]::UtcNow.ToString("o");results=$results}|ConvertTo-Json -Depth 5|Set-Content -Encoding utf8 $out
 Write-Output $out
}finally{foreach($name in $containers){docker rm -f $name|Out-Null}}
