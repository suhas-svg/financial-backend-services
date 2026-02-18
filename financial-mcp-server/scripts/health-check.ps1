# Health Check Script for MCP Financial Server (PowerShell)
param(
    [string]$Mode = "full",
    [string]$HostName = "localhost",
    [int]$Port = 8082,
    [int]$MetricsPort = 9090,
    [int]$Timeout = 10,
    [int]$Retries = 3,
    [string]$Environment = "development"
)

# Colors for output
function Write-Info { param($Message) Write-Host "[INFO] $Message" -ForegroundColor Blue }
function Write-Success { param($Message) Write-Host "[SUCCESS] $Message" -ForegroundColor Green }
function Write-Warning { param($Message) Write-Host "[WARNING] $Message" -ForegroundColor Yellow }
function Write-Error { param($Message) Write-Host "[ERROR] $Message" -ForegroundColor Red }

function Test-Endpoint {
    param(
        [string]$Url,
        [string]$Description,
        [int]$ExpectedStatus = 200
    )
    
    Write-Info "Checking $Description..."
    
    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $Timeout
            
            if ($response.StatusCode -eq $ExpectedStatus) {
                Write-Success "$Description is healthy (HTTP $($response.StatusCode))"
                return $true
            }
        }
        catch {
            $statusCode = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.value__ } else { "000" }
        }
        
        if ($attempt -eq $Retries) {
            Write-Error "$Description failed (HTTP $statusCode)"
            if ($_.Exception.Message) {
                Write-Error "Error: $($_.Exception.Message)"
            }
            return $false
        }
        
        Write-Warning "$Description check failed (attempt $attempt/$Retries), retrying..."
        Start-Sleep -Seconds 2
    }
    
    return $false
}

function Test-DetailedHealth {
    $baseUrl = "http://${HostName}:${Port}"
    $metricsUrl = "http://${HostName}:${MetricsPort}"
    
    Write-Info "Running detailed health check for MCP Financial Server"
    Write-Host "Environment: $Environment"
    Write-Host "Base URL: $baseUrl"
    Write-Host "Metrics URL: $metricsUrl"
    Write-Host "=============================================="
    
    $failedChecks = 0
    
    # Basic health endpoint
    if (-not (Test-Endpoint "$baseUrl/health" "Health endpoint")) {
        $failedChecks++
    }
    
    # Readiness check
    if (-not (Test-Endpoint "$baseUrl/ready" "Readiness endpoint")) {
        $failedChecks++
    }
    
    # Metrics endpoint
    if (-not (Test-Endpoint "$metricsUrl/metrics" "Metrics endpoint")) {
        $failedChecks++
    }
    
    # MCP protocol endpoint (if available)
    if (-not (Test-Endpoint "$baseUrl/mcp" "MCP protocol endpoint")) {
        Write-Warning "MCP protocol endpoint not available (this may be normal)"
    }
    
    return $failedChecks
}

function Test-Dependencies {
    Write-Info "Checking service dependencies..."
    
    $failedDeps = 0
    
    # Check Account Service
    $accountServiceUrl = $env:ACCOUNT_SERVICE_URL
    if (-not $accountServiceUrl) { $accountServiceUrl = "http://localhost:8080" }
    
    if (-not (Test-Endpoint "$accountServiceUrl/actuator/health" "Account Service")) {
        $failedDeps++
    }
    
    # Check Transaction Service
    $transactionServiceUrl = $env:TRANSACTION_SERVICE_URL
    if (-not $transactionServiceUrl) { $transactionServiceUrl = "http://localhost:8081" }
    
    if (-not (Test-Endpoint "$transactionServiceUrl/actuator/health" "Transaction Service")) {
        $failedDeps++
    }
    
    # Check Database (if accessible)
    $dbHost = $env:DB_HOST
    $dbPort = $env:DB_PORT
    if (-not $dbHost) { $dbHost = "localhost" }
    if (-not $dbPort) { $dbPort = 5432 }
    
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $tcpClient.Connect($dbHost, $dbPort)
        $tcpClient.Close()
        Write-Success "PostgreSQL database is accessible"
    }
    catch {
        Write-Error "PostgreSQL database is not accessible"
        $failedDeps++
    }
    
    # Check Redis (if configured)
    $redisUrl = $env:REDIS_URL
    if ($redisUrl) {
        try {
            # Parse Redis URL to get host and port
            $uri = [System.Uri]$redisUrl
            $tcpClient = New-Object System.Net.Sockets.TcpClient
            $tcpClient.Connect($uri.Host, $uri.Port)
            $tcpClient.Close()
            Write-Success "Redis is accessible"
        }
        catch {
            Write-Error "Redis is not accessible"
            $failedDeps++
        }
    }
    
    return $failedDeps
}

