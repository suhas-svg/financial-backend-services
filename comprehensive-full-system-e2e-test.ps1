#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Comprehensive Full System End-to-End Test for Account Service and Transaction Service Integration
.DESCRIPTION
    This script runs both services together using Docker Compose and performs comprehensive 
    end-to-end testing of the complete financial system workflow with real service-to-service communication.
.PARAMETER CleanStart
    Whether to clean and rebuild everything from scratch (default: true)
.PARAMETER UseDocker
    Whether to use Docker Compose for service orchestration (default: true)
.PARAMETER TestTimeout
    Timeout in seconds for individual tests (default: 30)
.PARAMETER ServiceTimeout
    Timeout in seconds for service startup (default: 180)
.EXAMPLE
    ./comprehensive-full-system-e2e-test.ps1 -CleanStart $true -UseDocker $true
#>

param(
    [bool]$CleanStart = $true,
    [bool]$UseDocker = $true,
    [int]$TestTimeout = 30,
    [int]$ServiceTimeout = 180
)

# Configuration
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# Colors for output
$Green = "`e[32m"
$Red = "`e[31m"
$Yellow = "`e[33m"
$Blue = "`e[34m"
$Cyan = "`e[36m"
$Magenta = "`e[35m"
$Reset = "`e[0m"

# Service URLs
$AccountServiceUrl = if ($UseDocker) { "http://localhost:8081" } else { "http://localhost:8081" }
$TransactionServiceUrl = if ($UseDocker) { "http://localhost:8080" } else { "http://localhost:8080" }

# Test results tracking
$TestResults = @{
    Infrastructure = @{}
    ServiceStartup = @{}
    ServiceIntegration = @{}
    EndToEndWorkflows = @{}
    PerformanceTests = @{}
    ErrorHandling = @{}
    DataConsistency = @{}
    SecurityTests = @{}
    CleanupTests = @{}
}

# Test data
$TestUsers = @{
    StandardUser = @{
        UserId = "e2e-standard-user"
        AccountType = "STANDARD"
        InitialBalance = 1000.00
    }
    PremiumUser = @{
        UserId = "e2e-premium-user"
        AccountType = "PREMIUM"
        InitialBalance = 5000.00
    }
    BusinessUser = @{
        UserId = "e2e-business-user"
        AccountType = "BUSINESS"
        InitialBalance = 10000.00
    }
}

function Write-Header {
    param([string]$Message)
    Write-Host "`n${Blue}=== $Message ===${Reset}" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "${Green}✓${Reset} $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "${Red}✗${Reset} $Message" -ForegroundColor Red
}

function Write-Warning {
    param([string]$Message)
    Write-Host "${Yellow}⚠${Reset} $Message" -ForegroundColor Yellow
}

function Write-Info {
    param([string]$Message)
    Write-Host "${Cyan}ℹ${Reset} $Message" -ForegroundColor Cyan
}

function Write-Step {
    param([string]$Message)
    Write-Host "${Magenta}→${Reset} $Message" -ForegroundColor Magenta
}

function Test-Prerequisites {
    Write-Header "Checking Prerequisites"
    
    $allGood = $true
    
    # Check Docker
    try {
        $dockerVersion = docker --version 2>$null
        Write-Success "Docker found: $dockerVersion"
        
        # Check if Docker daemon is running
        docker info 2>$null | Out-Null
        Write-Success "Docker daemon is running"
    }
    catch {
        Write-Error "Docker is not available or not running"
        $allGood = $false
    }
    
    # Check Docker Compose
    try {
        $composeVersion = docker-compose --version 2>$null
        Write-Success "Docker Compose found: $composeVersion"
    }
    catch {
        Write-Error "Docker Compose is not available"
        $allGood = $false
    }
    
    # Check if required files exist
    $requiredFiles = @(
        "docker-compose-full-e2e.yml",
        "account-service/Dockerfile",
        "transaction-service/Dockerfile",
        "transaction-service/src/test/resources/full-system-init.sql"
    )
    
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Write-Success "Required file found: $file"
        } else {
            Write-Error "Required file missing: $file"
            $allGood = $false
        }
    }
    
    return $allGood
}

function Start-FullSystemInfrastructure {
    Write-Header "Starting Full System Infrastructure"
    
    try {
        if ($CleanStart) {
            Write-Info "Cleaning up existing containers and volumes..."
            docker-compose -f docker-compose-full-e2e.yml down -v --remove-orphans 2>$null
            docker system prune -f 2>$null
        }
        
        Write-Info "Building and starting services with Docker Compose..."
        $env:COMPOSE_PROJECT_NAME = "full-e2e-test"
        
        # Start infrastructure services first
        Write-Step "Starting database and cache services..."
        docker-compose -f docker-compose-full-e2e.yml up -d postgres-e2e redis-e2e
        
        # Wait for databases to be ready
        Write-Step "Waiting for databases to be ready..."
        $maxRetries = 30
        for ($i = 1; $i -le $maxRetries; $i++) {
            try {
                $pgHealth = docker-compose -f docker-compose-full-e2e.yml exec -T postgres-e2e pg_isready -U testuser -d fullsystem_test
                $redisHealth = docker-compose -f docker-compose-full-e2e.yml exec -T redis-e2e redis-cli ping
                
                if ($pgHealth -match "accepting connections" -and $redisHealth -eq "PONG") {
                    Write-Success "Databases are ready"
                    break
                }
            }
            catch {
                if ($i -eq $maxRetries) {
                    Write-Error "Databases failed to start within timeout"
                    return $false
                }
            }
            Write-Info "Waiting for databases... (attempt $i/$maxRetries)"
            Start-Sleep -Seconds 3
        }
        
        # Build and start application services
        Write-Step "Building and starting Account Service..."
        docker-compose -f docker-compose-full-e2e.yml up -d --build account-service-e2e
        
        Write-Step "Building and starting Transaction Service..."
        docker-compose -f docker-compose-full-e2e.yml up -d --build transaction-service-e2e
        
        $TestResults.Infrastructure.DatabaseStartup = $true
        $TestResults.Infrastructure.ServiceBuild = $true
        return $true
    }
    catch {
        Write-Error "Failed to start infrastructure: $($_.Exception.Message)"
        $TestResults.Infrastructure.DatabaseStartup = $false
        ll
}ns 2>$nuremove-orphadown -v --e.yml ose-full-e2ocker-compompose -f d   docker-cup..."
 Final cleanInfo "e-  Writhappens
  nup leaEnsure cy {
    # 
}
finallexit 1ture
    trucystemInfrasllSStop-Fu    .."
anup.ing cleo "AttemptWrite-Inf  ssage)"
  n.Meceptio: $($_.ExE testinge E2ensivg compreherror durinatal -Error "Fite Wratch {
   
c
}
    }exit 1      s"
  tailfor deort view the repled - re tests faitical "Some criror-Er   Write
     SUES"H IS WITETEDCOMPLUITE 2E TEST SPREHENSIVE ECOMder "❌ Hearite-      W{
    } else exit 0
      ment"
    loytion depducdy for prois rea"System Success Write-        "
