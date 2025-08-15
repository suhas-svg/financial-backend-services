# Staging Environment Infrastructure
# This configuration sets up the staging environment for the account service

terraform {
  required_version = ">= 1.0"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
  }
}

# Configure Kubernetes provider
provider "kubernetes" {
  config_path    = "~/.kube/config"
  config_context = var.kubernetes_context
}

# Configure Helm provider
provider "helm" {
  kubernetes {
    config_path    = "~/.kube/config"
    config_context = var.kubernetes_context
  }
}

# Create Kubernetes infrastructure
module "kubernetes" {
  source = "../../modules/kubernetes"

  app_name              = var.app_name
  environment           = var.environment
  namespace             = var.namespace
  enable_network_policy = var.enable_network_policy
  enable_resource_quota = var.enable_resource_quota
  
  resource_quota = {
    requests_cpu    = "2"
    requests_memory = "4Gi"
    limits_cpu      = "4"
    limits_memory   = "8Gi"
    pods            = "15"
    services        = "10"
  }

  labels = var.common_labels
}

# Create database infrastructure
module "database" {
  source = "../../modules/database"

  app_name           = var.app_name
  environment        = var.environment
  namespace          = module.kubernetes.namespace_name
  db_name            = var.db_name
  db_username        = var.db_username
  postgres_version   = var.postgres_version
  db_storage_size    = "20Gi"  # Larger storage for staging
  storage_class_name = var.storage_class_name

  db_resources = {
    requests = {
      cpu    = "250m"
      memory = "512Mi"
    }
    limits = {
      cpu    = "1"
      memory = "2Gi"
    }
  }

  # Enhanced database configuration for staging
  db_max_connections        = "200"
  db_shared_buffers        = "512MB"
  db_effective_cache_size  = "2GB"

  depends_on = [module.kubernetes]
}

# Create monitoring infrastructure
module "monitoring" {
  source = "../../modules/monitoring"

  app_name                = var.app_name
  environment             = var.environment
  monitoring_namespace    = var.monitoring_namespace
  app_namespace          = module.kubernetes.namespace_name
  enable_grafana         = var.enable_grafana
  enable_alertmanager    = var.enable_alertmanager
  grafana_admin_password = var.grafana_admin_password
  storage_class_name     = var.storage_class_name
  
  # Production-like resources for staging
  prometheus_storage_size = "50Gi"
  prometheus_retention   = "30d"
  prometheus_resources = {
    requests = {
      cpu    = "500m"
      memory = "2Gi"
    }
    limits = {
      cpu    = "2"
      memory = "4Gi"
    }
  }

  grafana_storage_size = "10Gi"
  alertmanager_storage_size = "10Gi"

  # Enable all monitoring features for staging
  create_service_monitor = true
  create_alerting_rules  = true

  depends_on = [module.kubernetes]
}