function Test-Performance {
    Write-Info "Running performance checks..."
    
    $baseUrl = "http://${HostName}:${Port}"
    
    try {
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $response = Invoke-WebRequest -Uri "$baseUrl/health" -UseBasicParsing -TimeoutSec $Timeout
        $stopwatch.Stop()
        
        $responseTimeMs = $stopwatch.ElapsedMilliseconds
        
        if ($responseTimeMs -lt 1000) {
            Write-Success "Response time: ${responseTimeMs}ms (good)"
        }
        elseif ($responseTimeMs -lt 3000) {
            Write-Warning "Response time: ${responseTimeMs}ms (acceptable)"
        }
        else {
            Write-Error "Response time: ${responseTimeMs}ms (slow)"
            return $false
        }
    }
    catch {
        Write-Error "Failed to measure response time: $_"
        return $false
    }
    
    return $true
}

function Test-Resources {
    Write-Info "Checking resource usage..."
    
    # Check if running in Docker
    try {
        $containerInfo = docker ps --filter "name=mcp-financial-server" --format "table {{.Names}}\t{{.Status}}" 2>$null
        if ($containerInfo) {
            Write-Info "Running inside Docker container"
            
            # Get container stats
            $stats = docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}" mcp-financial-server 2>$null
            if ($stats) {
                Write-Info "Container stats: $stats"
            }
        }
        else {
            Write-Info "Running on host system"
            
            # Get system information
            $memory = Get-WmiObject -Class Win32_OperatingSystem
            $totalMemoryGB = [math]::Round($memory.TotalVisibleMemorySize / 1MB, 2)
            $freeMemoryGB = [math]::Round($memory.FreePhysicalMemory / 1MB, 2)
            $usedMemoryGB = $totalMemoryGB - $freeMemoryGB
            
            Write-Info "System memory: ${usedMemoryGB}GB / ${totalMemoryGB}GB used"
            
            # Get disk information
            $disk = Get-WmiObject -Class Win32_LogicalDisk -Filter "DeviceID='C:'"
            $totalDiskGB = [math]::Round($disk.Size / 1GB, 2)
            $freeDiskGB = [math]::Round($disk.FreeSpace / 1GB, 2)
            $usedDiskGB = $totalDiskGB - $freeDiskGB
            
            Write-Info "Disk usage: ${usedDiskGB}GB / ${totalDiskGB}GB used"
        }
    }
    catch {
        Write-Warning "Could not retrieve resource information: $_"
    }
}

function New-HealthReport {
    param([int]$ExitCode)
    
    $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    $status = if ($ExitCode -eq 0) { "healthy" } else { "unhealthy" }
    
    $report = @{
        timestamp = $timestamp
        environment = $Environment
        status = $status
        host = $HostName
        port = $Port
        exit_code = $ExitCode
        checks = @{
            health_endpoint = ($ExitCode -eq 0)
            dependencies = ($ExitCode -eq 0)
            performance = ($ExitCode -eq 0)
        }
    }
    
    $reportJson = $report | ConvertTo-Json -Depth 3
    $reportPath = $env:HEALTH_REPORT_PATH
    
    if ($reportPath) {
        $reportJson | Out-File -FilePath $reportPath -Encoding UTF8
        Write-Info "Health report saved to: $reportPath"
    }
    else {
        $tempPath = Join-Path $env:TEMP "health_report.json"
        $reportJson | Out-File -FilePath $tempPath -Encoding UTF8
        Write-Info "Health report saved to: $tempPath"
    }
}

function Invoke-Main {
    switch ($Mode.ToLower()) {
        "basic" {
            Write-Info "Running basic health check..."
            $failures = Test-DetailedHealth
            exit $failures
        }
        "deps" {
            Write-Info "Checking dependencies only..."
            $failures = Test-Dependencies
            exit $failures
        }
        "perf" {
            Write-Info "Running performance check..."
            $success = Test-Performance
            exit $(if ($success) { 0 } else { 1 })
        }
        "full" {
            Write-Info "Running comprehensive health check..."
            Write-Host "=============================================="
            
            $totalFailures = 0
            
            # Run all checks
            $totalFailures += Test-DetailedHealth
            Write-Host ""
            
            $totalFailures += Test-Dependencies
            Write-Host ""
            
            if (-not (Test-Performance)) {
                $totalFailures++
            }
            Write-Host ""
            
            Test-Resources
            Write-Host ""
            
            # Generate report
            New-HealthReport -ExitCode $totalFailures
            
            if ($totalFailures -eq 0) {
                Write-Success "All health checks passed! 🎉"
                exit 0
            }
            else {
                Write-Error "Health check failed with $totalFailures issues"
                exit 1
            }
        }
        default {
            Write-Host "Usage: .\health-check.ps1 [-Mode {basic|deps|perf|full}]"
            Write-Host ""
            Write-Host "Modes:"
            Write-Host "  basic - Basic health endpoint check"
            Write-Host "  deps  - Check service dependencies"
            Write-Host "  perf  - Performance and response time check"
            Write-Host "  full  - Comprehensive health check (default)"
            Write-Host ""
            Write-Host "Parameters:"
            Write-Host "  -HostName     Server host (default: localhost)"
            Write-Host "  -Port         Server port (default: 8082)"
            Write-Host "  -MetricsPort  Metrics port (default: 9090)"
            Write-Host "  -Timeout      Request timeout (default: 10)"
            Write-Host "  -Retries      Number of retries (default: 3)"
            Write-Host "  -Environment  Environment name (default: development)"
            exit 1
        }
    }
}

# Run main function
Invoke-Main