ifiedion verce integration Servid Transactvice ant Ser"Both Accounccess   Write-SuY!"
      CCESSFULLPLETED SUST SUITE COMSIVE E2E TEPREHENder "🎉 COMea  Write-H
      uccess) { ($reportSsult
    ifnal re
    # Fiture
    trucfrasmIn-FullSyste
    Stopleanup
    # Crt
    poveTestRerehensierate-Compccess = GenreportSu    $ort
ive repnsmpreheco # Generate  
        }
      }

    = $falseSuccess rall     $ove"
       esfailursts had te.Name) testSuining "$($teWar    Write-{
        ot $result)       if (-n
  nctiontestSuite.Fusult = & $  $re."
       tests...Name)Suiteest$($tnning Ruite-Info "      Wr  s) {
estSuiten $te itSuitreach ($tes    fo
e
    $trullSuccess = vera 
    $o
   } }
    )narios ecuritySce { Test-Stion =nc; Funarios"Sceurity Secame = "{ N      @e } }
  ceBaselin-Performanion = { Test; Functine"ce Baselanrm= "Perfoe   @{ Nam } }
      encyDataConsistTest-on = { Functiistency"; ata Cons"D =   @{ Name   
   s } }onrentOperatist-Concurtion = { Teons"; Funcperaticurrent Oon = "C  @{ Name } }
      ngScenariosHandliest-Errorction = { T"; Fung Scenarioslinrror Handame = "E  @{ N } }
      flowtWorkinessAccounus-Bn = { Testtiow"; Funclo Workfuntess Accosin"Bu @{ Name =        } }
ney erJourCompleteUst-= { Tes"; Function yneUser Jouromplete { Name = "C  @  on } }
    ratiIntegServicet-on = { Tescti"; Funrationvice IntegName = "Ser  @{        = @(
Suites    $testsuites
test # Run        }
    
  exit 1
    
   astructuremInfrFullSyste     Stop-erly"
   rop pto startices failed r "ServWrite-Erro{
        ) vicesReady)ert-ForSf (-not (Wai
    i readybees to icait for serv 
    # W
    }
    1xit      e"
  ucturet infrastrto staror "Failed rrite-E   Wr)) {
     rastructureSystemInftart-Full-not (S    if (e
uctur infrastrtart# S
    
        }xit 1
        e
led"eck faiquisites chr "Prere Write-Erro) {
       ites)t-Prerequis (-not (Tes
    ifequisites Check prer
    
    #onds"eTimeout secvice=$Servicer seconds, StTimeoutest=$Teseouts: TInfo "TimWrite-cker"
    eDoocker=$Ust, UseDarSt$CleanCleanStart=iguration: Info "Confite-   WrTest"
  End-to-End ull Systemive Fmprehens"Starting CoHeader te- {
    Wrion
tryexecutiain }

# Mtests pass
re r mo5% ocessful if 8te sucsuitest # Consider  85  Rate -geesssuccrn $ retu    
   portPath"
d to: $reort saverepd leaiss "Detite-Succe Wreport
   te-Host $r
    Wri    UTF8
 h -EncodingatportP$reePath t-File -Fil| Ou    $report "
).mdMMdd-HHmmss' 'yyyyDate -Format$(Get-report--e2e-test-ensive"comprehrtPath =     $repofile
t to  repor
    # Save    
"@
st Suite*tem E2E Teull Sysve Fprehensiated by Comergent epor-
*Re

--est suits thised on ts banbooknt rumeloyate dep
5. Creentn deploymtior producng fotind alertoring a moni
4. Set upnt environmeionproductons in onfiguratiy cify securit3. Vergh
hitimes are esponse f rization irmance optimerfo
2. Run psfailed testy w and fix anRevieeps

1. 
## Next St})

oyment"e deples befornificant fixsigm requires  Syste ISSUES** -ALIC  "❌ **CRIT {
  
} elseesolved"e r need to buesissral SeveNT** - DS IMPROVEME **NEE   "⚠️{
  70) Rate -geesssucc ($if"
} elseductionore prod tests befiles fay, addrestly readosystem is m* - S⚠️ **GOOD*{
    "-ge 85) ccessRate lseif ($suyment"
} ection deplo produfor is ready SystemNT** - LLE**EXCE"✅  {
    e 95)-gRate cess$suc

$(if (onsmendaticom## Re"`n")

join s -($failedTest
$sed Test Fail
##
eanup)tureClastrucnfrests.I.CleanupTTestResultsleanup: $($tructure Cts
- Infrasleanup Tes## C
#ionTests)
.AuthenticatyTests.SecuritestResults $($Ts: Testcation- Authentiy Tests
 Securite)ms

###nsactionTimndTraests.EndToEceTs.PerformansultstRe($Tee: $ Timctionansa-End Tr End-toTime)ms
-iceResponsenServsactio.TranstseTeormancPerflts.Resuime: $($TestResponse TService ion sact
- Tran)msTimesponsentServiceReoueTests.Accormancs.Perflt($TestResuime: $e Response Tcount ServicTests
- Acformance ### Pertency)

tionConsisncy.TransacConsisteDataults.estResstency: $($TConsiaction nsests
- Traency Tnsist Coata## Dios)

#cenarng.InvalidSHandli.ErroresultstR($Tescenarios: $d Sts
- Invaling TesndliHa# Error ##

Operations)rentflows.ConcurWorkoEndEndTlts.esustR: $($Tenstioperaoncurrent Oow)
- CountWorkflsAccinesorkflows.BusndWs.EndToEResultow: $($Testount WorkflAcc- Business erJourney)
mpleteUs.CoEndWorkflowssults.EndTostRerney: $($Teer Jou Usetets
- ComplTeskflow nd Woro-E

### End-tmunication)erviceCom.SoniceIntegratiesults.Serv: $($TestRmmunicationCo Service PI)
-erviceAccountSegration.A.ServiceIntResults$TestAPI: $(ervice unt S
- Accolth)iceHeasactionServTrantegration.rviceInts.Seul: $($TestResalthrvice Hection Se
- Transa)ceHealthntServiration.AccouIntegerviceesults.STestRHealth: $($nt Service  AccouTests
-ration e Integ## Servic
#dis)
ReServicesactionTranup.viceStartSersults.TestRe Redis: $($ion Service
- TransactServiceDB).TransactionuprticeStaervestResults.S$Tice DB: $(action ServTrans)
- tServiceDBunccoartup.As.ServiceStsult: $($TestRevice DBcount Sere)
- AcnServicctiosaanartup.TriceSts.Serv$TestResult$(ervice: on S Transactiice)
-untServartup.AccoiceStrvstResults.Se$Tece: $(t Serviuns
- Accortup Testrvice Sta

### SeviceBuild)ture.Sernfrastrucesults.ITestRe Build: $($ic)
- ServseStartuptabaure.DaastructResults.Infrst: $($Teupase StartabTests
- Datructure 
### Infrastgories
# Test Cateate}%

#{successR:** $ Rate **SuccessTests)
-sed- $pastotalTests $($:** 
- **FailedsedTests $pasPassed:****s
- lTests:** $totaal TestTotmary

- **cutive Sumxe## Etion)

tegraice (Real In Servransaction Te +ervic** Account Sces Tested:m
**Servitell SysCompose Fur on:** Dockegurati*Test Confimestamp
* $tirated:**ne

**GeTest Report-End stem End-to Syullsive Fmprehen Co
#t = @"$repor
    0 }
    } else { 2) ts) * 100, otalTesdTests / $tound(($passeath]::R[mts -gt 0) { f ($totalTese = icessRatuc 
    $s  }
         }
     }
        
   .$test"gory$catests += "   $failedTe       {
       se el        }
    assedTests++          $p   
   true) {-eq $ry][$test] gocatesults[$estRef ($T     i      sts++
 totalTe   $        {
 s) ategory].Key[$csultsin $TestRech ($test       forea
   {ys)Results.Ke $Test ingorych ($cate   foreaesults
 t rtes   # Count 
    
  = @()sts  $failedTe   0
sedTests =  $pasests = 0
   $totalT"
   -dd HH:mm:ssyy-MMt "yy-Date -Formatamp = Get   $times 
 t"
   porve Test Reehensig Compr "Generatinite-Header
    Wrort {eTestRepmprehensivrate-Counction Gene
}

f
    }alse return $f
        $falsereCleanup =frastructupTests.Inleanus.CstResult  $Tee)"
      tion.Messagcepure: $($_.Exstructranfed to stop iilror "Fa Write-Erh {
        catc
      }rn $true
    returue
     eanup = $teClstructurranupTests.InfeaClults.estRes       $T up"
  and cleanedtoppedcture sInfrastru-Success "rite 
        W   
    -f 2>$nullstem prune  docker sy"
       esources...p Docker rCleaning ue-Info "it        Wr
        -orphans
v --removen -yml dowe-full-e2e.er-composckose -f docompdocker-    ."
    rvices.. all seppingInfo "Stoe- Writ     ry {
     t   
 e"
 ructurastInfrSystem ull  Fr "Stoppingite-Heade
    Wr{cture temInfrastru-FullSys Stopfunction  }
}

 $false
     return      = $false
cationTestsuthentiurityTests.AResults.Sec $Test
       ge)"eption.Messa.Exc $($_ failed:rity testError "Secute-Wri
        atch {
    crue
    }$treturn e
         $trunTests =icatioests.AuthentecurityT.SstResults
        $Te   
      }       $false
 rntu       re
     sage)"ption.Mes: $($_.Excen failedthenticatio"Valid auror te-Er      Wri
        catch {  
     }       rrectly"
ng coation worki authenticss "Validrite-Succe          WGet
  d " -Methouator/healtheUrl/actServic"$Accountri thod -UstMe= Invoke-ReuntHealth $acco            y {
        tr    
}
            on"
ication/js"appl-Type" = ntent"Co         "
   earer $token" = "Bzationthori"Au    @{
        eaders = alidH
        $vJwtTokenesten = Get-T    $tokd work
    en shoul: Valid tok   # Test 3   
   
       }       }
     e
        $falsrn       retu          Code"
: $statusenlid toknvae for iatus codcted stror "Unexpe    Write-Er            else {
       }
      us: 401)"tat rejected (Sen properlyalid tok "Invuccess   Write-S           ) {
  401tusCode -eq    if ($sta        __
 e.valuese.StatusCod.Responion = $_.ExcepttusCode $sta          {
  catch         }
 se
      $fal     return d"
       est succeedebut requ error, onalidatid token vor "Expecteite-Err     Wr      s
 idHeaderers $invalHeadethod Get -ccounts" -M/api/aServiceUrl$Accounthod -Uri "etRestMke-voInesult =  $r         try {
           
    }
     
      "ion/jsonlicate" = "appTyp "Content-         en"
  ok-tarer invalidBeon" = "ati"Authoriz            rs = @{
de$invalidHea
        illd fahoutoken sd : Invalist 2      # Te   
      }
     
     }          lse
 turn $fa       re        de"
 t: $statusCocated requestiunauthenr fostatus code xpected "Unerror    Write-E        e {
     } els            s: 401)"
