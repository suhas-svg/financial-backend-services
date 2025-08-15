# Container Security Scanning Script for Account Service (PowerShell)
# Usage: .\security-scan.ps1 [ImageName] [options]

param(
    [string]$ImageName = "account-service:latest",
    [string]$OutputDir = "./security-reports",
    [bool]$FailOnCritical = $true,
    [bool]$FailOnHigh = $false,
    [int]$CriticalThreshold = 0,
    [int]$HighThreshold = 10,
    [switch]$Help = $false
)

# Function to write colored output
function Write-Status {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Function to show usage
function Show-Usage {
    Write-Host "Container Security Scanning Script for Account Service" -ForegroundColor Blue
    Write-Host ""
    Write-Host "Usage: .\security-scan.ps1 [options]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Parameters:" -ForegroundColor Yellow
    Write-Host "  -ImageName         Docker image to scan [default: account-service:latest]"
    Write-Host "  -OutputDir         Output directory for reports [default: ./security-reports]"
    Write-Host "  -FailOnCritical    Fail on critical vulnerabilities [default: true]"
    Write-Host "  -FailOnHigh        Fail on high vulnerabilities [default: false]"
    Write-Host "  -CriticalThreshold Maximum critical vulnerabilities allowed [default: 0]"
    Write-Host "  -HighThreshold     Maximum high vulnerabilities allowed [default: 10]"
    Write-Host "  -Help              Show this help message"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Green
    Write-Host "  .\security-scan.ps1 -ImageName account-service:v1.0.0"
    Write-Host "  .\security-scan.ps1 -OutputDir C:\temp\scans -ImageName my-image:latest"
    Write-Host "  .\security-scan.ps1 -FailOnHigh `$true -HighThreshold 5 -ImageName account-service:dev"
}

# Function to check if command exists
function Test-Command {
    param([string]$Command)
    return [bool](Get-Command $Command -ErrorAction SilentlyContinue)
}

# Function to install security tools
function Install-Tools {
    Write-Status "Checking and installing security scanning tools..."
    
    # Check if Trivy is installed
    if (-not (Test-Command "trivy")) {
        Write-Status "Installing Trivy..."
        # For Windows, we'll provide instructions since automatic installation is complex
        Write-Warning "Trivy not found. Please install Trivy manually:"
        Write-Host "1. Download from: https://github.com/aquasecurity/trivy/releases"
        Write-Host "2. Add to PATH"
        Write-Host "3. Or use: winget install Aqua.Trivy"
        return $false
    }
    
    # Check if Docker is available
    if (-not (Test-Command "docker")) {
        Write-Error "Docker not found. Please install Docker Desktop."
        return $false
    }
    
    Write-Success "Security tools are ready!"
    return $true
}

# Function to run Trivy scan
function Invoke-TrivyScan {
    param([string]$Image, [string]$OutputDir)
    
    Write-Status "Running Trivy vulnerability scan..."
    
    try {
        # Scan for vulnerabilities
        & trivy image --format json --output "$OutputDir/trivy-vulnerabilities.json" $Image
        & trivy image --format table --output "$OutputDir/trivy-vulnerabilities.txt" $Image
        
        # Scan for secrets
        & trivy image --scanners secret --format json --output "$OutputDir/trivy-secrets.json" $Image
        & trivy image --scanners secret --format table --output "$OutputDir/trivy-secrets.txt" $Image
        
        # Scan for misconfigurations
        & trivy image --scanners config --format json --output "$OutputDir/trivy-config.json" $Image
        & trivy image --scanners config --format table --output "$OutputDir/trivy-config.txt" $Image
        
        # Generate SARIF for GitHub integration
        & trivy image --format sarif --output "$OutputDir/trivy-results.sarif" $Image
        
        Write-Success "Trivy scan completed!"
        return $true
    } catch {
        Write-Error "Trivy scan failed: $_"
        return $false
    }
}

# Function to analyze base image
function Get-BaseImageInfo {
    param([string]$Image, [string]$OutputDir)
    
    Write-Status "Analyzing base image security..."
    
    try {
        $baseImage = docker inspect $Image --format='{{.Config.Image}}' 2>$null
        if ($baseImage) {
            "Base image: $baseImage" | Out-File -FilePath "$OutputDir/base-image-info.txt"
            
            # Scan base image
            & trivy image --format json --output "$OutputDir/base-image-scan.json" $baseImage 2>$null
            & trivy image --format table --output "$OutputDir/base-image-scan.txt" $baseImage 2>$null
        } else {
            "Could not determine base image" | Out-File -FilePath "$OutputDir/base-image-info.txt"
        }
        
        Write-Success "Base image analysis completed!"
        return $true
    } catch {
        Write-Warning "Base image analysis failed: $_"
        return $false
    }
}

# Function to parse scan results
function Get-ScanResults {
    param([string]$OutputDir)
    
    Write-Status "Parsing scan results..."
    
    $results = @{
        TrivyCritical = 0
        TrivyHigh = 0
        TrivyMedium = 0
        TrivyLow = 0
    }
    
    # Parse Trivy results
    $trivyFile = "$OutputDir/trivy-vulnerabilities.json"
    if (Test-Path $trivyFile) {
        try {
            $trivyData = Get-Content $trivyFile | ConvertFrom-Json
            $vulnerabilities = $trivyData.Results | ForEach-Object { $_.Vulnerabilities } | Where-Object { $_ -ne $null }
            
            $results.TrivyCritical = ($vulnerabilities | Where-Object { $_.Severity -eq "CRITICAL" }).Count
            $results.TrivyHigh = ($vulnerabilities | Where-Object { $_.Severity -eq "HIGH" }).Count
            $results.TrivyMedium = ($vulnerabilities | Where-Object { $_.Severity -eq "MEDIUM" }).Count
            $results.TrivyLow = ($vulnerabilities | Where-Object { $_.Severity -eq "LOW" }).Count
        } catch {
            Write-Warning "Failed to parse Trivy results: $_"
        }
    }
    
    Write-Success "Results parsed successfully!"
    return $results
}

# Function to generate summary report
function New-SecurityReport {
    param([string]$Image, [string]$OutputDir, [hashtable]$Results)
    
    Write-Status "Generating security report..."
    
    $reportFile = "$OutputDir/security-summary.md"
    $scanDate = Get-Date -Format "yyyy-MM-dd HH:mm:ss UTC"
    
    $reportContent = @"
# Container Security Scan Report

## Image Information
- **Image**: $Image
- **Scan Date**: $scanDate
- **Scanner Version**: Trivy (PowerShell scan)

## Vulnerability Summary

| Scanner | Critical | High | Medium | Low |
|---------|----------|------|--------|-----|
| Trivy   | $($Results.TrivyCritical) | $($Results.TrivyHigh) | $($Results.TrivyMedium) | $($Results.TrivyLow) |

## Security Assessment

"@

    if ($Results.TrivyCritical -gt 0) {
        $reportContent += "- 🚨 **CRITICAL ALERT**: $($Results.TrivyCritical) critical vulnerabilities found - Immediate action required!`n"
    }
    
    if ($Results.TrivyHigh -gt 5) {
        $reportContent += "- ⚠️ **HIGH PRIORITY**: $($Results.TrivyHigh) high vulnerabilities found - Consider updating dependencies`n"
    }
    
    if ($Results.TrivyCritical -eq 0 -and $Results.TrivyHigh -le 5) {
        $reportContent += "- ✅ **GOOD**: Security posture is acceptable`n"
    }
    
    $reportContent += @"

## Recommendations

1. **Immediate Actions**:
   - Review and address all critical vulnerabilities
   - Update base image to latest secure version
   - Scan dependencies for known vulnerabilities

2. **Best Practices**:
   - Implement regular security scanning in CI/CD pipeline
   - Use minimal base images (e.g., Alpine, Distroless)
   - Keep dependencies up to date
   - Follow principle of least privilege

3. **Monitoring**:
   - Set up automated vulnerability monitoring
   - Subscribe to security advisories for used components
   - Implement runtime security monitoring

## Files Generated

- `trivy-vulnerabilities.json/txt` - Trivy vulnerability scan results
- `trivy-secrets.json/txt` - Secret detection results
- `trivy-config.json/txt` - Configuration scan results
- `trivy-results.sarif` - SARIF format for GitHub integration

"@

    $reportContent | Out-File -FilePath $reportFile -Encoding UTF8
    Write-Success "Security report generated: $reportFile"
}

# Function to display results
function Show-Results {
    param([hashtable]$Results)
    
    Write-Host ""
    Write-Host "==================================" -ForegroundColor Cyan
    Write-Host "    SECURITY SCAN RESULTS" -ForegroundColor Cyan
    Write-Host "==================================" -ForegroundColor Cyan
    Write-Host ""
    
    $format = "{0,-12} {1,-10} {2,-8} {3,-8} {4,-8}"
    Write-Host ($format -f "Scanner", "Critical", "High", "Medium", "Low") -ForegroundColor White
    Write-Host "==================================================" -ForegroundColor Gray
    Write-Host ($format -f "Trivy", $Results.TrivyCritical, $Results.TrivyHigh, $Results.TrivyMedium, $Results.TrivyLow)
    Write-Host ""
    
    if ($Results.TrivyCritical -gt $CriticalThreshold) {
        Write-Error "CRITICAL: Found $($Results.TrivyCritical) critical vulnerabilities (threshold: $CriticalThreshold)"
    }
    
    if ($Results.TrivyHigh -gt $HighThreshold) {
        Write-Warning "HIGH: Found $($Results.TrivyHigh) high vulnerabilities (threshold: $HighThreshold)"
    }
    
    if ($Results.TrivyCritical -eq 0 -and $Results.TrivyHigh -le $HighThreshold) {
        Write-Success "Security scan passed all thresholds!"
    }
}

# Main execution
function Main {
    # Show help if requested
    if ($Help) {
        Show-Usage
        return 0
    }
    
    Write-Status "Starting container security scan for: $ImageName"
    
    # Create output directory
    if (-not (Test-Path $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    }
    
    # Check if image exists
    try {
        docker image inspect $ImageName | Out-Null
    } catch {
        Write-Error "Docker image '$ImageName' not found. Please build the image first."
        return 1
    }
    
    # Install required tools
    if (-not (Install-Tools)) {
        return 1
    }
    
    # Run security scans
    $scanSuccess = $true
    $scanSuccess = $scanSuccess -and (Invoke-TrivyScan $ImageName $OutputDir)
    $scanSuccess = $scanSuccess -and (Get-BaseImageInfo $ImageName $OutputDir)
    
    if (-not $scanSuccess) {
        Write-Error "One or more security scans failed"
        return 1
    }
    
    # Parse and display results
    $results = Get-ScanResults $OutputDir
    New-SecurityReport $ImageName $OutputDir $results
    Show-Results $results
    
    # Check thresholds and exit accordingly
    $exitCode = 0
    
    if ($FailOnCritical -and $results.TrivyCritical -gt $CriticalThreshold) {
        Write-Error "Scan failed: Critical vulnerabilities exceed threshold ($($results.TrivyCritical) > $CriticalThreshold)"
        $exitCode = 1
    }
    
    if ($FailOnHigh -and $results.TrivyHigh -gt $HighThreshold) {
        Write-Error "Scan failed: High vulnerabilities exceed threshold ($($results.TrivyHigh) > $HighThreshold)"
        $exitCode = 1
    }
    
    if ($exitCode -eq 0) {
        Write-Success "Security scan completed successfully!"
        Write-Status "Reports saved to: $OutputDir"
    } else {
        Write-Error "Security scan failed due to policy violations!"
    }
    
    return $exitCode
}

# Change to script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AccountServiceDir = Split-Path -Parent $ScriptDir
Push-Location $AccountServiceDir

try {
    $exitCode = Main
    exit $exitCode
} finally {
    Pop-Location
}