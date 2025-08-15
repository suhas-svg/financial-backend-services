#!/bin/bash

# Container Registry Management Script for Account Service
# Usage: ./registry-manage.sh [command] [options]

set -e

# Default values
REGISTRY=${REGISTRY:-"ghcr.io"}
REPOSITORY=${REPOSITORY:-"account-service"}
OWNER=${OWNER:-$(git config --get remote.origin.url | sed 's/.*github.com[:/]\([^/]*\).*/\1/' || echo "unknown")}

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
    echo "Container Registry Management Script for Account Service"
    echo ""
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Commands:"
    echo "  list           List all images and tags"
    echo "  info           Show registry information"
    echo "  cleanup        Clean up old images"
    echo "  sign           Sign an image"
    echo "  verify         Verify image signature"
    echo "  push           Push image to registry"
    echo "  pull           Pull image from registry"
    echo "  delete         Delete specific image/tag"
    echo "  policy         Show/update registry policies"
    echo "  help           Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  REGISTRY       Container registry [default: ghcr.io]"
    echo "  REPOSITORY     Repository name [default: account-service]"
    echo "  OWNER          Repository owner [default: auto-detected]"
    echo ""
    echo "Examples:"
    echo "  $0 list"
    echo "  $0 info"
    echo "  $0 cleanup --dry-run"
    echo "  $0 sign account-service:v1.0.0"
    echo "  $0 verify account-service:v1.0.0"
}

