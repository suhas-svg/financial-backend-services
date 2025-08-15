# ================================================
# Database Migration Management Script for Windows
# ================================================

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment,
    
    [Parameter(Mandatory=$true)]
    [ValidateSet("migrate", "info", "validate", "clean", "repair", "baseline", "init-db", "validate-migration")]
    [string]$Action,
    
    [string]$DatabaseHost = "",
    [string]$DatabasePort = "",
    [string]$DatabaseName = "",
    [string]$DatabaseUser = "",
    [string]$DatabasePassword = "",
    [switch]$Force = $false,
    [switch]$Verbose = $false
)

# Set error action preference
$ErrorActionPreference = "Stop"

# Script configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$MavenWrapper = Join-Path $ProjectRoot "mvnw.cmd"

# Environment-specific configurations
$EnvironmentConfig = @{
    "dev" = @{
        Host = "localhost"
        Port = "5433"
        Database = "transactiondb_dev"
        User = "postgres"
        Password = "postgres"
        Profile = "dev"
        InitScript = "init-db-dev.sql"
    }
    "staging" = @{
        Host = "staging-db"
        Port = "5432"
        Database = "transactiondb_staging"
        User = "transaction_staging_user"
        Password = "staging_secure_password"
        Profile = "staging"
        InitScript = "init-db-staging.sql"
    }
    "prod" = @{
        Host = "prod-db"
        Port = "5432"
        Database = "transactiondb"
        User = "transaction_app_user"
        Password = $env:FLYWAY_PASSWORD
        Profile = "prod"
        InitScript = "init-db-prod.sql"
    }
}

# Functions
function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $color = switch ($Level) {
        "ERROR" { "Red" }
        "WARNING" { "Yellow" }
        "SUCCESS" { "Green" }
        default { "White" }
    }
    Write-Host "[$timestamp] [$Level] $Message" -ForegroundColor $color
}

function Test-Prerequisites {
    Write-Log "Checking prerequisites..."
    
    # Check if Maven wrapper exists
    if (-not (Test-Path $MavenWrapper)) {
        Write-Log "Maven wrapper not found at: $MavenWrapper" "ERROR"
        exit 1
    }
    
    # Check if Java is available
    try {
        $javaVersion = & java -version 2>&1 | Select-String "version"
        Write-Log "Java found: $javaVersion"
    }
    catch {
        Write-Log "Java not found. Please install Java 22 or later." "ERROR"
        exit 1
    }
    
    # Check if PostgreSQL client is available (for init-db and validate-migration)
    if ($Action -in @("init-db", "validate-migration")) {
        try {
            $psqlVersion = & psql --version 2>&1
            Write-Log "PostgreSQL client found: $psqlVersion"
        }
        catch {
            Write-Log "PostgreSQL client (psql) not found. Please install PostgreSQL client tools." "ERROR"
            exit 1
        }
    }
    
    Write-Log "Prerequisites check completed" "SUCCESS"
}

function Get-DatabaseConfig {
    param([string]$Env)
    
    $config = $EnvironmentConfig[$Env]
    
    # Override with command line parameters if provided
    if ($DatabaseHost) { $config.Host = $DatabaseHost }
    if ($DatabasePort) { $config.Port = $DatabasePort }
    if ($DatabaseName) { $config.Database = $DatabaseName }
    if ($DatabaseUser) { $config.User = $DatabaseUser }
    if ($DatabasePassword) { $config.Password = $DatabasePassword }
    
    return $config
}

function Invoke-FlywayCommand {
    param([string]$Command, [hashtable]$Config)
    
    Write-Log "Executing Flyway command: $Command for environment: $Environment"
    
    $mavenArgs = @(
        "flyway:$Command"
        "-P$($Config.Profile)"
    )
    
    if ($Verbose) {
        $mavenArgs += "-X"
    }
    
    # Set environment variables for Flyway
    $env:FLYWAY_URL = "jdbc:postgresql://$($Config.Host):$($Config.Port)/$($Config.Database)"
    $env:FLYWAY_USER = $Config.User
    $env:FLYWAY_PASSWORD = $Config.Password
    
    try {
        Write-Log "Maven command: $MavenWrapper $($mavenArgs -join ' ')"
        & $MavenWrapper $mavenArgs
        
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Flyway command '$Command' completed successfully" "SUCCESS"
        } else {
            Write-Log "Flyway command '$Command' failed with exit code: $LASTEXITCODE" "ERROR"
            exit $LASTEXITCODE
        }
    }
    catch {
        Write-Log "Error executing Flyway command: $($_.Exception.Message)" "ERROR"
        exit 1
    }
}