cted (Statuperly rejest proed requecatnauthenti"Ucess te-Sucri      W          1) {
Code -eq 40f ($status      i     e__
 sCode.value.Statuon.Respons_.ExceptiatusCode = $         $st  ch {
         cat   }

     eurn $fals    ret"
         succeededquestt reerror, buntication pected autheEx "Error  Write-          hod Get
ts" -Meti/accouneUrl/apccountServici "$Ahod -UrtMetke-Result = Invo$res         
   ry {       t
 hould failt sated requesuthentic: Una # Test 1       
       ts..."
 emen requiricationing authent "Testrite-Step      W try {
    
   "
  ariosecurity Sceng Stineader "Teste-H {
    WrienarioscuritySc Test-Se
function

    }
}rn $false     retusage)"
   Meseption.$($_.Exc: failed test lineance baser "PerformErro  Write-{
      catch        }

        }
 performancet suite for tire tes the en failrue  # Don'turn $t      ret      
ngs"niwarted with omplee test cnce baseling "Performarite-Warnin     We {
             } elsn $true
         retur    "
 passede test ce baselin "Performanite-Success       Wrle) {
     tabormanceAccep  if ($perf    
          00)
ime -lt 50nsactionT  $tra                             0 -and 
  e -lt 200imeTctionServicsa$tran                          d 
       000 -ane -lt 2viceTimSer$account (le =ceAcceptab $performan       ble time
reasonain plete witherations comle if all opcceptabormance is a  # Perf      
    onTime
    ansacti$tr = onTimesactiandTrts.EndToEnesceTormanults.Perf$TestResme
        nServiceTictio= $transasponseTime ceReervitionSacs.TransmanceTestlts.PerforstResu $Te
       erviceTimeountSccime = $aonseTespntServiceRouTests.Accancerforms.PelttResu       $Tes    
 ms"
    me}tionTitransacn time: ${nsactio tra-to-enduccess "End  Write-S    
      
    secondsdMilli.Elapseatchopw $sttionTime =ransac    $top()
    ch.Sttopwat     $s
   son) ConvertTo-JepositData |y ($ders -Bodhead $ost -Headers  -Method P        `
   posit"/deansactionsrl/api/trerviceUsactionSri "$Tranthod -U-RestMeult = InvokesitRes   $depo     rtNew()
h]::Statopwatc.Sgnosticsem.Dia[Systpwatch = sto   $     
       }
   it"
       test deposance "Performon =ptiscri          de0.00
  mount = 10           a
 .ToString()count.id$perfAcntId =       accou       = @{
ataitD     $depos
   ormanceperfransaction  # Test t  
       son)
      ConvertTo-Jta | countDarfAcBody ($pes -$headerst -Headers Method Po       -     `
" ntscoueUrl/api/acrvic"$AccountSehod -Uri MetInvoke-Restccount = fA    $per  
                }
.00
  = 1000alance nitialB         i
   NDARD"Type = "STAccount    a
        er"t-usf-tes = "pernerId      ow    
  tData = @{perfAccoun        $ount
ate test acc # Cre
       
                }n"
/jsolicationapp "pe" =ent-Ty  "Cont     ken"
     er $toar = "Beation""Authoriz            rs = @{
