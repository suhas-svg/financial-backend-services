#!/bin/bash

# Transaction Service - Advanced Monitoring Stack Startup Script
# This script starts the complete monitoring and observability stack

set -e

echo "🚀 Starting Transaction Service Advanced Monitoring Stack..."

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
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# Check if Docker and Docker Compose are installed
check_prerequisites() {
    print_header "Checking prerequisites..."
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
    
    print_status "Prerequisites check passed ✓"
}

# Create necessary directories
create_directories() {
    print_header "Creating necessary directories..."
    
    mkdir -p logs
    mkdir -p monitoring/grafana/provisioning/datasources
    mkdir -p monitoring/grafana/provisioning/dashboards
    mkdir -p monitoring/alertmanager
    mkdir -p monitoring/logstash/pipeline
    mkdir -p monitoring/logstash/templates
    mkdir -p scripts
    
    print_status "Directories created ✓"
}

# Create database initialization script
create_db_init() {
    print_header "Creating database initialization script..."
    
    cat > scripts/init-db.sql << 'EOF'
-- Transaction Service Database Initialization

-- Create transactions table
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(255) PRIMARY KEY,
    from_account_id VARCHAR(255),
    to_account_id VARCHAR(255),
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    transaction_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    created_by VARCHAR(255),
    reversal_transaction_id VARCHAR(255)
);

-- Create transaction limits table
CREATE TABLE IF NOT EXISTS transaction_limits (
    id BIGSERIAL PRIMARY KEY,
    account_type VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    daily_limit DECIMAL(19,2),
    monthly_limit DECIMAL(19,2),
    per_transaction_limit DECIMAL(19,2),
    daily_count INTEGER,
    monthly_count INTEGER,
    active BOOLEAN DEFAULT true,
    UNIQUE(account_type, transaction_type)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account ON transactions(to_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_transactions_created_by ON transactions(created_by);

-- Insert default transaction limits
INSERT INTO transaction_limits (account_type, transaction_type, daily_limit, monthly_limit, per_transaction_limit, daily_count, monthly_count)
VALUES 
    ('STANDARD', 'TRANSFER', 10000.00, 50000.00, 5000.00, 50, 200),
    ('STANDARD', 'DEPOSIT', 20000.00, 100000.00, 10000.00, 20, 100),
    ('STANDARD', 'WITHDRAWAL', 5000.00, 25000.00, 2500.00, 10, 50),
    ('PREMIUM', 'TRANSFER', 50000.00, 250000.00, 25000.00, 100, 500),
    ('PREMIUM', 'DEPOSIT', 100000.00, 500000.00, 50000.00, 50, 250),
    ('PREMIUM', 'WITHDRAWAL', 25000.00, 125000.00, 12500.00, 25, 125)
ON CONFLICT (account_type, transaction_type) DO NOTHING;

-- Create a view for transaction statistics
CREATE OR REPLACE VIEW transaction_stats AS
SELECT 
    DATE(created_at) as transaction_date,
    transaction_type,
    status,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount
FROM transactions
GROUP BY DATE(created_at), transaction_type, status;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
EOF
    
    print_status "Database initialization script created ✓"
}

# Create Elasticsearch template
create_elasticsearch_template() {
    print_header "Creating Elasticsearch template..."
    
    mkdir -p monitoring/logstash/templates
    
    cat > monitoring/logstash/templates/transaction-service-template.json << 'EOF'
{
  "index_patterns": ["transaction-service-logs-*"],
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "index.refresh_interval": "5s"
  },
  "mappings": {
    "properties": {
      "@timestamp": {
        "type": "date"
      },
      "level": {
        "type": "keyword"
      },
      "logger": {
        "type": "keyword"
      },
      "message": {
        "type": "text",
        "analyzer": "standard"
      },
      "service": {
        "type": "keyword"
      },
      "environment": {
        "type": "keyword"
      },
      "correlation_id": {
        "type": "keyword"
      },
      "trace_id": {
        "type": "keyword"
      },
      "span_id": {
        "type": "keyword"
      },
      "user_id": {
        "type": "keyword"
      },
      "transaction_id": {
        "type": "keyword"
      },
      "transaction_type": {
        "type": "keyword"
      },
      "amount": {
        "type": "double"
      },
      "processing_time": {
        "type": "integer"
      },
      "account_service_duration": {
        "type": "integer"
      },
      "log_category": {
        "type": "keyword"
      },
      "alert_level": {
        "type": "keyword"
      }
    }
  }
}
EOF
    
    print_status "Elasticsearch template created ✓"
}

# Build the application
build_application() {
    print_header "Building Transaction Service application..."
    
    if [ -f "pom.xml" ]; then
        print_status "Building with Maven..."
        ./mvnw clean package -DskipTests
        print_status "Application built successfully ✓"
    else
        print_warning "No pom.xml found, skipping build"
    fi
}

# Start the monitoring stack
start_stack() {
    print_header "Starting monitoring stack..."
    
    # Stop any existing containers
    print_status "Stopping existing containers..."
    docker-compose -f docker-compose-monitoring.yml down --remove-orphans
    
    # Start the stack
    print_status "Starting containers..."
    docker-compose -f docker-compose-monitoring.yml up -d
    
    print_status "Monitoring stack started ✓"
}

