#!/bin/bash

# Repository Setup Script for Account Service CI/CD Pipeline
# This script helps configure the GitHub repository with proper settings

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}[SETUP]${NC} $1"
}

# Check if GitHub CLI is installed
check_gh_cli() {
    if ! command -v gh &> /dev/null; then
        print_error "GitHub CLI (gh) is not installed. Please install it first:"
        print_error "https://cli.github.com/"
        exit 1
    fi
    
    # Check if user is authenticated
    if ! gh auth status &> /dev/null; then
        print_error "Please authenticate with GitHub CLI first:"
        print_error "gh auth login"
        exit 1
    fi
    
    print_status "GitHub CLI is installed and authenticated"
}

# Get repository information
get_repo_info() {
    if [ -z "$GITHUB_REPOSITORY" ]; then
        REPO_OWNER=$(gh repo view --json owner --jq '.owner.login' 2>/dev/null || echo "")
        REPO_NAME=$(gh repo view --json name --jq '.name' 2>/dev/null || echo "")
        
        if [ -z "$REPO_OWNER" ] || [ -z "$REPO_NAME" ]; then
            print_error "Could not determine repository information. Please run this script from a Git repository."
            exit 1
        fi
        
        GITHUB_REPOSITORY="$REPO_OWNER/$REPO_NAME"
    else
        REPO_OWNER=$(echo "$GITHUB_REPOSITORY" | cut -d'/' -f1)
        REPO_NAME=$(echo "$GITHUB_REPOSITORY" | cut -d'/' -f2)
    fi
    
    print_status "Repository: $GITHUB_REPOSITORY"
}

# Enable security features
enable_security_features() {
    print_header "Enabling Security Features"
    
    # Enable vulnerability alerts
    gh api -X PUT "repos/$GITHUB_REPOSITORY/vulnerability-alerts" || print_warning "Could not enable vulnerability alerts"
    
    # Enable automated security updates
    gh api -X PUT "repos/$GITHUB_REPOSITORY/automated-security-fixes" || print_warning "Could not enable automated security fixes"
    
    print_status "Security features enabled"
}

# Configure branch protection
configure_branch_protection() {
    print_header "Configuring Branch Protection"
    
    # Main branch protection
    print_status "Setting up main branch protection..."
    
    cat > /tmp/main-protection.json << EOF
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "validate / Code Validation",
      "security-scan / Security Scanning",
      "pr-validation / PR Validation"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 2,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true,
    "require_last_push_approval": true
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true
}
EOF
    
    gh api -X PUT "repos/$GITHUB_REPOSITORY/branches/main/protection" \
        --input /tmp/main-protection.json || print_warning "Could not set main branch protection"
    
    # Develop branch protection (if exists)
    if gh api "repos/$GITHUB_REPOSITORY/branches/develop" &> /dev/null; then
        print_status "Setting up develop branch protection..."
        
        cat > /tmp/develop-protection.json << EOF
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "pr-validation / PR Validation",
      "pr-security-scan / PR Security Scan"
    ]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
        
        gh api -X PUT "repos/$GITHUB_REPOSITORY/branches/develop/protection" \
            --input /tmp/develop-protection.json || print_warning "Could not set develop branch protection"
    fi
    
    print_status "Branch protection configured"
}

# Create environments
create_environments() {
    print_header "Creating Environments"
    
    # Development environment
    print_status "Creating development environment..."
    gh api -X PUT "repos/$GITHUB_REPOSITORY/environments/development" \
        --field wait_timer=0 \
        --field prevent_self_review=false || print_warning "Could not create development environment"
    
    # Staging environment
    print_status "Creating staging environment..."
    cat > /tmp/staging-env.json << EOF
{
  "wait_timer": 0,
  "prevent_self_review": true,
  "reviewers": [
    {
      "type": "Team",
      "id": "devops-team"
    }
  ]
}
EOF
    
    gh api -X PUT "repos/$GITHUB_REPOSITORY/environments/staging" \
        --input /tmp/staging-env.json || print_warning "Could not create staging environment"
    
    # Production environment
    print_status "Creating production environment..."
    cat > /tmp/production-env.json << EOF
{
  "wait_timer": 300,
  "prevent_self_review": true,
  "reviewers": [
    {
      "type": "Team",
      "id": "devops-team"
    },
    {
      "type": "Team",
      "id": "security-team"
    }
  ]
}
EOF
    
    gh api -X PUT "repos/$GITHUB_REPOSITORY/environments/production" \
        --input /tmp/production-env.json || print_warning "Could not create production environment"
    
    print_status "Environments created"
}

