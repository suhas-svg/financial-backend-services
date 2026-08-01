param(
 [Parameter(Mandatory=$true)][ValidateSet("RUN SYNTHETIC FAILURE DRILLS")][string]$Confirmation,
 [string]$EvidenceDirectory=(Join-Path $PSScriptRoot "..\artifacts\failure-drills")
)
$ErrorActionPreference="Stop"
if($env:SANDBOX_PROFILE -ne "synthetic-sandbox"){throw "Failure drills require SANDBOX_PROFILE=synthetic-sandbox"}
$root=(Resolve-Path (Join-Path $PSScriptRoot "..")).Path;$base=Join-Path $root "docker-compose.synthetic-sandbox.yml";$project="financial-synthetic-sandbox"
New-Item -ItemType Directory -Force -Path $EvidenceDirectory|Out-Null;$results=@()
function Wait-Healthy([string]$service){for($i=0;$i-lt 60;$i++){$row=docker compose --project-name $project -f $base ps --format json $service|ConvertFrom-Json;if($row.State -eq 'running' -and (!$row.Health -or $row.Health -eq 'healthy')){return};Start-Sleep 2};throw "$service did not recover"}
function Drill([string]$name,[scriptblock]$action){$started=[DateTimeOffset]::UtcNow;try{&$action;$script:results+=[ordered]@{name=$name;passed=$true;startedAt=$started.ToString('o');completedAt=[DateTimeOffset]::UtcNow.ToString('o')}}catch{$script:results+=[ordered]@{name=$name;passed=$false;error=$_.Exception.GetType().Name};throw}}
function Invoke-SandboxJson([string]$method,[string]$path,[hashtable]$headers,[object]$body=$null){
 $arguments=@{Method=$method;Uri="$($env:SANDBOX_BASE_URL.TrimEnd('/'))$path";Headers=$headers;TimeoutSec=45}
 if($null-ne$body){$arguments.ContentType='application/json';$arguments.Body=$body|ConvertTo-Json -Depth 8}
 Invoke-RestMethod @arguments
}
Drill 'transaction-service-restart-during-transfer-recovery' {
 foreach($required in 'SANDBOX_BASE_URL','SANDBOX_OPERATOR_USERNAME','SANDBOX_OPERATOR_PASSWORD'){
  if([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($required))){throw "$required is required for the live synthetic transfer drill"}
 }
 [System.Net.ServicePointManager]::ServerCertificateValidationCallback={$true}
 $login=Invoke-SandboxJson POST '/account-api/api/auth/login' @{} @{username=$env:SANDBOX_OPERATOR_USERNAME;password=$env:SANDBOX_OPERATOR_PASSWORD}
 if([string]::IsNullOrWhiteSpace($login.accessToken)){throw 'Synthetic operator login did not return an access token'}
 $headers=@{Authorization="Bearer $($login.accessToken)"}
 $accountPage=Invoke-SandboxJson GET '/account-api/api/accounts?size=100' $headers
 $accounts=@($accountPage.content|Where-Object{$_.status-eq'ACTIVE'})
 $source=$accounts|Where-Object{[decimal]$_.balance-ge1}|Select-Object -First 1
 if($null-eq$source){throw 'The live restart drill requires one active funded synthetic account'}
 $destination=$accounts|Where-Object{$_.id-ne$source.id-and$_.currency-eq$source.currency}|Select-Object -First 1
 if($null-eq$destination){
  $destination=Invoke-SandboxJson POST '/account-api/api/accounts' $headers @{
   accountType='CHECKING';currency=$source.currency;interestRate=0;creditLimit=0
  }
 }
 $initialTotal=[decimal]((@(Invoke-SandboxJson GET '/account-api/api/accounts?size=100' $headers).content|Measure-Object balance -Sum).Sum)
 $firstRun=[DateTimeOffset]::UtcNow.AddSeconds(8)
 $schedule=Invoke-SandboxJson POST '/transaction-api/api/scheduled-transfers' $headers @{
  fromAccountId=$source.id;toAccountId=$destination.id;amount=0.01;currency=$source.currency
  description='Phase 4 restart recovery drill';reference='phase4-restart-recovery'
  scheduleType='ONE_TIME';firstRunAt=$firstRun.ToString('o');timeZone='UTC'
 }
 if($schedule.scheduleId-notmatch'^[0-9a-f-]{36}$'){throw 'Scheduled-transfer drill returned an invalid identifier'}
 Start-Sleep -Seconds ([Math]::Max(1,[Math]::Ceiling(($firstRun-[DateTimeOffset]::UtcNow).TotalSeconds)+1))
 $accountContainer=(docker compose --project-name $project -f $base ps -q account-service).Trim()
 $databaseContainer=(docker compose --project-name $project -f $base ps -q transaction-postgres).Trim()
 if(!$accountContainer-or!$databaseContainer){throw 'Required synthetic containers are unavailable'}
 $env:PHASE4_DRILL_TOKEN=$login.accessToken
 $paused=$false
 try{
  docker pause $accountContainer|Out-Null;$paused=$true
  $workerJob=Start-Job -ArgumentList $env:SANDBOX_BASE_URL,$login.accessToken -ScriptBlock {
   param($sandboxBaseUrl,$accessToken)
   & curl.exe -ksS -X POST -H "Authorization: Bearer $accessToken" "$($sandboxBaseUrl.TrimEnd('/'))/transaction-api/api/scheduled-transfers/admin/recover-stale?batchSize=10"|Out-Null
  }
  $claimed=$false
  for($i=0;$i-lt40;$i++){
   $count=(docker exec $databaseContainer psql -U transaction_app -d transaction_sandbox -tAc "select count(*) from scheduled_transfer_runs where schedule_id='$($schedule.scheduleId)' and status='PROCESSING';").Trim()
   if([int]$count-gt0){$claimed=$true;break};Start-Sleep -Milliseconds 250
  }
  if(!$claimed){throw 'The worker did not persist an in-flight claim'}
  docker compose --project-name $project -f $base restart transaction-service|Out-Null
 }finally{
  if($paused){docker unpause $accountContainer|Out-Null}
  if($null-ne$workerJob){Stop-Job $workerJob -ErrorAction SilentlyContinue;Remove-Job $workerJob -Force -ErrorAction SilentlyContinue}
  Remove-Item Env:PHASE4_DRILL_TOKEN -ErrorAction SilentlyContinue
 }
 Wait-Healthy 'transaction-service'
 $updated=(docker exec $databaseContainer psql -U transaction_app -d transaction_sandbox -tAc "update scheduled_transfer_runs set started_at=current_timestamp-interval '301 seconds' where schedule_id='$($schedule.scheduleId)' and status='PROCESSING' returning run_id;").Trim()
 if([string]::IsNullOrWhiteSpace($updated)){throw 'The exact synthetic claim could not be fast-forwarded to stale'}
 $recovery=Invoke-SandboxJson POST '/transaction-api/api/scheduled-transfers/admin/recover-stale?batchSize=10' $headers
 $runs=Invoke-SandboxJson GET "/transaction-api/api/scheduled-transfers/$($schedule.scheduleId)/runs?size=20" $headers
 $transactionCount=(docker exec $databaseContainer psql -U transaction_app -d transaction_sandbox -tAc "select count(*) from transactions where idempotency_key like 'scheduled-transfer:$($schedule.scheduleId):%' and status='COMPLETED';").Trim()
 $finalTotal=[decimal]((@(Invoke-SandboxJson GET '/account-api/api/accounts?size=100' $headers).content|Measure-Object balance -Sum).Sum)
 if($runs.totalElements-ne1-or$runs.content[0].status-ne'COMPLETED'){throw 'Interrupted scheduled transfer did not recover to one completed run'}
 if([int]$transactionCount-ne1){throw 'Interrupted scheduled transfer did not create exactly one completed transaction'}
 if($finalTotal-ne$initialTotal){throw 'Synthetic total balance changed during the restart drill'}
 $script:restartTransferEvidence=[ordered]@{
  scheduleId=$schedule.scheduleId;durableClaimObserved=$true;leaseFastForwardedSeconds=301
  completedRuns=1;completedTransactions=1;balanceTotalConserved=$true;operatorRecoveryProcessed=$recovery.processed
 }
}
Drill 'postgres-restart-recovery' { docker compose --project-name $project -f $base restart transaction-postgres|Out-Null;Wait-Healthy 'transaction-postgres';Wait-Healthy 'transaction-service' }
Drill 'redis-loss-recovery' { docker compose --project-name $project -f $base stop redis|Out-Null;Start-Sleep 3;docker compose --project-name $project -f $base start redis|Out-Null;Wait-Healthy 'redis';Wait-Healthy 'transaction-service' }
Drill 'duplicate-request-and-stuck-schedule-regression' {
 Push-Location (Join-Path $root 'transaction-service');try{.\mvnw.cmd -q '-Dtest=ScheduledTransferServiceTest,TransactionIdempotencyClaimServiceTest,SpendingLimitReservationClientAspectTest,SpendingLimitReservationSagaCoordinatorTest' test;if($LASTEXITCODE-ne0){throw 'Duplicate/stuck-schedule regressions failed'}}finally{Pop-Location}
}
Drill 'notification-receiver-failure-regression' {
 Push-Location (Join-Path $root 'account-service');try{.\mvnw.cmd -q '-Dtest=NotificationProviderBoundaryTest,HttpNotificationProviderTest,NotificationProviderRetryDispatcherTest,NotificationProviderDispatcherTest' test;if($LASTEXITCODE-ne0){throw 'Notification receiver failure regressions failed'}}finally{Pop-Location}
}
Drill 'alert-receiver-fail-closed-contract' { $config=Get-Content -Raw (Join-Path $root 'transaction-service\monitoring\alertmanager\alertmanager.yml');if($config -notmatch 'send_resolved:\s*true' -or $config -match 'hooks\.slack\.com|pagerduty|company\.com'){throw 'Receiver contract is not fail-closed'} }
$out=Join-Path $EvidenceDirectory ("failure-drill-"+[DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'.json')
[ordered]@{classification='SYNTHETIC_NO_REAL_MONEY';bounded=$true;completedAt=[DateTimeOffset]::UtcNow.ToString('o');restartTransferEvidence=$restartTransferEvidence;results=$results}|ConvertTo-Json -Depth 5|Set-Content -Encoding utf8 $out
Write-Output $out