# Wait for services to be ready
wait_for_services() {
    print_header "Waiting for services to be ready..."
    
    services=(
        "postgres:5432"
        "redis:6379"
        "prometheus:9090"
        "grafana:3000"
        "zipkin:9411"
        "alertmanager:9093"
        "elasticsearch:9200"
    )
    
    for service in "${services[@]}"; do
        IFS=':' read -r host port <<< "$service"
        print_status "Waiting for $host:$port..."
        
        timeout=60
        while ! docker-compose -f docker-compose-monitoring.yml exec -T $host nc -z localhost $port 2>/dev/null; do
            sleep 2
            timeout=$((timeout - 2))
            if [ $timeout -le 0 ]; then
                print_warning "Timeout waiting for $host:$port"
                break
            fi
        done
        
        if [ $timeout -gt 0 ]; then
            print_status "$host:$port is ready ✓"
        fi
    done
}

# Display service URLs
display_urls() {
    print_header "Service URLs:"
    
    echo ""
    echo -e "${GREEN}📊 Monitoring Services:${NC}"
    echo "  • Transaction Service:    http://localhost:8081"
    echo "  • Prometheus:            http://localhost:9090"
    echo "  • Grafana:               http://localhost:3000 (admin/admin)"
    echo "  • Alertmanager:          http://localhost:9093"
    echo ""
    echo -e "${GREEN}🔍 Tracing Services:${NC}"
    echo "  • Zipkin:                http://localhost:9411"
    echo "  • Jaeger:                http://localhost:16686"
    echo ""
    echo -e "${GREEN}📝 Logging Services:${NC}"
    echo "  • Elasticsearch:         http://localhost:9200"
    echo "  • Kibana:                http://localhost:5601"
    echo ""
    echo -e "${GREEN}💾 Data Services:${NC}"
    echo "  • PostgreSQL:            localhost:5433 (postgres/postgres)"
    echo "  • Redis:                 localhost:6379"
    echo ""
    echo -e "${GREEN}📈 System Monitoring:${NC}"
    echo "  • Node Exporter:         http://localhost:9100"
    echo "  • cAdvisor:              http://localhost:8080"
    echo ""
    echo -e "${GREEN}🔧 API Endpoints:${NC}"
    echo "  • Health Check:          http://localhost:8081/actuator/health"
    echo "  • Metrics:               http://localhost:8081/actuator/metrics"
    echo "  • Prometheus Metrics:    http://localhost:8081/actuator/prometheus"
    echo "  • Detailed Health:       http://localhost:8081/api/monitoring/health/detailed"
    echo "  • Transaction Stats:     http://localhost:8081/api/monitoring/stats/transactions"
    echo "  • System Stats:          http://localhost:8081/api/monitoring/stats/system"
    echo "  • Alert Status:          http://localhost:8081/api/monitoring/alerts/status"
    echo ""
}

# Display helpful commands
display_commands() {
    print_header "Useful Commands:"
    
    echo ""
    echo -e "${GREEN}📋 Docker Commands:${NC}"
    echo "  • View logs:             docker-compose -f docker-compose-monitoring.yml logs -f [service]"
    echo "  • Stop stack:            docker-compose -f docker-compose-monitoring.yml down"
    echo "  • Restart service:       docker-compose -f docker-compose-monitoring.yml restart [service]"
    echo "  • View containers:       docker-compose -f docker-compose-monitoring.yml ps"
    echo ""
    echo -e "${GREEN}🔍 Monitoring Commands:${NC}"
    echo "  • Test metrics:          curl http://localhost:8081/actuator/prometheus"
    echo "  • Check health:          curl http://localhost:8081/actuator/health"
    echo "  • View alerts:           curl http://localhost:9093/api/v1/alerts"
    echo "  • Query Prometheus:      curl 'http://localhost:9090/api/v1/query?query=up'"
    echo ""
    echo -e "${GREEN}🧪 Testing Commands:${NC}"
    echo "  • Run tests:             ./mvnw test"
    echo "  • Load test:             ./mvnw test -Dtest=*PerformanceTest"
    echo "  • Integration test:      ./mvnw test -Dtest=*IntegrationTest"
    echo ""
}

# Main execution
main() {
    echo "🏦 Transaction Service - Advanced Monitoring & Observability Stack"
    echo "=================================================================="
    echo ""
    
    check_prerequisites
    create_directories
    create_db_init
    create_elasticsearch_template
    build_application
    start_stack
    wait_for_services
    
    echo ""
    echo -e "${GREEN}🎉 Monitoring stack started successfully!${NC}"
    echo ""
    
    display_urls
    display_commands
    
    echo ""
    echo -e "${BLUE}💡 Next Steps:${NC}"
    echo "  1. Import the Grafana dashboard from src/main/resources/grafana-dashboard.json"
    echo "  2. Configure alert notification channels in Alertmanager"
    echo "  3. Set up log shipping to Elasticsearch"
    echo "  4. Configure Prometheus scraping for additional services"
    echo "  5. Test the monitoring setup with sample transactions"
    echo ""
    echo -e "${GREEN}✅ Setup complete! Happy monitoring! 🚀${NC}"
}

# Run main function
main "$@"