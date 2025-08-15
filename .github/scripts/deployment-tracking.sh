#!/bin/bash

# Deployment tracking script for CI/CD pipeline integration
# This script creates deployment markers and tracks deployment metrics

set -e

# Configuration
ENVIRONMENT=${1:-dev}
DEPLOYMENT_STATUS=${2:-start}
SERVICE_URL=${3:-http://localhost:8080}
DEPLOYMENT_ID=${4:-$(date +%s)}
VERSION=${5:-${GITHUB_SHA:-unknown}}

# Color functions
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

debug() {
    echo -e "${BLUE}[DEBUG]${NC} $1"
}

# Function to create deployment marker in Grafana
create_grafana_marker() {
    local grafana_url=${GRAFANA_URL:-http://localhost:3000}
    local grafana_user=${GRAFANA_USER:-admin}
    local grafana_password=${GRAFANA_PASSWORD:-admin}
    
    info "Creating deployment marker in Grafana..."
    
    local marker_data=$(cat <<EOF
{
  "time": $(date +%s)000,
  "timeEnd": $(date +%s)000,
  "tags": ["deployment", "$ENVIRONMENT", "$VERSION"],
  "text": "Deployment $DEPLOYMENT_STATUS - Version: $VERSION, Environment: $ENVIRONMENT"
}
EOF
)
    
    if curl -s -X POST \
        -H "Content-Type: application/json" \
        -u "$grafana_user:$grafana_password" \
        -d "$marker_data" \
        "$grafana_url/api/annotations" > /dev/null 2>&1; then
        info "✅ Grafana deployment marker created"
    else
        warn "⚠️  Could not create Grafana deployment marker"
    fi
}

# Function to record deployment event in application
record_deployment_event() {
    local endpoint="$SERVICE_URL/api/health/deployment"
    local start_time=${DEPLOYMENT_START_TIME:-$(date +%s)}
    local current_time=$(date +%s)
    local duration=$((($current_time - $start_time) * 1000)) # Convert to milliseconds
    
    info "Recording deployment event in application..."
    debug "Endpoint: $endpoint"
    debug "Status: $DEPLOYMENT_STATUS"
    debug "Duration: ${duration}ms"
    
    local response
    if response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "status=$DEPLOYMENT_STATUS&duration=$duration" \
        "$endpoint" 2>/dev/null); then
        info "✅ Deployment event recorded in application"
        debug "Response: $response"
    else
        warn "⚠️  Could not record deployment event in application"
    fi
}

# Function to check application health after deployment
check_post_deployment_health() {
    local health_endpoint="$SERVICE_URL/api/health/status"
    local max_attempts=30
    local attempt=0
    
    info "Checking post-deployment health..."
    
    while [ $attempt -lt $max_attempts ]; do
        attempt=$((attempt + 1))
        
        if response=$(curl -s "$health_endpoint" 2>/dev/null); then
            local status=$(echo "$response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
            local health_score=$(echo "$response" | jq -r '.deployment.healthScore // 0' 2>/dev/null || echo "0")
            
            if [ "$status" = "UP" ] && [ "$(echo "$health_score >= 80" | bc -l 2>/dev/null || echo 0)" = "1" ]; then
                info "✅ Application is healthy (Score: $health_score)"
                return 0
            else
                warn "Attempt $attempt/$max_attempts - Health: $status, Score: $health_score"
            fi
        else
            warn "Attempt $attempt/$max_attempts - Health check failed"
        fi
        
        sleep 10
    done
    
    error "❌ Application failed health check after deployment"
    return 1
}

# Function to send deployment notification
send_deployment_notification() {
    local webhook_url=${SLACK_WEBHOOK_URL:-}
    local status_emoji
    local status_color
    
    case $DEPLOYMENT_STATUS in
        "success")
            status_emoji="✅"
            status_color="good"
            ;;
        "failure")
            status_emoji="❌"
            status_color="danger"
            ;;
        "start")
            status_emoji="🚀"
            status_color="warning"
            ;;
        *)
            status_emoji="ℹ️"
            status_color="#439FE0"
            ;;
    esac
    
    if [ -n "$webhook_url" ]; then
        info "Sending deployment notification..."
        
        local notification_data=$(cat <<EOF
{
  "attachments": [
    {
      "color": "$status_color",
      "title": "$status_emoji Deployment $DEPLOYMENT_STATUS",
      "fields": [
        {
          "title": "Environment",
          "value": "$ENVIRONMENT",
          "short": true
        },
        {
          "title": "Version",
          "value": "$VERSION",
          "short": true
        },
        {
          "title": "Service",
          "value": "Account Service",
          "short": true
        },
        {
          "title": "Timestamp",
          "value": "$(date -u +"%Y-%m-%d %H:%M:%S UTC")",
          "short": true
        }
      ]
    }
  ]
}
EOF
)
        
        if curl -s -X POST \
            -H "Content-Type: application/json" \
            -d "$notification_data" \
            "$webhook_url" > /dev/null 2>&1; then
            info "✅ Deployment notification sent"
        else
            warn "⚠️  Could not send deployment notification"
        fi
    else
        debug "No webhook URL configured, skipping notification"
    fi
}

# Function to update deployment metrics in Prometheus
update_prometheus_metrics() {
    local prometheus_pushgateway=${PROMETHEUS_PUSHGATEWAY_URL:-}
    
    if [ -n "$prometheus_pushgateway" ]; then
        info "Updating Prometheus metrics..."
        
        local metrics_data="deployment_event{environment=\"$ENVIRONMENT\",version=\"$VERSION\",status=\"$DEPLOYMENT_STATUS\"} 1"
        
        if echo "$metrics_data" | curl -s -X POST \
            --data-binary @- \
            "$prometheus_pushgateway/metrics/job/deployment-tracking/instance/$ENVIRONMENT" > /dev/null 2>&1; then
            info "✅ Prometheus metrics updated"
        else
            warn "⚠️  Could not update Prometheus metrics"
        fi
    else
        debug "No Prometheus Pushgateway configured, skipping metrics update"
    fi
}

# Main execution
main() {
    info "=== Deployment Tracking Script ==="
    info "Environment: $ENVIRONMENT"
    info "Status: $DEPLOYMENT_STATUS"
    info "Version: $VERSION"
    info "Service URL: $SERVICE_URL"
    info "Deployment ID: $DEPLOYMENT_ID"
    
    case $DEPLOYMENT_STATUS in
        "start")
            info "🚀 Starting deployment tracking..."
            export DEPLOYMENT_START_TIME=$(date +%s)
            create_grafana_marker
            send_deployment_notification
            update_prometheus_metrics
            ;;
        "success")
            info "✅ Recording successful deployment..."
            record_deployment_event
            create_grafana_marker
            check_post_deployment_health
            send_deployment_notification
            update_prometheus_metrics
            ;;
        "failure")
            error "❌ Recording failed deployment..."
            record_deployment_event
            create_grafana_marker
            send_deployment_notification
            update_prometheus_metrics
            ;;
        "health-check")
            info "🏥 Performing post-deployment health check..."
            check_post_deployment_health
            ;;
        *)
            error "Unknown deployment status: $DEPLOYMENT_STATUS"
            exit 1
            ;;
    esac
    
    info "Deployment tracking completed"
}

# Execute main function
main "$@"