function Initialize-Database {
    param([hashtable]$Config)
    
    Write-Log "Initializing database for environment: $Environment"
    
    $initScriptPath = Join-Path $ScriptDir $Config.InitScript
    
    if (-not (Test-Path $initScriptPath)) {
        Write-Log "Initialization script not found: $initScriptPath" "ERROR"
        exit 1
    }
    
    # Construct psql connection string
    $psqlArgs = @(
        "-h", $Config.Host
        "-p", $Config.Port
        "-U", $Config.User
        "-f", $initScriptPath
    )
    
    # Set password environment variable
    $env:PGPASSWORD = $Config.Password
    
    try {
        Write-Log "Running database initialization script: $($Config.InitScript)"
        & psql $psqlArgs
        
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Database initialization completed successfully" "SUCCESS"
        } else {
            Write-Log "Database initialization failed with exit code: $LASTEXITCODE" "ERROR"
            exit $LASTEXITCODE
        }
    }
    catch {
        Write-Log "Error running database initialization: $($_.Exception.Message)" "ERROR"
        exit 1
    }
    finally {
        # Clear password environment variable
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

function Invoke-MigrationValidation {
    param([hashtable]$Config)
    
    Write-Log "Running migration validation for environment: $Environment"
    
    $validationScriptPath = Join-Path $ScriptDir "validate-migration.sql"
    
    if (-not (Test-Path $validationScriptPath)) {
        Write-Log "Validation script not found: $validationScriptPath" "ERROR"
        exit 1
    }
    
    # Construct psql connection string
    $psqlArgs = @(
        "-h", $Config.Host
        "-p", $Config.Port
        "-U", $Config.User
        "-d", $Config.Database
        "-f", $validationScriptPath
    )
    
    # Set password environment variable
    $env:PGPASSWORD = $Config.Password
    
    try {
        Write-Log "Running migration validation script"
        & psql $psqlArgs
        
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Migration validation completed successfully" "SUCCESS"
        } else {
            Write-Log "Migration validation failed with exit code: $LASTEXITCODE" "ERROR"
            exit $LASTEXITCODE
        }
    }
    catch {
        Write-Log "Error running migration validation: $($_.Exception.Message)" "ERROR"
        exit 1
    }
    finally {
        # Clear password environment variable
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

function Show-Usage {
    Write-Host @"
Database Migration Management Script

Usage: .\manage-migrations.ps1 -Environment <env> -Action <action> [options]

Parameters:
  -Environment    Target environment (dev, staging, prod)
  -Action         Action to perform:
                  migrate           - Run database migrations
                  info             - Show migration status
                  validate         - Validate migrations
                  clean            - Clean database (dev only)
                  repair           - Repair migration checksums
                  baseline         - Baseline existing database
                  init-db          - Initialize database
                  validate-migration - Run migration validation script
  
  -DatabaseHost   Override database host
  -DatabasePort   Override database port
  -DatabaseName   Override database name
  -DatabaseUser   Override database user
  -DatabasePassword Override database password
  -Force          Force action (for destructive operations)
  -Verbose        Enable verbose output

Examples:
  .\manage-migrations.ps1 -Environment dev -Action migrate
  .\manage-migrations.ps1 -Environment prod -Action info
  .\manage-migrations.ps1 -Environment staging -Action validate
  .\manage-migrations.ps1 -Environment dev -Action clean -Force
  .\manage-migrations.ps1 -Environment dev -Action init-db
  .\manage-migrations.ps1 -Environment prod -Action validate-migration

Environment Variables:
  FLYWAY_PASSWORD - Required for production environment
"@
}

# Main execution
function Main {
    Write-Log "Starting database migration management for environment: $Environment, action: $Action"
    
    # Show usage if help is requested
    if ($Action -eq "help") {
        Show-Usage
        exit 0
    }
    
    # Check prerequisites
    Test-Prerequisites
    
    # Get database configuration
    $config = Get-DatabaseConfig -Env $Environment
    
    # Validate production password
    if ($Environment -eq "prod" -and -not $config.Password) {
        Write-Log "Production password not provided. Set FLYWAY_PASSWORD environment variable." "ERROR"
        exit 1
    }
    
    # Validate clean operation
    if ($Action -eq "clean" -and $Environment -ne "dev" -and -not $Force) {
        Write-Log "Clean operation is not allowed in $Environment environment without -Force flag" "ERROR"
        exit 1
    }
    
    # Execute action
    switch ($Action) {
        "init-db" {
            Initialize-Database -Config $config
        }
        "validate-migration" {
            Invoke-MigrationValidation -Config $config
        }
        default {
            Invoke-FlywayCommand -Command $Action -Config $config
        }
    }
    
    Write-Log "Database migration management completed successfully" "SUCCESS"
}

# Execute main function
try {
    Main
}
catch {
    Write-Log "Script execution failed: $($_.Exception.Message)" "ERROR"
    exit 1
}