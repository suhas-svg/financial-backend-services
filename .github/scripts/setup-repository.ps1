# Repository Setup Script for Account Service CI/CD Pipeline (PowerShell)
# This script helps configure the GitHub repository with proper settings

param(
    [string]$Repository = $env:GITHUB_REPOSITORY
)

# Function to print colored output
function Write-Status {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Write-Header {
    param([string]$Message)
    Write-Host "[SETUP] $Message" -ForegroundColor Blue
}

# Check if GitHub CLI is installed
function Test-GitHubCLI {
    try {
        $null = Get-Command gh -ErrorAction Stop
        
        # Check if user is authenticated
        $authStatus = gh auth status 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Please authenticate with GitHub CLI first:"
            Write-Error "gh auth login"
            exit 1
        }
        
        Write-Status "GitHub CLI is installed and authenticated"
        return $true
    }
    catch {
        Write-Error "GitHub CLI (gh) is not installed. Please install it first:"
        Write-Error "https://cli.github.com/"
        exit 1
    }
}

# Get repository information
function Get-RepositoryInfo {
    if (-not $Repository) {
        try {
            $repoOwner = gh repo view --json owner --jq '.owner.login' 2>$null
            $repoName = gh repo view --json name --jq '.name' 2>$null
            
            if (-not $repoOwner -or -not $repoName) {
                Write-Error "Could not determine repository information. Please run this script from a Git repository."
                exit 1
            }
            
            $script:Repository = "$repoOwner/$repoName"
        }
        catch {
            Write-Error "Could not determine repository information. Please run this script from a Git repository."
            exit 1
        }
    }
    
    $script:RepoOwner = $Repository.Split('/')[0]
    $script:RepoName = $Repository.Split('/')[1]
    
    Write-Status "Repository: $Repository"
}

# Enable security features
function Enable-SecurityFeatures {
    Write-Header "Enabling Security Features"
    
    try {
        # Enable vulnerability alerts
        gh api -X PUT "repos/$Repository/vulnerability-alerts" 2>$null
        Write-Status "Vulnerability alerts enabled"
    }
    catch {
        Write-Warning "Could not enable vulnerability alerts"
    }
    
    try {
        # Enable automated security updates
        gh api -X PUT "repos/$Repository/automated-security-fixes" 2>$null
        Write-Status "Automated security fixes enabled"
    }
    catch {
        Write-Warning "Could not enable automated security fixes"
    }
    
    Write-Status "Security features configuration completed"
}

# Configure branch protection
function Set-BranchProtection {
    Write-Header "Configuring Branch Protection"
    
    # Main branch protection
    Write-Status "Setting up main branch protection..."
    
    $mainProtection = @{
        required_status_checks = @{
            strict = $true
            contexts = @(
                "validate / Code Validation",
                "security-scan / Security Scanning",
                "pr-validation / PR Validation"
            )
        }
        enforce_admins = $true
        required_pull_request_reviews = @{
            required_approving_review_count = 2
            dismiss_stale_reviews = $true
            require_code_owner_reviews = $true
            require_last_push_approval = $true
        }
        restrictions = $null
        allow_force_pushes = $false
        allow_deletions = $false
        block_creations = $false
        required_conversation_resolution = $true
    } | ConvertTo-Json -Depth 10
    
    try {
        $mainProtection | gh api -X PUT "repos/$Repository/branches/main/protection" --input -
        Write-Status "Main branch protection configured"
    }
    catch {
        Write-Warning "Could not set main branch protection: $_"
    }
    
    # Check if develop branch exists and configure protection
    try {
        gh api "repos/$Repository/branches/develop" 2>$null | Out-Null
        Write-Status "Setting up develop branch protection..."
        
        $developProtection = @{
            required_status_checks = @{
                strict = $true
                contexts = @(
                    "pr-validation / PR Validation",
                    "pr-security-scan / PR Security Scan"
                )
            }
            enforce_admins = $false
            required_pull_request_reviews = @{
                required_approving_review_count = 1
                dismiss_stale_reviews = $true
                require_code_owner_reviews = $true
            }
            restrictions = $null
            allow_force_pushes = $false
            allow_deletions = $false
        } | ConvertTo-Json -Depth 10
        
        $developProtection | gh api -X PUT "repos/$Repository/branches/develop/protection" --input -
        Write-Status "Develop branch protection configured"
    }
    catch {
        Write-Status "Develop branch not found or could not configure protection"
    }
}

