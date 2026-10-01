param(
 [Parameter(Mandatory=$true)][string]$BackupDirectory,
 [Parameter(Mandatory=$true)][ValidateSet("RESTORE TO ISOLATED SYNTHETIC TARGET")][string]$Confirmation,
 [string]$EvidenceDirectory=(Join-Path $PSScriptRoot "..\artifacts\restore-drills"),
 # Recovery objectives the drill is measured against (see docs/operations/load-soak-restore-drills.md).
 [ValidateRange(1,86400)][int]$RtoTargetSeconds=900,
 [ValidateRange(1,86400)][int]$RpoTargetSeconds=900
)
$ErrorActionPreference="Stop"
. (Join-Path $PSScriptRoot "lib/SyntheticDrillSnapshot.ps1")
$source=(Resolve-Path -LiteralPath $BackupDirectory).Path
$receipt=Get-Content -Raw (Join-Path $source "backup-receipt.json")|ConvertFrom-Json
if($receipt.classification -ne "SYNTHETIC_NO_REAL_MONEY"){throw "Backup is not classified synthetic"}
foreach($file in $receipt.files){$actual=(Get-FileHash (Join-Path $source $file.name) -Algorithm SHA256).Hash.ToLowerInvariant();if($actual -ne $file.sha256){throw "Backup hash mismatch: $($file.name)"}}
$run=[guid]::NewGuid().ToString("N").Substring(0,12);$password=[guid]::NewGuid().ToString("N")
$containers=@();$results=@();$failures=@()
# The clock starts when recovery starts: a fresh database server, the restore, and verification.
$restoreStartedAt=[DateTimeOffset]::UtcNow
$timer=[Diagnostics.Stopwatch]::StartNew()
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
  $query={param($sql) $value=docker exec $name psql -U postgres -d $database -Atc $sql; if($LASTEXITCODE -ne 0){throw "Verification query failed for $label"}; $value}
  $tables=[int]([string](& $query "select count(*) from information_schema.tables where table_schema='public';")).Trim()
  $restored=Get-SyntheticDrillSnapshot $label $query
  if($tables -lt 1 -or [int]$restored.flywaySuccessfulMigrations -lt 1){throw "Integrity verification failed for $label"}
  # Data-loss check: the restored database must match what the backup recorded, value for value.
  $mismatches=@()
  $expected=$receipt.snapshots.$label
  if($null -eq $expected){$mismatches+="backup receipt has no snapshot for $label (taken before snapshots were recorded)"}
  else{foreach($key in $restored.Keys){
   # ConvertFrom-Json turns ISO timestamps in the receipt into DateTime; compare in the
   # format the snapshot query produced, not in the current culture's date format.
   $want=$expected.$key
   if($want -is [datetime]){$want=$want.ToString("yyyy-MM-dd'T'HH:mm:ss.ffffff",[Globalization.CultureInfo]::InvariantCulture)}
   if([string]$want -ne [string]$restored[$key]){$mismatches+="$key expected $want restored $($restored[$key])"}
  }}
  $unbalanced=$null
  if($label -eq "transaction"){
   $unbalanced=[int]([string](& $query $SyntheticDrillUnbalancedJournalsQuery)).Trim()
   if($unbalanced -ne 0){$mismatches+="unbalancedJournals=$unbalanced"}
  }
  if($mismatches.Count -gt 0){$failures+="${label}: $($mismatches -join '; ')"}
  $results+=[ordered]@{database=$label;tables=$tables;successfulMigrations=[int]$restored.flywaySuccessfulMigrations;restored=$restored;unbalancedJournals=$unbalanced;matchesBackup=($mismatches.Count -eq 0);mismatches=$mismatches}
 }
 $timer.Stop()
 $rtoSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,1)
 # RPO: what a failure at the moment recovery started would lose with this backup, i.e. the
 # time since the newest committed transaction it contains.
 $latest=$results|Where-Object{$_.database -eq "transaction"}|ForEach-Object{$_.restored.latestTransactionAt}
 $rpoSeconds=$null
 if($latest){$rpoSeconds=[Math]::Round(($restoreStartedAt-[DateTimeOffset]::Parse("${latest}Z")).TotalSeconds,1)}
 if($rtoSeconds -gt $RtoTargetSeconds){$failures+="RTO ${rtoSeconds}s exceeds target ${RtoTargetSeconds}s"}
 if($null -eq $rpoSeconds){$failures+="RPO not measurable: the backup contains no transactions"}
 elseif($rpoSeconds -gt $RpoTargetSeconds){$failures+="RPO ${rpoSeconds}s exceeds target ${RpoTargetSeconds}s"}
 New-Item -ItemType Directory -Force -Path $EvidenceDirectory|Out-Null
 $out=Join-Path $EvidenceDirectory "restore-$run.json"
 [ordered]@{
  classification="SYNTHETIC_NO_REAL_MONEY";isolated=$true
  sourceReceiptSha256=(Get-FileHash (Join-Path $source "backup-receipt.json") -Algorithm SHA256).Hash.ToLowerInvariant()
  backupCreatedAt=$receipt.createdAt;restoreStartedAt=$restoreStartedAt.ToString("o");completedAt=[DateTimeOffset]::UtcNow.ToString("o")
  objectives=[ordered]@{rtoTargetSeconds=$RtoTargetSeconds;rtoSeconds=$rtoSeconds;rpoTargetSeconds=$RpoTargetSeconds;rpoSeconds=$rpoSeconds}
  passed=($failures.Count -eq 0);failures=$failures;results=$results
 }|ConvertTo-Json -Depth 6|Set-Content -Encoding utf8 $out
 Write-Output $out
 if($failures.Count -gt 0){throw "Restore drill failed: $($failures -join ' | ')"}
}finally{foreach($name in $containers){docker rm -f $name|Out-Null}}
