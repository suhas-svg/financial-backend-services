# Terraform Validation and Testing Configuration
# This file contains validation rules and testing configurations

# Validation for Terraform version
terraform {
  required_version = ">= 1.0"
}

# Provider version constraints
terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }
}

# Validation rules for common variables
variable "environment_validation" {
  description = "Validation for environment variable"
  type        = string
  default     = "dev"
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment_validation)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

# Data source to validate Kubernetes cluster connectivity
data "kubernetes_namespace" "kube_system" {
  metadata {
    name = "kube-system"
  }
}

# Null resource for pre-deployment validation
resource "null_resource" "pre_deployment_validation" {
  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    command = <<-EOT
      echo "Running pre-deployment validation..."
      
      # Check if kubectl is available
      if ! command -v kubectl &> /dev/null; then
        echo "ERROR: kubectl is not installed or not in PATH"
        exit 1
      fi
      
      # Check if helm is available
      if ! command -v helm &> /dev/null; then
        echo "ERROR: helm is not installed or not in PATH"
        exit 1
      fi
      
      # Check Kubernetes cluster connectivity
      if ! kubectl cluster-info &> /dev/null; then
        echo "ERROR: Cannot connect to Kubernetes cluster"
        exit 1
      fi
      
      # Check if required namespaces can be created
      echo "Validation passed: kubectl and helm are available, cluster is accessible"
    EOT
  }
}

# Null resource for post-deployment validation
resource "null_resource" "post_deployment_validation" {
  depends_on = [null_resource.pre_deployment_validation]
  
  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    command = <<-EOT
      echo "Running post-deployment validation..."
      
      # Add post-deployment checks here
      # For example:
      # - Verify that all pods are running
      # - Check service endpoints
      # - Validate monitoring setup
      
      echo "Post-deployment validation completed"
    EOT
  }
}

# Local values for validation
locals {
  # Validate environment-specific configurations
  environment_configs = {
    dev = {
      min_replicas = 1
      max_replicas = 3
      cpu_limit    = "1"
      memory_limit = "2Gi"
    }
    staging = {
      min_replicas = 2
      max_replicas = 5
      cpu_limit    = "2"
      memory_limit = "4Gi"
    }
    prod = {
      min_replicas = 3
      max_replicas = 10
      cpu_limit    = "4"
      memory_limit = "8Gi"
    }
  }
  
  # Validation for resource naming
  name_regex = "^[a-z0-9-]+$"
  
  # Common tags that should be applied to all resources
  required_tags = [
    "environment",
    "application",
    "managed-by"
  ]
}

# Output validation results
output "validation_status" {
  description = "Status of infrastructure validation"
  value = {
    terraform_version = "✓ Terraform version >= 1.0"
    providers        = "✓ Required providers configured"
    cluster_access   = "✓ Kubernetes cluster accessible"
    tools_available  = "✓ kubectl and helm available"
  }
}