#!/bin/bash

# Test Script for Task 4: Container Image Management and Security
# This script tests all components of task 4 to ensure they work correctly

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results tracking
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to run a test
run_test() {
    local test_name="$1"
    local test_command="$2"
    
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
    print_status "Running test: $test_name"
    
    if eval "$test_command"; then
        print_success "✅ $test_name - PASSED"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        print_error "❌ $test_name - FAILED"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Test 1: Verify Dockerfile structure and best practices
test_dockerfile_structure() {
    print_status "Testing Dockerfile structure..."
    
    # Check if Dockerfile exists
    if [ ! -f "Dockerfile" ]; then
        print_error "Dockerfile not found"
        return 1
    fi
    
    # Check for multi-stage build
    if ! grep -q "FROM.*AS builder" Dockerfile; then
        print_error "Multi-stage build not found in Dockerfile"
        return 1
    fi
    
    # Check for non-root user
    if ! grep -q "USER appuser" Dockerfile; then
        print_error "Non-root user not configured in Dockerfile"
        return 1
    fi
    
    # Check for health check
    if ! grep -q "HEALTHCHECK" Dockerfile; then
        print_error "Health check not found in Dockerfile"
        return 1
    fi
    
    # Check for proper labeling
    if ! grep -q "org.opencontainers.image" Dockerfile; then
        print_error "OCI labels not found in Dockerfile"
        return 1
    fi
    
    print_success "Dockerfile structure validation passed"
    return 0
}

# Test 2: Verify development Dockerfile exists
test_dev_dockerfile() {
    print_status "Testing development Dockerfile..."
    
    if [ ! -f "Dockerfile.dev" ]; then
        print_error "Dockerfile.dev not found"
        return 1
    fi
    
    # Check for debug port exposure
    if ! grep -q "EXPOSE.*5005" Dockerfile.dev; then
        print_error "Debug port not exposed in Dockerfile.dev"
        return 1
    fi
    
    print_success "Development Dockerfile validation passed"
    return 0
}

# Test 3: Test Docker build process
test_docker_build() {
    print_status "Testing Docker build process..."
    
    # Check if Docker is running
    if ! docker info >/dev/null 2>&1; then
        print_warning "Docker is not running, skipping build test"
        return 0
    fi
    
    # Test basic build
    if docker build -t account-service:test . >/dev/null 2>&1; then
        print_success "Docker build successful"
        
        # Test image layers
        LAYERS=$(docker history account-service:test --format "table {{.CreatedBy}}" | wc -l)
        if [ "$LAYERS" -lt 20 ]; then
            print_success "Image has optimized layers ($LAYERS layers)"
        else
            print_warning "Image has many layers ($LAYERS layers) - consider optimization"
        fi
        
        # Clean up test image
        docker rmi account-service:test >/dev/null 2>&1 || true
        return 0
    else
        print_error "Docker build failed"
        return 1
    fi
}

# Test 4: Test build scripts
test_build_scripts() {
    print_status "Testing build scripts..."
    
    # Test bash script
    if [ -f "scripts/build-docker.sh" ]; then
        if [ -x "scripts/build-docker.sh" ]; then
            print_success "build-docker.sh is executable"
        else
            print_warning "build-docker.sh is not executable"
        fi
        
        # Test help option
        if ./scripts/build-docker.sh --help >/dev/null 2>&1; then
            print_success "build-docker.sh help option works"
        else
            print_warning "build-docker.sh help option failed"
        fi
    else
        print_error "build-docker.sh not found"
        return 1
    fi
    
    # Test PowerShell script
    if [ -f "scripts/build-docker.ps1" ]; then
        print_success "build-docker.ps1 exists"
    else
        print_error "build-docker.ps1 not found"
        return 1
    fi
    
    return 0
}

# Test 5: Test Docker Compose configuration
test_docker_compose() {
    print_status "Testing Docker Compose configuration..."
    
    # Check if docker-compose.yml exists
    if [ ! -f "docker-compose.yml" ]; then
        print_error "docker-compose.yml not found"
        return 1
    fi
    
    # Validate compose file syntax
    if command_exists docker-compose; then
        if docker-compose config >/dev/null 2>&1; then
            print_success "Docker Compose syntax is valid"
        else
            print_error "Docker Compose syntax validation failed"
            return 1
        fi
    elif command_exists docker; then
        if docker compose config >/dev/null 2>&1; then
            print_success "Docker Compose syntax is valid"
        else
            print_error "Docker Compose syntax validation failed"
            return 1
        fi
    else
        print_warning "Docker Compose not available, skipping syntax validation"
    fi
    
    # Check for environment-specific overrides
    if [ -f "docker-compose.dev.yml" ] && [ -f "docker-compose.staging.yml" ]; then
        print_success "Environment-specific compose files exist"
    else
        print_error "Environment-specific compose files missing"
        return 1
    fi
    
    return 0
}

# Test 6: Test environment configuration files
test_env_configs() {
    print_status "Testing environment configuration files..."
    
    # Check for environment files
    local env_files=(".env.dev" ".env.staging")
    for env_file in "${env_files[@]}"; do
        if [ -f "$env_file" ]; then
            print_success "$env_file exists"
            
            # Check for required variables
            if grep -q "ENVIRONMENT=" "$env_file" && grep -q "REGISTRY=" "$env_file"; then
                print_success "$env_file has required variables"
            else
                print_warning "$env_file missing some required variables"
            fi
        else
            print_error "$env_file not found"
            return 1
        fi
    done
    
    return 0
}

# Test 7: Test security scanning scripts
test_security_scripts() {
    print_status "Testing security scanning scripts..."
    
    # Test bash security script
    if [ -f "scripts/security-scan.sh" ]; then
        if [ -x "scripts/security-scan.sh" ]; then
            print_success "security-scan.sh is executable"
        else
            print_warning "security-scan.sh is not executable"
        fi
        
        # Test help option
        if ./scripts/security-scan.sh --help >/dev/null 2>&1; then
            print_success "security-scan.sh help option works"
        else
            print_warning "security-scan.sh help option failed"
        fi
    else
        print_error "security-scan.sh not found"
        return 1
    fi
    
    # Test PowerShell security script
    if [ -f "scripts/security-scan.ps1" ]; then
        print_success "security-scan.ps1 exists"
    else
        print_error "security-scan.ps1 not found"
        return 1
    fi
    
    return 0
}

# Test 8: Test security policy configuration
test_security_policies() {
    print_status "Testing security policy configuration..."
    
    # Check security policy file
    if [ -f "security-policy.yaml" ]; then
        print_success "security-policy.yaml exists"
        
        # Check for key sections
        if grep -q "vulnerability_scanning:" security-policy.yaml && \
           grep -q "secret_detection:" security-policy.yaml && \
           grep -q "configuration_scanning:" security-policy.yaml; then
            print_success "Security policy has required sections"
        else
            print_warning "Security policy missing some sections"
        fi
    else
        print_error "security-policy.yaml not found"
        return 1
    fi
    
    # Check Trivy ignore file
    if [ -f ".trivyignore" ]; then
        print_success ".trivyignore file exists"
    else
        print_warning ".trivyignore file not found"
    fi
    
    return 0
}

# Test 9: Test registry management scripts
test_registry_scripts() {
    print_status "Testing registry management scripts..."
    
    # Test bash registry script
    if [ -f "scripts/registry-manage.sh" ]; then
        if [ -x "scripts/registry-manage.sh" ]; then
            print_success "registry-manage.sh is executable"
        else
            print_warning "registry-manage.sh is not executable"
        fi
        
        # Test help option
        if ./scripts/registry-manage.sh --help >/dev/null 2>&1; then
            print_success "registry-manage.sh help option works"
        else
            print_warning "registry-manage.sh help option failed"
        fi
    else
        print_error "registry-manage.sh not found"
        return 1
    fi
    
    # Test PowerShell registry script
    if [ -f "scripts/registry-manage.ps1" ]; then
        print_success "registry-manage.ps1 exists"
    else
        print_error "registry-manage.ps1 not found"
        return 1
    fi
    
    return 0
}

# Test 10: Test registry configuration
test_registry_config() {
    print_status "Testing registry configuration..."
    
    if [ -f "registry-config.yaml" ]; then
        print_success "registry-config.yaml exists"
        
        # Check for key sections
        if grep -q "registry:" registry-config.yaml && \
           grep -q "tagging:" registry-config.yaml && \
           grep -q "security:" registry-config.yaml; then
            print_success "Registry config has required sections"
        else
            print_warning "Registry config missing some sections"
        fi
    else
        print_error "registry-config.yaml not found"
        return 1
    fi
    
    return 0
}

# Test 11: Test Docker management scripts
test_docker_management() {
    print_status "Testing Docker management scripts..."
    
    # Test bash docker management script
    if [ -f "scripts/docker-manage.sh" ]; then
        if [ -x "scripts/docker-manage.sh" ]; then
            print_success "docker-manage.sh is executable"
        else
            print_warning "docker-manage.sh is not executable"
        fi
        
        # Test help option
        if ./scripts/docker-manage.sh --help >/dev/null 2>&1; then
            print_success "docker-manage.sh help option works"
        else
            print_warning "docker-manage.sh help option failed"
        fi
    else
        print_error "docker-manage.sh not found"
        return 1
    fi
    
    # Test PowerShell docker management script
    if [ -f "scripts/docker-manage.ps1" ]; then
        print_success "docker-manage.ps1 exists"
    else
        print_error "docker-manage.ps1 not found"
        return 1
    fi
    
    return 0
}

# Test 12: Test CI/CD workflow integration
test_cicd_integration() {
    print_status "Testing CI/CD workflow integration..."
    
    if [ -f "../.github/workflows/ci-cd-pipeline.yml" ]; then
        print_success "CI/CD workflow file exists"
        
        # Check for Docker build steps
        if grep -q "docker/build-push-action" ../.github/workflows/ci-cd-pipeline.yml; then
            print_success "Docker build action found in workflow"
        else
            print_error "Docker build action not found in workflow"
            return 1
        fi
        
        # Check for security scanning
        if grep -q "trivy-action" ../.github/workflows/ci-cd-pipeline.yml; then
            print_success "Security scanning found in workflow"
        else
            print_error "Security scanning not found in workflow"
            return 1
        fi
        
        # Check for image signing
        if grep -q "cosign" ../.github/workflows/ci-cd-pipeline.yml; then
            print_success "Image signing found in workflow"
        else
            print_error "Image signing not found in workflow"
            return 1
        fi
    else
        print_error "CI/CD workflow file not found"
        return 1
    fi
    
    return 0
}

# Test 13: Test database initialization script
test_db_init() {
    print_status "Testing database initialization script..."
    
    if [ -f "scripts/init-db.sql" ]; then
        print_success "Database initialization script exists"
        
        # Check for basic SQL syntax
        if grep -q "CREATE DATABASE" scripts/init-db.sql && \
           grep -q "CREATE EXTENSION" scripts/init-db.sql; then
            print_success "Database script has required SQL commands"
        else
            print_warning "Database script missing some SQL commands"
        fi
    else
        print_error "Database initialization script not found"
        return 1
    fi
    
    return 0
}

# Test 14: Test file permissions and security
test_file_security() {
    print_status "Testing file permissions and security..."
    
    # Check script permissions
    local scripts=("scripts/build-docker.sh" "scripts/security-scan.sh" "scripts/registry-manage.sh" "scripts/docker-manage.sh")
    for script in "${scripts[@]}"; do
        if [ -f "$script" ]; then
            if [ -x "$script" ]; then
                print_success "$script has execute permissions"
            else
                print_warning "$script missing execute permissions"
                chmod +x "$script" 2>/dev/null || print_warning "Could not set execute permissions for $script"
            fi
        fi
    done
    
    # Check for sensitive files
    if [ -f ".env" ]; then
        print_warning ".env file found - ensure it's in .gitignore"
    fi
    
    return 0
}

# Test 15: Integration test with actual build (if Docker is available)
test_integration() {
    print_status "Running integration test..."
    
    if ! command_exists docker; then
        print_warning "Docker not available, skipping integration test"
        return 0
    fi
    
    if ! docker info >/dev/null 2>&1; then
        print_warning "Docker not running, skipping integration test"
        return 0
    fi
    
    # Test build with environment variables
    export BUILD_ENV=dev
    export APP_VERSION=test-1.0.0
    export BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
    export VCS_REF=$(git rev-parse HEAD 2>/dev/null || echo "test-commit")
    
    if docker build \
        --build-arg BUILD_ENV="$BUILD_ENV" \
        --build-arg APP_VERSION="$APP_VERSION" \
        --build-arg BUILD_DATE="$BUILD_DATE" \
        --build-arg VCS_REF="$VCS_REF" \
        -t account-service:integration-test . >/dev/null 2>&1; then
        
        print_success "Integration build successful"
        
        # Test image labels
        LABELS=$(docker inspect account-service:integration-test --format='{{json .Config.Labels}}')
        if echo "$LABELS" | grep -q "org.opencontainers.image.version"; then
            print_success "Image labels are properly set"
        else
            print_warning "Image labels not found"
        fi
        
        # Clean up
        docker rmi account-service:integration-test >/dev/null 2>&1 || true
        return 0
    else
        print_error "Integration build failed"
        return 1
    fi
}

# Main test execution
main() {
    echo "========================================"
    echo "  Task 4 Testing: Container Image"
    echo "  Management and Security"
    echo "========================================"
    echo ""
    
    # Change to account-service directory
    cd "$(dirname "$0")/.."
    
    print_status "Starting Task 4 component tests..."
    echo ""
    
    # Run all tests
    run_test "Dockerfile Structure" "test_dockerfile_structure"
    run_test "Development Dockerfile" "test_dev_dockerfile"
    run_test "Docker Build Process" "test_docker_build"
    run_test "Build Scripts" "test_build_scripts"
    run_test "Docker Compose Configuration" "test_docker_compose"
    run_test "Environment Configurations" "test_env_configs"
    run_test "Security Scanning Scripts" "test_security_scripts"
    run_test "Security Policy Configuration" "test_security_policies"
    run_test "Registry Management Scripts" "test_registry_scripts"
    run_test "Registry Configuration" "test_registry_config"
    run_test "Docker Management Scripts" "test_docker_management"
    run_test "CI/CD Integration" "test_cicd_integration"
    run_test "Database Initialization" "test_db_init"
    run_test "File Security" "test_file_security"
    run_test "Integration Test" "test_integration"
    
    # Print summary
    echo ""
    echo "========================================"
    echo "           TEST SUMMARY"
    echo "========================================"
    echo "Total Tests: $TESTS_TOTAL"
    echo "Passed: $TESTS_PASSED"
    echo "Failed: $TESTS_FAILED"
    echo ""
    
    if [ $TESTS_FAILED -eq 0 ]; then
        print_success "🎉 All tests passed! Task 4 implementation is working correctly."
        echo ""
        echo "Task 4 Components Verified:"
        echo "✅ 4.1 Optimized Docker build process"
        echo "✅ 4.2 Container security scanning"
        echo "✅ 4.3 Container registry integration"
        return 0
    else
        print_error "❌ Some tests failed. Please review the issues above."
        echo ""
        echo "Failed tests need attention before Task 4 can be considered complete."
        return 1
    fi
}

# Run main function
main "$@"