ade $heken
       et-TestJwtTooken = G  $tce
      n performannsactio-end traTest end-to       # 
 
        eTime}ms"ionServic: ${transactse timepon res ServiceTransactione-Success "Writ"
        msceTime}ccountServie: ${a timnserespoice unt Servcos "AcWrite-Succes      
         econds
 edMillistch.Elaps = $stopwarviceTimenSe $transactio       )
Stop( $stopwatch.et
       ethod Gth" -Meal/hatorviceUrl/actuansactionSer$Tr -Uri "stMethodvoke-Realth = InnsactionHe     $tra
   w()Ne::Startopwatch]Stnostics.System.Diagwatch = [   $stope
     nse timpoService resaction ans# Test Tr          
    
  ondsMillisec.Elapsedstopwatchme = $rviceTitSecoun   $ac
     top()atch.S      $stopwGet
  ethod lth" -Mator/hea/actutServiceUrlri "$AccountMethod -U Invoke-RestHealth =ccoun        $aStartNew()
opwatch]::stics.Sttem.Diagno[Sysopwatch =      $st   ponse time
 resiceAccount Serv# Test          
  .."
     onse times.e respsting servic"TeWrite-Step     ry {
      
    t
  seline"ormance Bag Perfder "TestinHeaite- Wr{
   line eBaseerformancest-Pon Tncti
fu
   }
}rn $false
     retu   = $false
 cy sistensactionConcy.TranaConsistenatstResults.DTe $"
       n.Message)$_.Exceptioiled: $(st facy teisten"Data conste-Error Wri      catch {
       }
    }
  e
      $falseturn           rlse
 ency = $faonsistionCy.TransactnsistenctaCo.DaesultsTestR    $        balance)"
ccount2.nalActual: $($fiance2, ABalpecteded: $exxpect 2 - E "Accountte-Error Wri       nce)"
    count1.balafinalAcctual: $($dBalance1, A: $expecte Expected"Account 1 -r e-ErroWrit  
          ted"ency detec inconsist"Balancee-Error      Writ     e {
         } els   }
         $false
     return         $false
    stency = actionConsiansistency.TrCons.DatasultsRe     $Test           ted"
ncy detecnsistetory incotion hisr "Transacrro Write-E             {
     } else        rue
  $treturn                y = $true
 istencnsactionConsracy.Tistennss.DataCostResult $Te              fied"
 eritency vory consisn histio"TransactSuccess Write-              
  nt -ge 3) {ontent.Cou.cory2 -and $histnt -ge 2content.Couy1.($histor        if            
   ders
  hea-Headers $ethod Get    -M            )" `
 idunt2./$($accons/accounttioi/transacUrl/apnServiceansactio "$Trthod -UriRestMe2 = Invoke-  $history         eaders
 Headers $hethod Get -          -M      `
t1.id)" /$($accounaccountransactions/eUrl/api/tnServicnsactiorari "$T -UMethode-RestInvokstory1 = $hi        ncy
    y consisteon historactinsrify tra    # Ve           
  )"
       2.balanceinalAccount2: $($fe), Account balancnt1.inalAccouount 1: $($fified - Accency verata consist"Dte-Success   Wri
          lt 0.01) {e2) -Balanc $expectedce -anunt2.balalAcco]::Abs($fin  [math      and
     -01 -lt 0.1)ncexpectedBala - $ebalance1.countinalActh]::Abs($f   if ([ma     
     5.00
   nce2 = 112tedBala   $expec50.00
     lance1 = 10pectedBa        $ex 75 = 1125
50 - 100 +000 + 12: 1# Account 
        050150 = 10 + 200 - count 1: 100Ac   #     balances
 expected te  Calcula     #    
   
    adersders $heHeaet -ethod G-M           
 " `.id)unt2unts/$($accol/api/accoceUrServii "$Account-UrtMethod e-Rest2 = Invokccoun     $finalA
    $headerseaderset -H God -Meth          `
 ount1.id)" $($accounts/l/api/accviceUrSerunt"$Accoethod -Uri -RestMke= Invoccount1 alA$fin
         ."
       lances..nt bainal accou f "Verifying Write-Step
       ancesalal b Verify fin      #   
  
      }      100
  secondsllileep -Mi    Start-S      ng
  rirdection onsure transay to edela   # Small                
       }
           urn $false
et   r          led"
   ype) faiion.T$transactror "$(e-Er      Writ   {
         else          }onId)"
  nsactiult.tra$($res- ID: leted ype) compansaction.T "$($trite-Success Wr      d
         nsactionIsult.traonIds += $re$transacti      
          ) {MPLETED"q "COt.status -e$resul  if (
                 
     Json) ConvertTo-ionData |actody ($transders -Bs $heat -Headerethod Pos-M         " `
       $endpointransactions/i/taperviceUrl/nsactionS$Trathod -Uri "-RestMe= Invokesult       $re   
            
      }}
                  
       "= "transferint      $endpo          }
               
          nioion.Descript$transaction = script      de                  nt
