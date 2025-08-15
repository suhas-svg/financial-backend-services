# Development Environment Infrastructure
# This configuration sets up the development environment for the account service

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

# Configure Kubernetes provider for local development
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
    requests_cpu    = "1"
    requests_memory = "2Gi"
    limits_cpu      = "2"
    limits_memory   = "4Gi"
    pods            = "10"
    services        = "5"
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
  db_storage_size    = "5Gi"  # Smaller storage for dev
  storage_class_name = var.storage_class_name

  db_resources = {
    requests = {
      cpu    = "100m"
      memory = "256Mi"
    }
    limits = {
      cpu    = "500m"
      memory = "1Gi"
    }
  }

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
  
  # Smaller resources for development
  prometheus_storage_size = "10Gi"
  prometheus_resources = {
    requests = {
      cpu    = "200m"
      memory = "1Gi"
    }
    limits = {
      cpu    = "1"
      memory = "2Gi"
    }
  }

  grafana_storage_size = "2Gi"
  alertmanager_storage_size = "2Gi"

  depends_on = [module.kubernetes]
}