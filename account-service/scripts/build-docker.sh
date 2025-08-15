#!/bin/bash

# Docker Build Script for Account Service
# Usage: ./build-docker.sh [environment] [version] [options]

set -e

# Default values
ENVIRONMENT=${1:-dev}
VERSION=${2:-latest}
REGISTRY=${REGISTRY:-ghcr.io}
IMAGE_NAME=${IMAGE_NAME:-account-service}
BUILD_ARGS=""
PUSH=${PUSH:-false}
PLATFORMS=${PLATFORMS:-linux/amd64}

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
    echo "Usage: $0 [environment] [version] [options]"
    echo ""
    echo "Arguments:"
    echo "  environment    Build environment (dev, staging, prod) [default: dev]"
    echo "  version        Image version tag [default: latest]"
    echo ""
    echo "Environment Variables:"
    echo "  REGISTRY       Container registry [default: ghcr.io]"
    echo "  IMAGE_NAME     Image name [default: account-service]"
    echo "  PUSH           Push to registry (true/false) [default: false]"
    echo "  PLATFORMS      Target platforms [default: linux/amd64]"
    echo ""
    echo "Examples:"
    echo "  $0 dev v1.0.0"
    echo "  PUSH=true $0 prod v1.2.3"
    echo "  PLATFORMS=linux/amd64,linux/arm64 $0 staging v1.1.0"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        --push)
            PUSH=true
            shift
            ;;
        --no-cache)
            BUILD_ARGS="$BUILD_ARGS --no-cache"
            shift
            ;;
        --platforms)
            PLATFORMS="$2"
            shift 2
            ;;
        *)
            # Positional arguments are handled above
            shift
            ;;
    esac
done

# Validate environment
case $ENVIRONMENT in
    dev|development)
        DOCKERFILE="Dockerfile.dev"
        BUILD_ENV="dev"
        ;;
    staging|stage)
        DOCKERFILE="Dockerfile"
        BUILD_ENV="staging"
        ;;
    prod|production)
        DOCKERFILE="Dockerfile"
        BUILD_ENV="prod"
        ;;
    *)
        print_error "Invalid environment: $ENVIRONMENT"
        print_error "Valid environments: dev, staging, prod"
        exit 1
        ;;
esac

# Set image tags
FULL_IMAGE_NAME="${REGISTRY}/${IMAGE_NAME}"
IMAGE_TAG="${FULL_IMAGE_NAME}:${VERSION}"
ENV_TAG="${FULL_IMAGE_NAME}:${BUILD_ENV}-latest"

print_status "Building Docker image for Account Service"
print_status "Environment: $BUILD_ENV"
print_status "Version: $VERSION"
print_status "Dockerfile: $DOCKERFILE"
print_status "Image: $IMAGE_TAG"
print_status "Platforms: $PLATFORMS"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    print_error "Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if buildx is available for multi-platform builds
if [[ "$PLATFORMS" == *","* ]]; then
    if ! docker buildx version > /dev/null 2>&1; then
        print_error "Docker Buildx is required for multi-platform builds"
        exit 1
    fi
    
    # Create builder instance if it doesn't exist
    if ! docker buildx inspect multiarch > /dev/null 2>&1; then
        print_status "Creating multi-platform builder..."
        docker buildx create --name multiarch --driver docker-container --use
        docker buildx inspect --bootstrap
    else
        docker buildx use multiarch
    fi
fi

# Generate build arguments
BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
VCS_REF=$(git rev-parse HEAD 2>/dev/null || echo "unknown")

BUILD_ARGS="$BUILD_ARGS --build-arg BUILD_ENV=$BUILD_ENV"
BUILD_ARGS="$BUILD_ARGS --build-arg APP_VERSION=$VERSION"
BUILD_ARGS="$BUILD_ARGS --build-arg BUILD_DATE=$BUILD_DATE"
BUILD_ARGS="$BUILD_ARGS --build-arg VCS_REF=$VCS_REF"

# Add labels
BUILD_ARGS="$BUILD_ARGS --label org.opencontainers.image.title='Account Service'"
BUILD_ARGS="$BUILD_ARGS --label org.opencontainers.image.description='Financial Account Service Microservice'"
BUILD_ARGS="$BUILD_ARGS --label org.opencontainers.image.version='$VERSION'"
BUILD_ARGS="$BUILD_ARGS --label org.opencontainers.image.created='$BUILD_DATE'"
BUILD_ARGS="$BUILD_ARGS --label org.opencontainers.image.revision='$VCS_REF'"
BUILD_ARGS="$BUILD_ARGS --label environment='$BUILD_ENV'"

# Build command
if [[ "$PLATFORMS" == *","* ]]; then
    # Multi-platform build
    BUILD_CMD="docker buildx build"
    BUILD_ARGS="$BUILD_ARGS --platform $PLATFORMS"
    if [[ "$PUSH" == "true" ]]; then
        BUILD_ARGS="$BUILD_ARGS --push"
    else
        BUILD_ARGS="$BUILD_ARGS --load"
        print_warning "Multi-platform builds cannot be loaded locally. Use --push to push to registry."
    fi
else
    # Single platform build
    BUILD_CMD="docker build"
fi

# Add tags
BUILD_ARGS="$BUILD_ARGS -t $IMAGE_TAG -t $ENV_TAG"

# Add dockerfile
BUILD_ARGS="$BUILD_ARGS -f $DOCKERFILE"

# Execute build
print_status "Executing build command..."
echo "Command: $BUILD_CMD $BUILD_ARGS ."

cd "$(dirname "$0")/.."
eval "$BUILD_CMD $BUILD_ARGS ."

if [[ $? -eq 0 ]]; then
    print_success "Docker image built successfully!"
    
    # Show image information
    if [[ "$PLATFORMS" != *","* ]]; then
        print_status "Image information:"
        docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}" | grep "$IMAGE_NAME" | head -5
    fi
    
    # Push to registry if requested and not multi-platform
    if [[ "$PUSH" == "true" && "$PLATFORMS" != *","* ]]; then
        print_status "Pushing image to registry..."
        docker push "$IMAGE_TAG"
        docker push "$ENV_TAG"
        print_success "Image pushed successfully!"
    fi
    
    # Security scan if trivy is available
    if command -v trivy &> /dev/null; then
        print_status "Running security scan..."
        trivy image --severity HIGH,CRITICAL "$IMAGE_TAG" || print_warning "Security scan found issues"
    fi
    
    print_success "Build process completed!"
    echo ""
    echo "Image tags:"
    echo "  - $IMAGE_TAG"
    echo "  - $ENV_TAG"
    
else
    print_error "Docker build failed!"
    exit 1
fi