Amouransaction. = $tamount                   ing()
     d.ToStrntIccousaction.ToAntId = $tran   toAccou                  g()
   ntId.ToStrinouion.FromAcc $transactountId =fromAcc                        = @{
Data ansaction    $tr              " {
  nsfer "tra            }
                   aw"
withdr "oint = $endp                   }
             on
       iptition.Descrnsac$traiption =   descr                      
mountsaction.Aunt = $tran     amo                   ()
Id.ToStringn.Accounttio = $transacntId accou                    {
   Data = @tionacrans   $t             {
     ithdraw"        "w         }
      "
         itt = "deposendpoin     $             }
               on
       .Descriptisactionanption = $tr     descri             
      .Amounttransaction $    amount =             
       ing()oStrcountId.Ttion.AcsacId = $trancount         ac               ata = @{
nsactionDra        $t          
  sit" {"depo               ype) {
 ansaction.T($trch         swit            
  = ""
   nt   $endpoi     = @{}
    Data ctiontransa         $         
     "
 ction...ransae) tTypansaction.g $($trinp "Perform-Ste    Write     ns) {
   ransactioon in $tctiransah ($t  foreac 
      
       @()ctionIds = $transa           
     )
  }
       2" ositst deptency tesison = "Cscription; De00t = 75.; Amoun.idaccount2= $countId posit"; Ac"de@{ Type =     
        wal" }thdray test wi"Consistenc= iption escr00.00; Dmount = 1count2.id; AacountId = $; Acc"withdraw"@{ Type =            }
  t transfer"istency tes= "Consn iptio0; Descrnt = 150.0.id; Amou= $account2ntId oAccount1.id; TtId = $accouFromAccounnsfer"; e = "tra    @{ Typ     1" }
   st deposit cy te"Consisten =  Description0;0.020Amount = id; t1.un= $accoccountId it"; Apospe = "de Ty          @{
   = @(tionstransac        $
actionses of transm a serifor       # Per
         ed"
atounts creccy test astenconsiuccess "Cte-S
        Wri      o-Json)
  | ConvertTcount2Data ody ($ac$headers -Beaders t -Hethod Pos     -M      
 ccounts" `/api/atServiceUrl"$Accounethod -Uri voke-RestMt2 = In   $accoun)
     Json| ConvertTo-ccount1Data ($aody headers -B-Headers $ Post ethod   -M         s" `
pi/accounterviceUrl/antS "$AccouUrithod -tMevoke-Res1 = Incount
        $ac     }
      .00
     1000e = alancalBtiini          ARD"
  = "STANDtType  accoun          -2"
 serncy-usisteon= "cId     owner        = @{
 atat2Dccoun       $a       
  }
 0
       = 1000.0e ancBal     initial      NDARD"
 = "STAuntType    acco"
         ncy-user-1nsiste"conerId =  ow       @{
    nt1Data = ccou       $as
 accounto  tw  # Create
           
   ting..."essistency tconfor counts ing aceatCr "Write-Step       
  {  
    try}
  
    json"cation/"appliype" = ntent-T       "Co"
 arer $token"Be" = horization     "Aut   rs = @{

    $headeJwtTokenTest Get-  $token =   
"
   istencya Cons"Testing Dater ad-He{
    Writeonsistency  Test-DataCunction
}

fe
    }ls  return $fa
      alse= $fations perConcurrentOws.dWorkflots.EndToEnResulest        $Tessage)"
ption.M.Exce $($_ failed:ons testperatirrent oConcu"ror    Write-Er
     {  catch  }
       }
       $false
eturn     r    
   ns = $falseentOperatioows.ConcurrflorkEndToEndWlts.   $TestResu       "
  nt)tionCouransaclureCount/$t ($faiiluresnsaction faurrent trany concoo ma "Trrore-Eit    Wr  
       } else {}
                  
 turn $false          re    
  alsens = $ftOperatioConcurrenorkflows.ts.EndToEndWsul $TestRe           e)"
    balancunt.$($finalAccotual: Aclance, expectedBad: $ Expectecy -repanBalance disc-Warning "te     Wri    e {
         } els        ue
  rn $tr      retu         = $true
  nstOperatios.ConcurrenkflowToEndWors.Endsult     $TestRe        
   nce)"balacount.Acal$($fin: ce - Balanation passedance verificalal bccess "Fin-Su    Write       nces
     reding diffemall roun  # Allow s { -lt 1.00)tedBalance)ecance - $expccount.bal:Abs($finalA[math]:f ( i          
 t * 50.00)cessCoun ($suc= 10000.00 +tedBalance   $expec               
  ers
     ers $headead Get -H    -Method           id)" `
 rentAccount.oncurs/$($ccountceUrl/api/accountServi"$Acthod -Uri Mest Invoke-RenalAccount = $fi      e
     alancccount bify final a # Ver        
            
   l)" successfutionCountunt/$transacsCoed ($succespasst tess t operation "ConcurrenessWrite-Succ           
 oncurrencyre due to clu% fai# Allow 200.8)) {  ount * nsactionC ($traessCount -ge($succ    if      
  "
     iledureCount fa, $failccessfulssCount suts: $succe resulnsactionrrent traoncunfo "Cite-I Wr       
 t
       uccessCounonCount - $sansacti $trount =ilureC      $fa.Count
   $true })-eq$_.Success -Object { Where$results |  (Count =success     $       
   ve-Job
 bs | Remo $job
       e-Jo| Receivut 60 -Timeot-Job $jobs | Waisults = 
        $re."plete..s to comtiontransaconcurrent aiting for c"We-Step   Writ  te
    s to comple all jobit for   # Wa
              }
    $job
    $jobs +=                   
i
     , $onDatasacti $tran", $headers,itons/depossactianUrl/api/trionService "$TransactgumentList     } -Ar              }
         
     }           
    Message.Exception.  Error = $_               
       ctionIdnsa = $TraonId   Transacti                   $false
  ccess = Su                  
      rn @{       retu            h {
   catc               }
               }
                  ult
  reslt = $       Resu           
      ctionId$TransaionId = sact        Tran           ue
     ss = $tr    Succe                 {
     return @                To-Json)
  ert | Convdy ($Dataers -Bos $Headeaderst -H Pol -Methodod -Uri $UrtMethInvoke-Res  $result =                
         try {         )
 tionId $Transac $Data,rs,de $Hea$Url,     param(         {
  tBlock -Scripob -J= Start   $job                
  
          }       $i"
actionrrent trans "Concuon =  descripti         
     .00= 50 amount             g()
   rinid.ToStAccount.nt$concurreountId =          acc
       Data = @{ransaction    $t        {
$i++) ionCount; ctnsae $tra = 1; $i -l  for ($i 
      
        = 10nCount$transactio     
   = @() $jobs 
       transactionsmall t srreniple concumultrform  # Pe  
       
      ."..onsransactit tng concurrenPerformiep "-Stite      Wr   

       count.id)"tAcen($concurrD: $ - Int createdaccou test "Concurrentss -Succe Write
           son)
    onvertTo-JData | CountncurrentAcc($coers -Body aders $headod Post -He-Meth       s" `
     ntl/api/accouviceUrer "$AccountStMethod -Uri= Invoke-ResntAccount $concurre
               }
   0
      0000.0= 1nce Balainitial   "
         "PREMIUMe = ntTypaccou           ser"
 current-ud = "con ownerI
           @{countData = rentAcncur    $co    
    .."
    t testing.concurren for  up accountngtep "Settite-S Wri
        
    try {
   n"
    }sotion/jca "applit-Type" ="Conten       $token"
 "Bearer on" = orizatiAuth      " @{
    $headers =en
  oktJwtTt-Testoken = Ge    $ 
  ons"
  Operaticurrenton"Testing C-Header rite
    W {ationsOpercurrentn Test-Conio
funct
}
}lse
    $farn  retu      
 os = $falsecenariInvalidSrHandling.esults.Erro$TestR  
      "ssage).Meonti: $($_.Excepst failedling tehandor rrrror "E   Write-E
     catch {    }
    
$true  return       ue
rios = $trlidScenanvaing.IrrorHandls.E$TestResult  
        
      
        }      }alse
      urn $f     ret           atusCode"
 $ster:nsfraccount tame-a for statuserror sexpected ror "Une-Er      Writ         se {
     } el
        ode)"s: $statusCturrectly (Sta corksdling woan error hsfert tranounme-accSa"-Success rite    W            422) {
 de -eqstatusCoq 400 -or $e -eusCod$stat if (          e__
 Code.valuponse.Statusption.Res $_.Exce =statusCode           ${
 catch         
e
        }fals $ return        "
    succeededonansacti trransfer, butt te-accounor for sam errctedpeEx"e-Error  Writ        son)
   onvertTo-Jata | CdTransferDvali -Body ($inrsers $headeost -Headd P    -Metho     " `
       fernstions/tratransacceUrl/api/nServinsactioTrad -Uri "$tMetho-Reskeult = Invo      $resry {
             t  
  }
      "
       nsfer-account tranvalid same"Icription =       des0
      0.0  amount = 1        ring()
  .id.ToStccountoorAd = $pcountI      toAc)
      String(ccount.id.TopoorAtId = $Accoun  from          {
ata = @lidTransferD     $inva
      
     rio..."r scenaid transfeg invalp "Testinte  Write-Sunt)
      accome sad transfer ( Invali# Test 3:  
                   }
        }
       lse
return $fa                
sCode"$statunds:  fufficientinsuus for  statrrored e"Unexpecte-Error       Writ
          } else {     
       e)"tatusCod(Status: $scorrectly  works dlingerror hans fundent Insuffici "rite-Success          W  ) {
    422e -eq  $statusCod -eq 400 -orsCode  if ($statu          _
de.value_se.StatusCoResponeption.de = $_.ExcatusCo $st    {
              catch   }
  false
        return $         eeded"
ion succctt transant funds, busufficierror for in"Expected error te-E         Wri   
rtTo-Json)nveata | CoithdrawalDargeW ($lBody -s $headersst -Header  -Method Po              `
ithdraw" sactions/wl/api/tranServiceUr$Transaction -Uri "odthke-RestMenvo $result = I  
          try {   
          }
     t"
     funds tesnt iensuffic= "Iescription    d0
          = 1000.0  amount      ()
    String.id.To$poorAccountountId =     acc    = @{
     atahdrawalDrgeWit  $la
              
Json)onvertTo-ta | CorAccountDa ($poders -BodyHeaders $hea Post -  -Method         
 " `countspi/aciceUrl/aervtSAccoun "$tMethod -Urike-Res = InvorAccount   $poo    
          }

       50.00lance =   initialBa        NDARD"
   "STAcountType =        ac  r-user"
  Id = "poo  owner
          = @{tData rAccounpoo     $       
   rio..."
 enaent funds scufficiesting insp "Tte-Ste   Wri    o
 arient funds scufficient 2: InsTes    #    
       }
     
             }    n $false
 retur               "
