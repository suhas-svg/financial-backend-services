#!/bin/bash

# Container Security Scanning Script for Account Service
# Usage: ./security-scan.sh [image-name] [options]

set -e

# Default values
IMAGE_NAME=${1:-"account-service:latest"}
OUTPUT_DIR=${OUTPUT_DIR:-"./security-reports"}
FAIL_ON_CRITICAL=${FAIL_ON_CRITICAL:-true}
FAIL_ON_HIGH=${FAIL_ON_HIGH:-false}
CRITICAL_THRESHOLD=${CRITICAL_THRESHOLD:-0}
HIGH_THRESHOLD=${HIGH_THRESHOLD:-10}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Function to show usage
show_usage() {
    echo "Container Security Scanning Script for Account Service"
    echo ""
    echo "Usage: $0 [image-name] [options]"
    echo ""
    echo "Arguments:"
    echo "  image-name         Docker image to scan [default: account-service:latest]"
    echo ""
    echo "Environment Variables:"
    echo "  OUTPUT_DIR         Output directory for reports [default: ./security-reports]"
    echo "  FAIL_ON_CRITICAL   Fail on critical vulnerabilities [default: true]"
    echo "  FAIL_ON_HIGH       Fail on high vulnerabilities [default: false]"
    echo "  CRITICAL_THRESHOLD Maximum critical vulnerabilities allowed [default: 0]"
    echo "  HIGH_THRESHOLD     Maximum high vulnerabilities allowed [default: 10]"
    echo ""
    echo "Examples:"
    echo "  $0 account-service:v1.0.0"
    echo "  OUTPUT_DIR=/tmp/scans $0 my-image:latest"
    echo "  FAIL_ON_HIGH=true HIGH_THRESHOLD=5 $0 account-service:dev"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to install security tools
install_tools() {
    print_status "Checking and installing security scanning tools..."
    
    # Install Trivy
    if ! command_exists trivy; then
        print_status "Installing Trivy..."
        curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
    fi
    
    # Install Grype
    if ! command_exists grype; then
        print_status "Installing Grype..."
        curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh | sh -s -- -b /usr/local/bin
    fi
    
    # Install Syft
    if ! command_exists syft; then
        print_status "Installing Syft..."
        curl -sSfL https://raw.githubusercontent.com/anchore/syft/main/install.sh | sh -s -- -b /usr/local/bin
    fi
    
    print_success "Security tools are ready!"
}

# Function to run Trivy scan
run_trivy_scan() {
    local image=$1
    local output_dir=$2
    
    print_status "Running Trivy vulnerability scan..."
    
    # Scan for vulnerabilities
    trivy image --format json --output "$output_dir/trivy-vulnerabilities.json" "$image"
    trivy image --format table --output "$output_dir/trivy-vulnerabilities.txt" "$image"
    
    # Scan for secrets
    trivy image --scanners secret --format json --output "$output_dir/trivy-secrets.json" "$image"
    trivy image --scanners secret --format table --output "$output_dir/trivy-secrets.txt" "$image"
    
    # Scan for misconfigurations
    trivy image --scanners config --format json --output "$output_dir/trivy-config.json" "$image"
    trivy image --scanners config --format table --output "$output_dir/trivy-config.txt" "$image"
    
    # Generate SARIF for GitHub integration
    trivy image --format sarif --output "$output_dir/trivy-results.sarif" "$image"
    
    print_success "Trivy scan completed!"
}

# Function to run Grype scan
run_grype_scan() {
    local image=$1
    local output_dir=$2
    
    print_status "Running Grype vulnerability scan..."
    
    grype "$image" -o json > "$output_dir/grype-results.json"
    grype "$image" -o table > "$output_dir/grype-results.txt"
    
    print_success "Grype scan completed!"
}

# Function to generate SBOM
generate_sbom() {
    local image=$1
    local output_dir=$2
    
    print_status "Generating Software Bill of Materials (SBOM)..."
    
    syft "$image" -o spdx-json > "$output_dir/sbom.spdx.json"
    syft "$image" -o table > "$output_dir/sbom.txt"
    syft "$image" -o cyclonedx-json > "$output_dir/sbom.cyclonedx.json"
    
    print_success "SBOM generation completed!"
}

# Function to analyze base image
analyze_base_image() {
    local image=$1
    local output_dir=$2
    
    print_status "Analyzing base image security..."
    
    # Get base image information
    BASE_IMAGE=$(docker inspect "$image" --format='{{.Config.Image}}' 2>/dev/null || echo "unknown")
    
    if [ "$BASE_IMAGE" != "unknown" ]; then
        echo "Base image: $BASE_IMAGE" > "$output_dir/base-image-info.txt"
        
        # Scan base image
        trivy image --format json --output "$output_dir/base-image-scan.json" "$BASE_IMAGE" 2>/dev/null || echo "Base image scan failed"
        trivy image --format table --output "$output_dir/base-image-scan.txt" "$BASE_IMAGE" 2>/dev/null || echo "Base image scan failed"
    else
        echo "Could not determine base image" > "$output_dir/base-image-info.txt"
    fi
    
    print_success "Base image analysis completed!"
}

# Function to parse scan results
parse_results() {
    local output_dir=$1
    
    print_status "Parsing scan results..."
    
    # Parse Trivy results
    if [ -f "$output_dir/trivy-vulnerabilities.json" ]; then
        TRIVY_CRITICAL=$(jq '[.Results[]?.Vulnerabilities[]? | select(.Severity == "CRITICAL")] | length' "$output_dir/trivy-vulnerabilities.json")
        TRIVY_HIGH=$(jq '[.Results[]?.Vulnerabilities[]? | select(.Severity == "HIGH")] | length' "$output_dir/trivy-vulnerabilities.json")
        TRIVY_MEDIUM=$(jq '[.Results[]?.Vulnerabilities[]? | select(.Severity == "MEDIUM")] | length' "$output_dir/trivy-vulnerabilities.json")
        TRIVY_LOW=$(jq '[.Results[]?.Vulnerabilities[]? | select(.Severity == "LOW")] | length' "$output_dir/trivy-vulnerabilities.json")
    else
        TRIVY_CRITICAL=0
        TRIVY_HIGH=0
        TRIVY_MEDIUM=0
        TRIVY_LOW=0
    fi
    
    # Parse Grype results
    if [ -f "$output_dir/grype-results.json" ]; then
        GRYPE_CRITICAL=$(jq '[.matches[] | select(.vulnerability.severity == "Critical")] | length' "$output_dir/grype-results.json")
        GRYPE_HIGH=$(jq '[.matches[] | select(.vulnerability.severity == "High")] | length' "$output_dir/grype-results.json")
        GRYPE_MEDIUM=$(jq '[.matches[] | select(.vulnerability.severity == "Medium")] | length' "$output_dir/grype-results.json")
        GRYPE_LOW=$(jq '[.matches[] | select(.vulnerability.severity == "Low")] | length' "$output_dir/grype-results.json")
    else
        GRYPE_CRITICAL=0
        GRYPE_HIGH=0
        GRYPE_MEDIUM=0
        GRYPE_LOW=0
    fi
    
    # Calculate totals
    TOTAL_CRITICAL=$((TRIVY_CRITICAL + GRYPE_CRITICAL))
    TOTAL_HIGH=$((TRIVY_HIGH + GRYPE_HIGH))
    TOTAL_MEDIUM=$((TRIVY_MEDIUM + GRYPE_MEDIUM))
    TOTAL_LOW=$((TRIVY_LOW + GRYPE_LOW))
    
    print_success "Results parsed successfully!"
}

# Function to generate summary report
generate_report() {
    local image=$1
    local output_dir=$2
    
    print_status "Generating security report..."
    
    local report_file="$output_dir/security-summary.md"
    
    cat > "$report_file" << EOF
# Container Security Scan Report

## Image Information
- **Image**: $image
- **Scan Date**: $(date -u)
- **Scanner Version**: Trivy $(trivy --version | head -1), Grype $(grype version | head -1)

## Vulnerability Summary

| Scanner | Critical | High | Medium | Low |
|---------|----------|------|--------|-----|
| Trivy   | $TRIVY_CRITICAL | $TRIVY_HIGH | $TRIVY_MEDIUM | $TRIVY_LOW |
| Grype   | $GRYPE_CRITICAL | $GRYPE_HIGH | $GRYPE_MEDIUM | $GRYPE_LOW |
| **Total** | **$TOTAL_CRITICAL** | **$TOTAL_HIGH** | **$TOTAL_MEDIUM** | **$TOTAL_LOW** |

## Security Assessment

EOF

    if [ "$TOTAL_CRITICAL" -gt 0 ]; then
        echo "- 🚨 **CRITICAL ALERT**: $TOTAL_CRITICAL critical vulnerabilities found - Immediate action required!" >> "$report_file"
    fi
    
    if [ "$TOTAL_HIGH" -gt 5 ]; then
        echo "- ⚠️ **HIGH PRIORITY**: $TOTAL_HIGH high vulnerabilities found - Consider updating dependencies" >> "$report_file"
    fi
    
    if [ "$TOTAL_CRITICAL" -eq 0 ] && [ "$TOTAL_HIGH" -le 5 ]; then
        echo "- ✅ **GOOD**: Security posture is acceptable" >> "$report_file"
    fi
    
    cat >> "$report_file" << EOF

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

- \`trivy-vulnerabilities.json/txt\` - Trivy vulnerability scan results
- \`trivy-secrets.json/txt\` - Secret detection results
- \`trivy-config.json/txt\` - Configuration scan results
- \`grype-results.json/txt\` - Grype vulnerability scan results
- \`sbom.spdx.json\` - Software Bill of Materials (SPDX format)
- \`sbom.cyclonedx.json\` - Software Bill of Materials (CycloneDX format)
- \`trivy-results.sarif\` - SARIF format for GitHub integration

EOF

    print_success "Security report generated: $report_file"
}

# Function to display results
display_results() {
    echo ""
    echo "=================================="
    echo "    SECURITY SCAN RESULTS"
    echo "=================================="
    echo ""
    printf "%-12s %-10s %-8s %-8s %-8s\n" "Scanner" "Critical" "High" "Medium" "Low"
    echo "=================================================="
    printf "%-12s %-10s %-8s %-8s %-8s\n" "Trivy" "$TRIVY_CRITICAL" "$TRIVY_HIGH" "$TRIVY_MEDIUM" "$TRIVY_LOW"
    printf "%-12s %-10s %-8s %-8s %-8s\n" "Grype" "$GRYPE_CRITICAL" "$GRYPE_HIGH" "$GRYPE_MEDIUM" "$GRYPE_LOW"
    echo "=================================================="
    printf "%-12s %-10s %-8s %-8s %-8s\n" "TOTAL" "$TOTAL_CRITICAL" "$TOTAL_HIGH" "$TOTAL_MEDIUM" "$TOTAL_LOW"
    echo ""
    
    if [ "$TOTAL_CRITICAL" -gt "$CRITICAL_THRESHOLD" ]; then
        print_error "CRITICAL: Found $TOTAL_CRITICAL critical vulnerabilities (threshold: $CRITICAL_THRESHOLD)"
    fi
    
    if [ "$TOTAL_HIGH" -gt "$HIGH_THRESHOLD" ]; then
        print_warning "HIGH: Found $TOTAL_HIGH high vulnerabilities (threshold: $HIGH_THRESHOLD)"
    fi
    
    if [ "$TOTAL_CRITICAL" -eq 0 ] && [ "$TOTAL_HIGH" -le "$HIGH_THRESHOLD" ]; then
        print_success "Security scan passed all thresholds!"
    fi
}

# Main execution
main() {
    # Parse command line arguments
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
    esac
    
    print_status "Starting container security scan for: $IMAGE_NAME"
    
    # Create output directory
    mkdir -p "$OUTPUT_DIR"
    
    # Check if image exists
    if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
        print_error "Docker image '$IMAGE_NAME' not found. Please build the image first."
        exit 1
    fi
    
    # Install required tools
    install_tools
    
    # Run security scans
    run_trivy_scan "$IMAGE_NAME" "$OUTPUT_DIR"
    run_grype_scan "$IMAGE_NAME" "$OUTPUT_DIR"
    generate_sbom "$IMAGE_NAME" "$OUTPUT_DIR"
    analyze_base_image "$IMAGE_NAME" "$OUTPUT_DIR"
    
    # Parse and display results
    parse_results "$OUTPUT_DIR"
    generate_report "$IMAGE_NAME" "$OUTPUT_DIR"
    display_results
    
    # Check thresholds and exit accordingly
    EXIT_CODE=0
    
    if [ "$FAIL_ON_CRITICAL" = "true" ] && [ "$TOTAL_CRITICAL" -gt "$CRITICAL_THRESHOLD" ]; then
        print_error "Scan failed: Critical vulnerabilities exceed threshold ($TOTAL_CRITICAL > $CRITICAL_THRESHOLD)"
        EXIT_CODE=1
    fi
    
    if [ "$FAIL_ON_HIGH" = "true" ] && [ "$TOTAL_HIGH" -gt "$HIGH_THRESHOLD" ]; then
        print_error "Scan failed: High vulnerabilities exceed threshold ($TOTAL_HIGH > $HIGH_THRESHOLD)"
        EXIT_CODE=1
    fi
    
    if [ $EXIT_CODE -eq 0 ]; then
        print_success "Security scan completed successfully!"
        print_status "Reports saved to: $OUTPUT_DIR"
    else
        print_error "Security scan failed due to policy violations!"
    fi
    
    exit $EXIT_CODE
}

# Change to script directory
cd "$(dirname "$0")/.."

# Run main function
main "$@"