# Create environments
function New-Environments {
    Write-Header "Creating Environments"
    
    # Development environment
    Write-Status "Creating development environment..."
    try {
        gh api -X PUT "repos/$Repository/environments/development" --field wait_timer=0 --field prevent_self_review=false
        Write-Status "Development environment created"
    }
    catch {
        Write-Warning "Could not create development environment: $_"
    }
    
    # Staging environment
    Write-Status "Creating staging environment..."
    $stagingEnv = @{
        wait_timer = 0
        prevent_self_review = $true
        reviewers = @(
            @{
                type = "Team"
                id = "devops-team"
            }
        )
    } | ConvertTo-Json -Depth 10
    
    try {
        $stagingEnv | gh api -X PUT "repos/$Repository/environments/staging" --input -
        Write-Status "Staging environment created"
    }
    catch {
        Write-Warning "Could not create staging environment: $_"
    }
    
    # Production environment
    Write-Status "Creating production environment..."
    $productionEnv = @{
        wait_timer = 300
        prevent_self_review = $true
        reviewers = @(
            @{
                type = "Team"
                id = "devops-team"
            },
            @{
                type = "Team"
                id = "security-team"
            }
        )
    } | ConvertTo-Json -Depth 10
    
    try {
        $productionEnv | gh api -X PUT "repos/$Repository/environments/production" --input -
        Write-Status "Production environment created"
    }
    catch {
        Write-Warning "Could not create production environment: $_"
    }
}

# Configure repository settings
function Set-RepositorySettings {
    Write-Header "Configuring Repository Settings"
    
    try {
        gh api -X PATCH "repos/$Repository" `
            --field has_issues=true `
            --field has_projects=true `
            --field has_wiki=false `
            --field allow_squash_merge=true `
            --field allow_merge_commit=false `
            --field allow_rebase_merge=true `
            --field delete_branch_on_merge=true `
            --field allow_auto_merge=true
        
        Write-Status "Repository settings configured"
    }
    catch {
        Write-Warning "Could not update repository settings: $_"
    }
}

# Validate setup
function Test-Setup {
    Write-Header "Validating Setup"
    
    # Check if workflows exist
    if (Test-Path ".github/workflows/ci-cd-pipeline.yml") {
        Write-Status "✅ Main CI/CD workflow found"
    }
    else {
        Write-Error "❌ Main CI/CD workflow not found"
    }
    
    if (Test-Path ".github/workflows/pr-validation.yml") {
        Write-Status "✅ PR validation workflow found"
    }
    else {
        Write-Error "❌ PR validation workflow not found"
    }
    
    if (Test-Path ".github/CODEOWNERS") {
        Write-Status "✅ CODEOWNERS file found"
    }
    else {
        Write-Error "❌ CODEOWNERS file not found"
    }
    
    # Check branch protection
    try {
        gh api "repos/$Repository/branches/main/protection" 2>$null | Out-Null
        Write-Status "✅ Main branch protection configured"
    }
    catch {
        Write-Warning "⚠️ Main branch protection not configured"
    }
    
    Write-Status "Setup validation completed"
}

# Display next steps
function Show-NextSteps {
    Write-Header "Next Steps"
    
    Write-Host ""
    Write-Host "Repository setup is complete! Here are the next steps:" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "1. 🔐 Configure Secrets:" -ForegroundColor White
    Write-Host "   - Go to Settings > Secrets and variables > Actions"
    Write-Host "   - Add required secrets (see .github/README.md for list)"
    Write-Host ""
    Write-Host "2. 👥 Update CODEOWNERS:" -ForegroundColor White
    Write-Host "   - Edit .github/CODEOWNERS file"
    Write-Host "   - Replace placeholder team names with actual GitHub teams"
    Write-Host ""
    Write-Host "3. 🔧 Configure External Integrations:" -ForegroundColor White
    Write-Host "   - Set up Slack/Discord webhooks"
    Write-Host "   - Configure SonarCloud project"
    Write-Host "   - Set up monitoring tools"
    Write-Host ""
    Write-Host "4. 🧪 Test the Pipeline:" -ForegroundColor White
    Write-Host "   - Create a test branch and PR"
    Write-Host "   - Verify all workflows run correctly"
    Write-Host "   - Check security scans and quality gates"
    Write-Host ""
    Write-Host "5. 📚 Review Documentation:" -ForegroundColor White
    Write-Host "   - Read .github/README.md for detailed information"
    Write-Host "   - Review .github/repository-settings.md for configuration details"
    Write-Host ""
    Write-Host "For more information, see: .github/README.md" -ForegroundColor Yellow
}

# Main execution
function Main {
    Write-Header "Account Service CI/CD Repository Setup"
    Write-Host ""
    
    Test-GitHubCLI
    Get-RepositoryInfo
    
    Write-Host ""
    Write-Status "Starting repository setup for $Repository"
    Write-Host ""
    
    Enable-SecurityFeatures
    Set-BranchProtection
    New-Environments
    Set-RepositorySettings
    Test-Setup
    
    Write-Host ""
    Show-NextSteps
    
    Write-Status "Repository setup completed successfully!"
}

# Run main function
try {
    Main
}
catch {
    Write-Error "Setup failed: $_"
    exit 1
}