odestatusC: $alid accountr inv fostatusr d erro"Unexpecteor   Write-Err            {
     } else      ode)"
    usCus: $statStattly (ks correcg worlin hand errorid accounts "Invalte-Succes         Wri
       e -eq 422) {tatusCod -or $s -eq 400tatusCode 404 -or $sCode -equs ($stat     if      ue__
 e.val.StatusCodesponseon.R.Excepti$_de = atusCo     $sth {
       tc        ca    }
    n $false
   retur
         ucceeded"n sactiout trans, blid accountor for inva erred"ExpectWrite-Error      n)
       To-JsovertonData | ConctiransainvalidT ($ers -Bodyaders $headt -HePos -Method                it" `
/deposionsansact/trceUrl/apitionServi$Transac-Uri "d thoestMe-Rnvoke = Iult  $res           {
     try     
   
          } test"
 ccountid avalon = "Inripti     desc    
   nt = 100.00  amou         "999999"
 ccountId =  a
           ta = @{ansactionDalidTr       $invaunt
 ent acco non-existn with: Transactiost 1 # Te      
 
        enarios..."nt scd accouvaliting ine-Step "Tes  Writ       try {
  
    
 n"
    }cation/jso" = "applitent-Type"Con
         $token"rer = "Beazation"horiut    "A  {
  = @  $headers n
  -TestJwtTokeken = Get  $to
    
  Scenarios"r Handling  Erro "Testingdere-Heait Wrrios {
   nangScendliHast-Error
function Te
}
se
    }urn $fal        ret $false
tWorkflow =inessAccounusflows.BoEndWorksults.EndT$TestRe"
        essage)xception.M_.Eailed: $($ow test frkflt wooun accusinesse-Error "B     Writatch {
      }
    c
   }    
  alseturn $f re           = $false
low countWorkfs.BusinessAckflowEndToEndWortResults.es          $Td"
  action failes transrge businesError "Larite-    W     else {
         } 
  uereturn $tr       
     uerkflow = $trntWossAccousinekflows.BundToEndWortResults.Ees    $T        
cessfully"ed sucpletnsaction comss trasineLarge bue-Success " Writ           TED") {
"COMPLEtus -eq sult.staonRegeTransacti   if ($lar   
     
     tTo-Json)| Convera ctionDatrgeTransas -Body ($laerrs $headHeadethod Post -      -Me     " `
 s/depositctioni/transaerviceUrl/apransactionSUri "$TRestMethod -nvoke-lt = IctionResusaTranlarge        $     
     }
   ion"
   transactge business tion = "Larscrip    de        00
= 15000.t moun  a     g()
     .ToStrinunt.idcco$businessAountId =         acc= @{
    nData nsactioTra    $largent)
    ness accoubusik for uld worction (shosaranTest large t
        # 
        count.id)"Acsiness - ID: $($bureated cccountss a"Busine-Success Write            
)
    ertTo-JsontData | Convody ($accoun-Bs $headers Headerst - -Method Po         
  counts" `Url/api/acountServiceri "$Acc -URestMethodt = Invoke-sinessAccoun
        $bu}
        e
        lanca.InitialBabusinessDatance = $initialBal        tType
    ccounsData.AsinestType = $bucoun   ac         UserId
Data.businessId = $   owner     
     @{a =$accountDat        sUser
usines$TestUsers.B= sinessData         $bu 
     ts..."
  mih high liount witss accating businep "CreWrite-Ste{
        y   
    tr
    }
  tion/json"plica" = "apt-Type  "Conten
      r $token"" = "Beareization"Author
        aders = @{  $heoken
  tJwtTet-Tesoken = G   
    $tow"
 kflcount Wor Business Acsting"Tee-Header {
    WritntWorkflow inessAccoust-Busion Tect

fun   }
}n $false
 ur    retfalse
     $rney =rJouses.CompleteUEndWorkflowndTo.EestResults$T
        Message)"_.Exception.ed: $($ily test fauser journeomplete ror "CWrite-Er
        atch {
    c  }  e
