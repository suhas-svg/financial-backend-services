# Repository Configuration Guide

This document outlines the recommended repository settings for the Account Service CI/CD pipeline.

## Branch Protection Rules

### Main Branch Protection
Configure the following settings for the `main` branch:

#### General Settings
- ✅ Restrict pushes that create files larger than 100 MB
- ✅ Require branches to be up to date before merging
- ✅ Require status checks to pass before merging
- ✅ Require conversation resolution before merging

#### Required Status Checks
The following status checks must pass before merging:
- `validate / Code Validation`
- `security-scan / Security Scanning`
- `pr-validation / PR Validation`
- `pr-security-scan / PR Security Scan`
- `code-quality / Code Quality Check`

#### Pull Request Requirements
- ✅ Require pull request reviews before merging
- ✅ Require review from code owners
- ✅ Dismiss stale reviews when new commits are pushed
- ✅ Require review from at least 2 reviewers for security-related changes
- ✅ Restrict who can dismiss pull request reviews

#### Push Restrictions
- ✅ Restrict pushes to matching branches
- ✅ Allow force pushes: ❌ (disabled)
- ✅ Allow deletions: ❌ (disabled)

#### Administrative Settings
- ✅ Include administrators in these restrictions
- ✅ Allow specified actors to bypass required pull requests (emergency access only)

### Develop Branch Protection
Configure the following settings for the `develop` branch:

#### General Settings
- ✅ Require pull request reviews before merging
- ✅ Require status checks to pass before merging
- ✅ Require branches to be up to date before merging

#### Required Status Checks
- `pr-validation / PR Validation`
- `pr-security-scan / PR Security Scan`

## Repository Security Settings

### Security Features
- ✅ Enable vulnerability alerts
- ✅ Enable automated security updates
- ✅ Enable secret scanning
- ✅ Enable push protection for secrets
- ✅ Enable code scanning (CodeQL)

### Access Control
- ✅ Require two-factor authentication for all members
- ✅ Restrict repository creation
- ✅ Restrict repository deletion
- ✅ Restrict repository visibility changes

## Collaboration Settings

### Issues
- ✅ Enable issues
- ✅ Use issue templates
- ✅ Require issue assignment before closing

### Pull Requests
- ✅ Enable pull requests
- ✅ Use pull request templates
- ✅ Automatically delete head branches after merge
- ✅ Enable auto-merge

### Discussions
- ✅ Enable discussions for team collaboration

## Webhook Configuration

### Required Webhooks
1. **Slack Integration** (if using Slack)
   - Payload URL: `https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK`
   - Content type: `application/json`
   - Events: Push, Pull request, Issues, Deployment status

2. **Security Monitoring** (if using external security tools)
   - Events: Security advisory, Vulnerability alert

## Environment Configuration

### Development Environment
- **Protection rules**: None (automatic deployment)
- **Reviewers**: Not required
- **Wait timer**: 0 minutes

### Staging Environment
- **Protection rules**: Required reviewers
- **Reviewers**: DevOps team, Team leads
- **Wait timer**: 0 minutes

### Production Environment
- **Protection rules**: Required reviewers
- **Reviewers**: DevOps team, Team leads, Security team
- **Wait timer**: 5 minutes (cooling-off period)

## Secrets Configuration

### Repository Secrets
The following secrets should be configured:

#### Container Registry
- `GHCR_TOKEN`: GitHub Container Registry token
- `DOCKER_HUB_TOKEN`: Docker Hub token (if using Docker Hub)

#### Deployment
- `KUBE_CONFIG`: Kubernetes configuration for deployments
- `SSH_PRIVATE_KEY`: SSH key for server access (if needed)

#### Monitoring and Notifications
- `SLACK_WEBHOOK_URL`: Slack webhook for notifications
- `DISCORD_WEBHOOK_URL`: Discord webhook for notifications

#### Security
- `SONAR_TOKEN`: SonarCloud token for code quality analysis
- `SNYK_TOKEN`: Snyk token for security scanning

### Environment Secrets
Configure environment-specific secrets:

#### Development
- `DB_PASSWORD_DEV`: Database password for development
- `JWT_SECRET_DEV`: JWT secret for development

#### Staging
- `DB_PASSWORD_STAGING`: Database password for staging
- `JWT_SECRET_STAGING`: JWT secret for staging

#### Production
- `DB_PASSWORD_PROD`: Database password for production
- `JWT_SECRET_PROD`: JWT secret for production

## Automation Rules

### Auto-merge Conditions
- ✅ All required status checks pass
- ✅ All required reviews approved
- ✅ No requested changes
- ✅ Branch is up to date

### Auto-assignment Rules
- Assign pull requests to code owners automatically
- Assign security-related issues to security team
- Assign bug reports to development team lead

## Compliance and Audit

### Audit Log Monitoring
- Monitor all administrative actions
- Track access to sensitive branches
- Log all secret access attempts

### Compliance Checks
- Regular review of access permissions
- Quarterly security settings audit
- Annual compliance assessment

## Implementation Steps

1. **Configure Branch Protection**
   ```bash
   # Use GitHub CLI or web interface to set up branch protection rules
   gh api repos/:owner/:repo/branches/main/protection \
     --method PUT \
     --field required_status_checks='{"strict":true,"contexts":["validate","security-scan"]}' \
     --field enforce_admins=true \
     --field required_pull_request_reviews='{"required_approving_review_count":2,"dismiss_stale_reviews":true}'
   ```

2. **Enable Security Features**
   - Go to Settings > Security & analysis
   - Enable all recommended security features

3. **Configure Environments**
   - Go to Settings > Environments
   - Create development, staging, and production environments
   - Configure protection rules and secrets

4. **Set Up Webhooks**
   - Go to Settings > Webhooks
   - Add webhooks for external integrations

5. **Configure Secrets**
   - Go to Settings > Secrets and variables > Actions
   - Add all required repository and environment secrets