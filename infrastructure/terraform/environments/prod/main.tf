# Production Environment Infrastructure
# This configuration sets up the production environment for the account service

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

  # Remote state configuration for production
  backend "local" {
    path = "terraform.tfstate"
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
    requests_cpu    = "4"
    requests_memory = "8Gi"
    limits_cpu      = "8"
    limits_memory   = "16Gi"
    pods            = "20"
    services        = "15"
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
  db_storage_size    = "100Gi"  # Large storage for production
  storage_class_name = var.storage_class_name

  db_resources = {
    requests = {
      cpu    = "500m"
      memory = "1Gi"
    }
    limits = {
      cpu    = "2"
      memory = "4Gi"
    }
  }

  # Production database configuration
  db_max_connections        = "500"
  db_shared_buffers        = "1GB"
  db_effective_cache_size  = "4GB"

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
  
  # Production monitoring resources
  prometheus_storage_size = "200Gi"
  prometheus_retention   = "90d"
  prometheus_resources = {
    requests = {
      cpu    = "1"
      memory = "4Gi"
    }
    limits = {
      cpu    = "4"
      memory = "8Gi"
    }
  }

  grafana_storage_size = "20Gi"
  grafana_resources = {
    requests = {
      cpu    = "200m"
      memory = "256Mi"
    }
    limits = {
      cpu    = "1"
      memory = "1Gi"
    }
  }

  alertmanager_storage_size = "20Gi"
  alertmanager_resources = {
    requests = {
      cpu    = "200m"
      memory = "256Mi"
    }
    limits = {
      cpu    = "500m"
      memory = "512Mi"
    }
  }

  # Enable all monitoring features for production
  create_service_monitor = true
  create_alerting_rules  = true
  enable_node_exporter   = true
  enable_kube_state_metrics = true

  depends_on = [module.kubernetes]
}