tru  return $      y = $true
UserJourneomplete.CwsToEndWorkflotResults.End     $Tes
       nce)"
    ccount.balayFinalA: $($verl balancefied - Finariney veuser jour "Complete e-Success Writ           }
$false
      return     e)"
      ount.balancinalAccl: $($veryFctuaance, AectedBalFinalExpcted: $veryxpeiled - Eation faficbalance verifinal ror "Very   Write-Er          0.01) {
 ce) -gtectedBalanlExp - $veryFinancecount.balaveryFinalAcmath]::Abs($     if ([   0
0.0ance1 - 20ectedBal$expce = ctedBalanpeveryFinalEx
        $  
      derss $heaeaderod Get -H  -Meth      `
    Id" accountnts/$oul/api/accUriceuntServco -Uri "$ActhodstMeoke-ReInvunt = ccolAnaFi    $very  lance
  baal account fy finStep 9: Veri   # 
     
        und"s fotionsact) tranounontent.CnHistory.csactioed - $($tranverifi history "Transactionte-Success ri
        W
        }sefalrn $   retu    "
     ent.Count)y.contonHistortiansactrnd: $($actions, fou transat least 3ected lete - Expcomptory intion hisTransacr "te-Erro      Wri     lt 3) {
  -tent.Countistory.conionHf ($transact
        i       
 rs $headerset -Headeod GMeth  -       
   d" `ountInt/$accions/accoui/transactl/apionServiceUr$Transactd -Uri "-RestMethoy = InvokenHistoriosact     $tranhistory
   ransaction ck the: Cp 8# Ste 
               ionId)"
ult.transactwalResrahd ID: $($wittion Transac -pletedawal com"Withdress e-Succ    Writ      }
    
  se $falreturn       "
     failedhdrawal  "Witrror    Write-E
        ) {OMPLETED"us -ne "Ct.statwalResul($withdra     if        
 )
   ononvertTo-Jsa | ChdrawalDatitrs -Body ($wde $heast -Headersd Po -Metho         raw" `
  ithds/wtiontransacl/api/viceUronSer$Transactihod -Uri "estMet= Invoke-Rlt suithdrawalRe
        $w      }
   "
       ithdrawal = "ATM wcription des   00
        200.   amount =       ()
   ngStriuntId.ToaccoccountId = $          aata = @{
  thdrawalD        $wiwithdrawal
 Perform p 7: Ste    #    
    
    balance)"lAccount2.($fina 2: $ccountalance), AAccount1.bnalnt 1: $($fiAccoued - verifis  balancenal"Fiuccess ite-S Wr       }
    
    sen $fal   retur         "
ce)nt2.balanlAccouna$($fiActual: nce2, edBalactted: $expent 2 - Expecror "Accourite-Er     W)"
       ncebalat1.Accounall: $($finActuaance1, Balcted$expexpected: ccount 1 - E "Ae-Error  Writ        "
  ailedtion frificabalance ver "Final Erro   Write-      {
   gt 0.01) ce2) -xpectedBalanalance - $ealAccount2.bbs($finmath]::A     [or 
       .01 -ce1) -gt 0pectedBalanlance - $ex1.bantalAccous($fin ([math]::Ab     if
        0
   00.0 + 3Balancea.InitialcondUserDat $sealance2 =pectedB   $ex0
     - 300.0edBalance ectance1 = $expedBalpect       $ex 
   s
      $headerHeadersd Get - -Metho  
         tId" `condAccoununts/$sei/accoviceUrl/apntSerAccou-Uri "$RestMethod  = Invoke-lAccount2   $fina    
 seradHeaders $heGet -od eth-M           untId" `
 accoccounts/$api/aUrl/viceccountSeri "$AUrd -RestMethonvoke-ount1 = IlAccna       $fi
 nt balances accou Verify both Step 6:   # 
        "
    actionId)sult.transferRe: $($transaction IDTranseted - nsfer complraess "T  Write-Succ             }
rn $false
     retu       
  failed" "Transfere-Errorit        Wr    TED") {
COMPLEatus -ne "ult.stRes$transfer    if (    
       tTo-Json)
 ata | ConvertransferDs -Body ($ers $headereadst -H  -Method Po      
    " `nsferns/tractioransaUrl/api/tionServiceransact"$Td -Uri Methovoke-Restult = InerResansf        $tr 

               }ount"
remium acc pnsfer to "Traption =  descri         = 300.00
     amount      ring()
   oStcountId.TecondAc $sntId =cou toAc         
  g()ntId.ToStrintId = $accouun    fromAcco@{
        ferData =   $trans     ccounts
 etween ar bansfe Perform tr # Step 5:
              
 ntId"couD: $secondAc- Iunt created Second accocess "e-Suc    Writ
    count.idndAcsecod = $countIecondAc   $s
             Json)
 ConvertTo-ata |ountDsecondAccs -Body ($ers $headert -Headethod Pos     -M   ts" `
    pi/accounl/atServiceUrcoun -Uri "$AcRestMethodnvoke-nt = IcousecondAc      $        
      }
   lance
 Baialta.InitndUserDa$secoalBalance =        initipe
     ntTyrData.Accou$secondUse= pe  accountTy       
    rIda.UserDatUse$secondId = owner        
     = @{countData  $secondAcr
      Useiumsers.PremtUa = $TescondUserDat $se    g
   stiner tensfr tra account foreate second: Cp 4       # Ste     
 
   t.balance)"ccounpdatedAlance: $($uNew baied - alance verift bccoun"Ae-Success  Writ       

        } $false   return       "
  t.balance)Accoun$updated Actual: $(ance,edBalexpect $ - Expected: failedificationlance ver-Error "Ba   Write{
         0.01) lance) -gt tedBance - $expecbaladAccount.s($updateAb]::th[ma     if (
   00 500.lBalance +.Initia= $userDatalance dBa$expecte        
     
   $headersrs et -Headeod G -Meth       " `
    Idccountccounts/$api/arl/aountServiceUAcc"$i UrstMethod --Re InvoketedAccount =       $upda
 ce updateount balan Verify accStep 3:        #   
"
      d)nsactionItResult.tra: $($deposisaction ID - Trantedposit comple "First deite-SuccessWr
         }   $false
       return          ailed"
osit f deprror "First     Write-E) {
       PLETED""COMstatus -ne tResult.f ($deposi
        i     )
   vertTo-Json | ConpositDataody ($de$headers -Bders d Post -HeaMetho     -     t" `
  ons/deposi/transactiiceUrl/apinsactionServ "$TraMethod -UriestInvoke-RsitResult =        $depo
        
       } bonus"
  meit - Welcot deposon = "Firs   descripti0
         ount = 500.0 am
           String()untId.TontId = $acco       accou= @{
     ta $depositDat
        st deposiirPerform f2: ep      # St       
   ance)"
 balnt.ccoulance: $($antId, BaID: $accoud - createunt "User accoess rite-Succd
        W $account.iccountId = $a        
            }
 lse
  rn $fa        retu   count"
  acereate used to crrror "Failte-E  Wri          unt.id) {
t $accono(- if          
   o-Json)
   rtTta | ConveuntDa$accoody ( $headers -B -Headersod Posteth     -M       s" `
/accountviceUrl/apiountSerUri "$Accthod -RestMenvoke-ccount = I   $a          
  }
   
      alBalanceata.InitierDce = $ustialBalan     ini
       .AccountType $userDatae =accountTyp          serId
  ata.UserDId = $u   owner        a = @{
 tDatccoun        $adardUser
Stan $TestUsers.serData =  $uunt
      acconew user reate a ep 1: C # St 
            "
  ansactionst Trnd Firion aeat Account Cr New Userurney 1: "Jote-Step
        Wriry { 
    t
    }
   tion/json"lica" = "appntent-Type   "Co
      $token"" = "Bearerhorization"Aut    s = @{
        $headertToken
stJwt-Te$token = Ge 
    "
   er Journeyplete UsTesting Comte-Header "    Wriey {
JournleteUserst-Compon Teuncti
f
}
    }urn $false
      ret)"
  geeption.Messa($_.Excd: $t faileon tesintegrati"Service ite-Error      Wr{
    catch 
    }
   turn $true     re
                 }

        }     $false
  ation =nicceCommuation.ServintegrviceItResults.Ser $Tes               
Message)"xception.$($_.Efailed: ation communic-to-service ervice-Error "S       Write          catch {
              }
            }
   
          $falsecation = rviceCommunition.SeIntegraServiceTestResults.     $          "
     atus)stResult.$($deposit status:  withbutcompleted saction Tran-Warning "Write                   e {
  } els         
      ion = $trueommunicaterviceCntegration.Ss.ServiceIestResult $T            "
       mpletedn co Transactio -ngtion workicommunicarvice -to-ses "Servicete-SuccesWri                  ") {
  LETEDq "COMPtus -etResult.staeposi      if ($d            
              son)
