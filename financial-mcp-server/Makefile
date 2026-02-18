# MCP Financial Server - Makefile
# Cross-platform deployment and management commands

.PHONY: help dev staging prod test clean health validate

# Default target
help:
	@echo "MCP Financial Server - Available Commands"
	@echo "========================================"
	@echo ""
	@echo "Development:"
	@echo "  make dev          - Deploy development environment"
	@echo "  make dev-logs     - View development logs"
	@echo "  make dev-stop     - Stop development environment"
	@echo ""
	@echo "Staging:"
	@echo "  make staging      - Deploy to staging"
	@echo "  make staging-test - Run staging tests"
	@echo "  make staging-rollback - Rollback staging deployment"
	@echo ""
	@echo "Production:"
	@echo "  make prod         - Deploy to production"
	@echo "  make prod-status  - Check production status"
	@echo "  make prod-rollback - Rollback production deployment"
	@echo ""
	@echo "Testing & Validation:"
	@echo "  make test         - Run all tests"
	@echo "  make health       - Run health checks"
	@echo "  make validate     - Validate configuration"
	@echo ""
	@echo "Maintenance:"
	@echo "  make clean        - Clean up containers and images"
	@echo "  make backup       - Create backup"
	@echo "  make logs         - View service logs"

# Development environment
dev:
	@echo "🚀 Deploying development environment..."
	@if [ -f "scripts/deploy-dev.sh" ]; then \
		chmod +x scripts/deploy-dev.sh && ./scripts/deploy-dev.sh; \
	else \
		powershell -ExecutionPolicy Bypass -File scripts/deploy-dev.ps1; \
	fi

dev-logs:
	docker-compose -f docker-compose.dev.yml logs -f

dev-stop:
	docker-compose -f docker-compose.dev.yml down

# Staging environment
staging:
	@echo "🚀 Deploying to staging..."
	@if [ -f "scripts/deploy-staging.sh" ]; then \
		chmod +x scripts/deploy-staging.sh && ./scripts/deploy-staging.sh; \
	else \
		powershell -ExecutionPolicy Bypass -File scripts/deploy-staging.ps1; \
	fi

staging-test:
	@if [ -f "scripts/deploy-staging.sh" ]; then \
		./scripts/deploy-staging.sh test; \
	else \
		powershell -ExecutionPolicy Bypass -File scripts/deploy-staging.ps1 -Action test; \
	fi

staging-rollback:
	@if [ -f "scripts/deploy-staging.sh" ]; then \
		./scripts/deploy-staging.sh rollback; \
	else \
		powershell -ExecutionPolicy Bypass -File scripts/deploy-staging.ps1 -Action rollback; \
	fi

# Production environment
prod:
	@echo "🚀 Deploying to production..."
	@echo "⚠️  WARNING: This will deploy to PRODUCTION!"
	@read -p "Are you sure? (yes/no): " confirm && [ "$$confirm" = "yes" ] || exit 1
	@if [ -f "scripts/deploy-prod.sh" ]; then \
		chmod +x scripts/deploy-prod.sh && ./scripts/deploy-prod.sh; \
	else \
		powershell -ExecutionPolicy Bypass -File scripts/deploy-prod.ps1; \
	fi

prod-status:
	@if [ -f "scripts/deploy-prod.sh" ]; then \
		./scripts/deploy-prod.sh status; \
	else \
		powershell -ExecutionPolicy Bypass -File scripts/deploy-prod.ps1 -Action status; \
	fi

prod-rollback:
	@if [ -f "scripts/deploy-prod.sh" ]; then \
		./scripts/deploy-prod.sh rollback; \
	else \
		powershell -ExecutionPolicy Bypass -File scripts/deploy-prod.ps1 -Action rollback; \
	fi

# Testing and validation
test:
	@echo "🧪 Running tests..."
	python -m pytest tests/ -v

health:
	@echo "🏥 Running health checks..."
	@if [ -f "scripts/health-check.sh" ]; then \
		chmod +x scripts/health-check.sh && ./scripts/health-check.sh full; \
	else \
		powershell -ExecutionPolicy Bypass -File scripts/health-check.ps1; \
	fi

validate:
	@echo "✅ Validating configuration..."
	python scripts/validate-config.py --env development

validate-staging:
	python scripts/validate-config.py --env staging --env-file .env.staging

validate-prod:
	python scripts/validate-config.py --env production --env-file .env.prod

# Maintenance
clean:
	@echo "🧹 Cleaning up..."
	docker system prune -f
	docker volume prune -f
	docker image prune -f

backup:
	@echo "💾 Creating backup..."
	@mkdir -p backups
	@tar -czf backups/config-backup-$$(date +%Y%m%d-%H%M%S).tar.gz \
		.env.* docker-compose*.yml monitoring/ scripts/
	@echo "Backup created in backups/ directory"

logs:
	docker-compose logs -f --tail=100

# Build targets
build:
	docker build -t mcp-financial-server:latest .

build-dev:
	docker build --target development -t mcp-financial-server:dev .

build-prod:
	docker build --target production -t mcp-financial-server:prod .

# Docker Compose shortcuts
up:
	docker-compose up -d

down:
	docker-compose down

restart:
	docker-compose restart mcp-financial-server

# Monitoring
metrics:
	@echo "📊 Opening metrics..."
	@echo "Prometheus: http://localhost:9091"
	@echo "Grafana: http://localhost:3000"
	@echo "MCP Metrics: http://localhost:9090/metrics"

# Development helpers
shell:
	docker-compose exec mcp-financial-server bash

install-deps:
	pip install -r requirements.txt

format:
	black src/ tests/
	flake8 src/ tests/

lint:
	mypy src/
	flake8 src/ tests/

# Environment setup
setup-dev:
	@echo "🔧 Setting up development environment..."
	@cp .env.example .env.dev
	@echo "Please edit .env.dev with your configuration"

setup-staging:
	@echo "🔧 Setting up staging environment..."
	@cp .env.example .env.staging
	@echo "Please edit .env.staging with your configuration"

setup-prod:
	@echo "🔧 Setting up production environment..."
	@cp .env.example .env.prod
	@echo "Please edit .env.prod with your configuration"

# Quick start
quick-start: setup-dev dev
	@echo "🎉 Development environment is ready!"
	@echo "MCP Server: http://localhost:8082"
	@echo "Health Check: http://localhost:8082/health"