# Configure repository settings
configure_repository_settings() {
    print_header "Configuring Repository Settings"
    
    # Update repository settings
    gh api -X PATCH "repos/$GITHUB_REPOSITORY" \
        --field has_issues=true \
        --field has_projects=true \
        --field has_wiki=false \
        --field allow_squash_merge=true \
        --field allow_merge_commit=false \
        --field allow_rebase_merge=true \
        --field delete_branch_on_merge=true \
        --field allow_auto_merge=true || print_warning "Could not update repository settings"
    
    print_status "Repository settings configured"
}

# Validate setup
validate_setup() {
    print_header "Validating Setup"
    
    # Check if workflows exist
    if [ -f ".github/workflows/ci-cd-pipeline.yml" ]; then
        print_status "✅ Main CI/CD workflow found"
    else
        print_error "❌ Main CI/CD workflow not found"
    fi
    
    if [ -f ".github/workflows/pr-validation.yml" ]; then
        print_status "✅ PR validation workflow found"
    else
        print_error "❌ PR validation workflow not found"
    fi
    
    if [ -f ".github/CODEOWNERS" ]; then
        print_status "✅ CODEOWNERS file found"
    else
        print_error "❌ CODEOWNERS file not found"
    fi
    
    # Check branch protection
    if gh api "repos/$GITHUB_REPOSITORY/branches/main/protection" &> /dev/null; then
        print_status "✅ Main branch protection configured"
    else
        print_warning "⚠️ Main branch protection not configured"
    fi
    
    print_status "Setup validation completed"
}

# Display next steps
show_next_steps() {
    print_header "Next Steps"
    
    echo ""
    echo "Repository setup is complete! Here are the next steps:"
    echo ""
    echo "1. 🔐 Configure Secrets:"
    echo "   - Go to Settings > Secrets and variables > Actions"
    echo "   - Add required secrets (see .github/README.md for list)"
    echo ""
    echo "2. 👥 Update CODEOWNERS:"
    echo "   - Edit .github/CODEOWNERS file"
    echo "   - Replace placeholder team names with actual GitHub teams"
    echo ""
    echo "3. 🔧 Configure External Integrations:"
    echo "   - Set up Slack/Discord webhooks"
    echo "   - Configure SonarCloud project"
    echo "   - Set up monitoring tools"
    echo ""
    echo "4. 🧪 Test the Pipeline:"
    echo "   - Create a test branch and PR"
    echo "   - Verify all workflows run correctly"
    echo "   - Check security scans and quality gates"
    echo ""
    echo "5. 📚 Review Documentation:"
    echo "   - Read .github/README.md for detailed information"
    echo "   - Review .github/repository-settings.md for configuration details"
    echo ""
    echo "For more information, see: .github/README.md"
}

# Main execution
main() {
    print_header "Account Service CI/CD Repository Setup"
    echo ""
    
    check_gh_cli
    get_repo_info
    
    echo ""
    print_status "Starting repository setup for $GITHUB_REPOSITORY"
    echo ""
    
    enable_security_features
    configure_branch_protection
    create_environments
    configure_repository_settings
    validate_setup
    
    echo ""
    show_next_steps
    
    print_status "Repository setup completed successfully!"
}

# Cleanup function
cleanup() {
    rm -f /tmp/main-protection.json
    rm -f /tmp/develop-protection.json
    rm -f /tmp/staging-env.json
    rm -f /tmp/production-env.json
}

# Set up cleanup trap
trap cleanup EXIT

# Run main function
main "$@"