vertTo-JCona | ositDatBody ($dep $headers -eadersthod Post -H    -Me              
  eposit" `nsactions/d/api/trarviceUrlctionSe$Transaod -Uri "stMeth-Reult = Invoke $depositRes           y {
           tr
                    }
       "
  t depositesration tn = "Integdescriptio                nt = 50.00
   amou    
         ring()Std.TotAccount.id = $tesuntI      acco          Data = @{
itpos       $de
     erviceth Account Smunicate wi comcanice saction Serv if Tran # Now test           
           
  $trueerviceAPI =countSon.AcIntegraticervis.Sesult $TestRe           d)"
t.itestAccouncreated: $($Account orking - I wService APccount  "Acessrite-Suc   W       ) {
  ount.idif ($testAcc 
               Json)
ertTo- | ConvntDataccou($testAy Bodheaders - -Headers $d Post  -Metho
          unts" `Url/api/accotServicecounri "$ActMethod -U Invoke-Resount =cc   $testA
     }
           0
     ce = 100.0alBalaniti   in"
         RD = "STANDAaccountType    "
        -test-useronti"integra  ownerId = 
           @{ountData =cc    $testAvice
    ction Ser Transaible from access isServiceccount erify At to voune a test acc Creat  #  
            s..."
ineson readcatie communio-servicg service-tstin"Te-Step     Writess
    on readinemunicatirvice com-se-to 3: Servicest     # Te      
   }
  
        uelth = $trviceHeaSerTransactionation.rviceIntegrults.SeTestRes        $ng"
     workith endpointe healion ServicTransactss "e-Succe  Writ      ") {
    eq "UPth.status -nHealio($transact    if    
  -Method Gethealth"tor/iceUrl/actuationServransac "$TritMethod -Uke-Res= InvoionHealth nsactra       $t 
 ."
       endpoints..ervice ransaction Ssting T"Terite-Step        Wdpoints
 n Service en: Transactioest 2    # T
         }
       rue
    alth = $tHerviceountSegration.AccrviceIntelts.SetResues$T           rking"
 int wopoealth endService hs "Account rite-Succes     W       ") {
 "UP-eqh.status ltountHea if ($accet
       " -Method Gor/healthactuatceUrl/ntServii "$Accou-Ur-RestMethod nvokealth = ItHe$accoun        
        
ints..."ice endpoServng Account tep "Testi     Write-Sints
   ce endpont Serviou: Acc    # Test 1ry {
     t  
   
    }
  on/json"atilic"app = ent-Type" "Cont       oken"
$tearer tion" = "Biza "Author      @{
   =ders$heaen
    tTok= Get-TestJw   $token  
 ation"
   ntegre Ing Servicr "Testite-Heade
    Wri {ontintegrat-ServiceItion Tesfunce"
}

aturxample-signDM2MDAwfQ.eIjoxNzMzNwiZXhwwMCQzMjQMzTczI6MImlhdCVNFUiIsfVTEVcyI6IlJPdXRob3JpdGllRVIiLCJhSI6IlVTicm9sZVyIiwGVzdC11c2IiOiJlMmUtdCJ9.eyJzdWR5cCI6IkpXV1NiIsInbGciOiJIUzIJhurn "eynt
    ret environme the testvalid forken is   # This to  secret
 our JWT hesmatc token that generated pre-e'll use aurposes, w pr testing  # Fo   
  -Compress
 n onvertTo-Jso    } | C) + 3600
-UFormat %s)e((Get-Date e]::Parsoublint][d exp = [        %s))
ate -UFormat((Get-D:Parse]:t][doublet = [inia"
        ER"ROLE_USorities = auth"
        "USER    role = er"
    "e2e-test-ussub = @{
        $payload = 
    
    pressomon -CTo-JsnvertCo  } | WT"
    typ = "J   256"
   HS    alg = "= @{
    eader  $hservice
   ication  an authentd come fromthis woulario, l scenea r a Inion
    #uthenticaten for a JWT tokate a test   # GenertToken {
 n Get-TestJwunctio
f
}
}false
    rn $    retu
    .Message)"$_.Exception: $(ledh check fairror "Healte-E        Writ
catch {  
     }e
 n $tru      retur 
      
       }    $true
 eRedis =icnServtioacansartup.TreStts.Servicul    $TestRes     lthy"
   eation is h connecce Redison Servi"Transacti-Success ite   Wr       {
   q "UP")s -eredis.statuomponents.Health.ctransaction($        if     }
    DB = $true
erviceionSransacteStartup.Trvicsults.SeestRe $T         ealthy"
  nection is hase convice databion SerTransact "rite-Success    W {
        q "UP")tatus -eponents.db.sealth.comionHctsa$tranf (  i      d Get
h" -Methohealtor/rl/actuatceUerviactionSri "$TransestMethod -U Invoke-RonHealth =tisac     $tran   ections
connedis se and Rbace dataaction Servick TransChe#             
      }
     rue
 $trviceDB = ountSeeStartup.Accsults.Servic$TestRe        
    thy"ealtion is hecbase connService data"Account cess -Sucte      Wri{
      ) -eq "UP"tus ents.db.sta.componountHealth($accif      hod Get
    -Methealth"or/rl/actuaticeUountServ "$Accethod -Urioke-RestMnvtHealth = Iaccoun $on
       se connectitabat Service dack Accoun# Che {
        ry
    t
    hecks..." chealthd ng detailePerformiStep "rite-s
    W checknal health# Additio
    
       }
         }
rn $false     retu        {
-not $ready)        if (
        

        }-Seconds 3p art-Slee         St
   s)"triet $i/$maxReempame)... (att($service.Niting for $e-Info "Wa       Writ        
    
       }             }
           
  urn $false      ret     
         se$falce.Key] = tup[$serviarceStvitResults.Ser $Tes            
       eout" within timtart failed to se)Name.$servicr "$(rite-Erro          W       
   ) {trieseq $maxRe$i - ( if            {
    catch 
              }         }
             ak
           bre            rue
eady = $t       $r         
    ueKey] = $tre.vicsertartup[$erviceStResults.STes      $          "
    Retries)i/$maxmpt $tedy (atame) is rea($service.Ness "$ucc-SWrite             {
       q "UP") status -eesponse.    if ($r          outSec 5
  Timethod Get -ce.Url -Me $servihod -Urie-RestMet= Invok $response             
      try {        
  {+); $i+tries$maxRe-le $i $i = 1;  (  for  
    se
        ready = $fal  $
      intervalsh 3-second s wit 3 minutes = 60  #etrie      $maxR 
        ."
  be ready..ame) toice.Nr $($serv"Waiting fotep   Write-S) {
      vicese in $ser($servicforeach 
         )
   
Service" }ion= "TransactKey ealth"; /horl/actuatrviceUrctionSeansa= "$Tr Url n Service";"Transactio{ Name =         @ce" }
ccountServi"; Key = "Ahhealtor/Url/actuatuntServicerl = "$Acco Uice";ccount Servame = "A      @{ N= @(
  $services       
 Ready"
   to beesr Servicfoting "Waie-Header  {
    WritrvicesReadyit-ForSeunction Wa
}

f
    }turn $false    rese
    $fald = uilviceBtructure.SernfrasestResults.I$T