# Function to check prerequisites
check_prerequisites() {
    local missing_tools=()
    
    # Check required tools
    if ! command -v docker &> /dev/null; then
        missing_tools+=("docker")
    fi
    
    if ! command -v gh &> /dev/null; then
        missing_tools+=("gh (GitHub CLI)")
    fi
    
    if ! command -v jq &> /dev/null; then
        missing_tools+=("jq")
    fi
    
    if [ ${#missing_tools[@]} -gt 0 ]; then
        print_error "Missing required tools: ${missing_tools[*]}"
        print_error "Please install the missing tools and try again."
        exit 1
    fi
    
    # Check authentication
    if ! gh auth status &> /dev/null; then
        print_error "GitHub CLI not authenticated. Please run 'gh auth login' first."
        exit 1
    fi
}

# Function to get full image name
get_full_image_name() {
    local tag=${1:-latest}
    echo "${REGISTRY}/${OWNER}/${REPOSITORY}:${tag}"
}

# Function to list images
list_images() {
    print_status "Listing images in registry..."
    
    local full_repo="${OWNER}/${REPOSITORY}"
    
    # Get package versions from GitHub API
    local versions=$(gh api "/orgs/${OWNER}/packages/container/${REPOSITORY}/versions" --jq '.[].metadata.container.tags[]' 2>/dev/null || echo "")
    
    if [ -n "$versions" ]; then
        echo "Available tags:"
        echo "$versions" | sort -V
        
        echo ""
        echo "Tag count: $(echo "$versions" | wc -l)"
    else
        print_warning "No images found or unable to access registry"
    fi
}

# Function to show registry info
show_registry_info() {
    print_status "Registry Information"
    echo "Registry: $REGISTRY"
    echo "Owner: $OWNER"
    echo "Repository: $REPOSITORY"
    echo "Full repository: ${OWNER}/${REPOSITORY}"
    echo ""
    
    # Get package info
    local package_info=$(gh api "/orgs/${OWNER}/packages/container/${REPOSITORY}" 2>/dev/null || echo "{}")
    
    if [ "$package_info" != "{}" ]; then
        echo "Package Information:"
        echo "$package_info" | jq -r '
            "Name: " + .name,
            "Visibility: " + .visibility,
            "Created: " + .created_at,
            "Updated: " + .updated_at,
            "Downloads: " + (.download_count | tostring),
            "Size: " + (.package_type // "unknown")
        '
    else
        print_warning "Unable to retrieve package information"
    fi
}

# Function to cleanup old images
cleanup_images() {
    local dry_run=${1:-false}
    
    print_status "Cleaning up old images..."
    
    if [ "$dry_run" = "true" ]; then
        print_warning "DRY RUN MODE - No images will be deleted"
    fi
    
    # Get all versions
    local versions=$(gh api "/orgs/${OWNER}/packages/container/${REPOSITORY}/versions" 2>/dev/null || echo "[]")
    
    if [ "$versions" = "[]" ]; then
        print_warning "No versions found or unable to access registry"
        return
    fi
    
    # Cleanup strategies
    echo "Cleanup strategies:"
    echo "1. Keep last 10 development tags"
    echo "2. Keep last 20 build tags"
    echo "3. Keep all production/staging tags"
    echo "4. Remove untagged images older than 30 days"
    
    # Get development tags (older than 10 most recent)
    local dev_tags=$(echo "$versions" | jq -r '.[].metadata.container.tags[]?' | grep -E '^(dev|feature|pr)-' | tail -n +11 || echo "")
    
    if [ -n "$dev_tags" ]; then
        echo ""
        echo "Development tags to cleanup:"
        echo "$dev_tags"
        
        if [ "$dry_run" = "false" ]; then
            # Actual cleanup would go here
            print_warning "Cleanup implementation requires additional API permissions"
        fi
    fi
    
    # Get build tags (older than 20 most recent)
    local build_tags=$(echo "$versions" | jq -r '.[].metadata.container.tags[]?' | grep -E '^build-' | tail -n +21 || echo "")
    
    if [ -n "$build_tags" ]; then
        echo ""
        echo "Build tags to cleanup:"
        echo "$build_tags"
        
        if [ "$dry_run" = "false" ]; then
            # Actual cleanup would go here
            print_warning "Cleanup implementation requires additional API permissions"
        fi
    fi
    
    print_success "Cleanup analysis completed"
}

# Function to sign image
sign_image() {
    local image_tag=$1
    
    if [ -z "$image_tag" ]; then
        print_error "Image tag is required for signing"
        exit 1
    fi
    
    local full_image=$(get_full_image_name "$image_tag")
    
    print_status "Signing image: $full_image"
    
    # Check if cosign is installed
    if ! command -v cosign &> /dev/null; then
        print_error "Cosign is not installed. Please install cosign first."
        exit 1
    fi
    
    # Sign the image
    cosign sign --yes "$full_image"
    
    print_success "Image signed successfully"
}

# Function to verify image signature
verify_image() {
    local image_tag=$1
    
    if [ -z "$image_tag" ]; then
        print_error "Image tag is required for verification"
        exit 1
    fi
    
    local full_image=$(get_full_image_name "$image_tag")
    
    print_status "Verifying image signature: $full_image"
    
    # Check if cosign is installed
    if ! command -v cosign &> /dev/null; then
        print_error "Cosign is not installed. Please install cosign first."
        exit 1
    fi
    
    # Verify the image signature
    if cosign verify "$full_image" --certificate-identity-regexp=".*" --certificate-oidc-issuer-regexp=".*"; then
        print_success "Image signature verified successfully"
    else
        print_error "Image signature verification failed"
        exit 1
    fi
}

# Function to push image
push_image() {
    local image_tag=$1
    local local_tag=${2:-$image_tag}
    
    if [ -z "$image_tag" ]; then
        print_error "Image tag is required for pushing"
        exit 1
    fi
    
    local full_image=$(get_full_image_name "$image_tag")
    
    print_status "Pushing image: $local_tag -> $full_image"
    
    # Tag the local image
    docker tag "$local_tag" "$full_image"
    
    # Push the image
    docker push "$full_image"
    
    print_success "Image pushed successfully"
}

# Function to pull image
pull_image() {
    local image_tag=$1
    
    if [ -z "$image_tag" ]; then
        print_error "Image tag is required for pulling"
        exit 1
    fi
    
    local full_image=$(get_full_image_name "$image_tag")
    
    print_status "Pulling image: $full_image"
    
    # Pull the image
    docker pull "$full_image"
    
    print_success "Image pulled successfully"
}

# Function to delete image
delete_image() {
    local image_tag=$1
    
    if [ -z "$image_tag" ]; then
        print_error "Image tag is required for deletion"
        exit 1
    fi
    
    print_warning "This will permanently delete the image: $image_tag"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_status "Deleting image: $image_tag"
        
        # Delete using GitHub API (requires appropriate permissions)
        print_warning "Image deletion requires additional API permissions"
        print_warning "Use GitHub web interface or contact repository admin"
    else
        print_status "Deletion cancelled"
    fi
}

# Function to show registry policies
show_policies() {
    print_status "Registry Policies"
    echo ""
    echo "Current policies for ${OWNER}/${REPOSITORY}:"
    echo ""
    echo "1. Image Retention:"
    echo "   - Production tags: Keep indefinitely"
    echo "   - Staging tags: Keep last 10"
    echo "   - Development tags: Keep last 10"
    echo "   - Build tags: Keep last 20"
    echo "   - Untagged images: Delete after 30 days"
    echo ""
    echo "2. Security Policies:"
    echo "   - All images must be signed"
    echo "   - Vulnerability scanning required"
    echo "   - SBOM generation required"
    echo ""
    echo "3. Access Policies:"
    echo "   - Read access: Public"
    echo "   - Write access: Repository collaborators"
    echo "   - Admin access: Repository owners"
    echo ""
    echo "4. Naming Conventions:"
    echo "   - Production: v{major}.{minor}.{patch}, stable, latest"
    echo "   - Staging: staging, staging-{version}"
    echo "   - Development: dev, dev-{feature}, pr-{number}"
    echo "   - Build: build-{date}-{number}"
}

# Main execution
main() {
    local command=${1:-help}
    shift || true
    
    case $command in
        list|ls)
            check_prerequisites
            list_images "$@"
            ;;
        info)
            check_prerequisites
            show_registry_info "$@"
            ;;
        cleanup|clean)
            check_prerequisites
            local dry_run=false
            if [[ "$1" == "--dry-run" ]]; then
                dry_run=true
            fi
            cleanup_images "$dry_run"
            ;;
        sign)
            check_prerequisites
            sign_image "$@"
            ;;
        verify)
            check_prerequisites
            verify_image "$@"
            ;;
        push)
            check_prerequisites
            push_image "$@"
            ;;
        pull)
            check_prerequisites
            pull_image "$@"
            ;;
        delete|rm)
            check_prerequisites
            delete_image "$@"
            ;;
        policy|policies)
            show_policies "$@"
            ;;
        help|--help|-h)
            show_usage
            ;;
        *)
            print_error "Unknown command: $command"
            show_usage
            exit 1
            ;;
    esac
}

# Change to script directory
cd "$(dirname "$0")/.."

# Run main function
main "$@"