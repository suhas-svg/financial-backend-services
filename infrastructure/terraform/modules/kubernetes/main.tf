# Kubernetes Cluster Module
# This module creates a local Kubernetes cluster using Kind for development
# and provides configuration for different environments

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
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }
}

# Create namespace for the application
resource "kubernetes_namespace" "app_namespace" {
  metadata {
    name = var.namespace
    labels = {
      environment = var.environment
      application = var.app_name
    }
  }
}

# Create service account for the application
resource "kubernetes_service_account" "app_service_account" {
  metadata {
    name      = "${var.app_name}-service-account"
    namespace = kubernetes_namespace.app_namespace.metadata[0].name
    labels = {
      environment = var.environment
      application = var.app_name
    }
  }
}

# Create cluster role for the application
resource "kubernetes_cluster_role" "app_cluster_role" {
  metadata {
    name = "${var.app_name}-cluster-role"
    labels = {
      environment = var.environment
      application = var.app_name
    }
  }

  rule {
    api_groups = [""]
    resources  = ["pods", "services", "endpoints"]
    verbs      = ["get", "list", "watch"]
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "replicasets"]
    verbs      = ["get", "list", "watch"]
  }
}

# Bind cluster role to service account
resource "kubernetes_cluster_role_binding" "app_cluster_role_binding" {
  metadata {
    name = "${var.app_name}-cluster-role-binding"
    labels = {
      environment = var.environment
      application = var.app_name
    }
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role.app_cluster_role.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account.app_service_account.metadata[0].name
    namespace = kubernetes_namespace.app_namespace.metadata[0].name
  }
}

# Create network policy for security
resource "kubernetes_network_policy" "app_network_policy" {
  count = var.enable_network_policy ? 1 : 0

  metadata {
    name      = "${var.app_name}-network-policy"
    namespace = kubernetes_namespace.app_namespace.metadata[0].name
    labels = {
      environment = var.environment
      application = var.app_name
    }
  }

  spec {
    pod_selector {
      match_labels = {
        app = var.app_name
      }
    }

    policy_types = ["Ingress", "Egress"]

    ingress {
      from {
        namespace_selector {
          match_labels = {
            name = kubernetes_namespace.app_namespace.metadata[0].name
          }
        }
      }
      
      ports {
        port     = "8080"
        protocol = "TCP"
      }
    }

    egress {
      to {
        namespace_selector {
          match_labels = {
            name = "kube-system"
          }
        }
      }
    }

    egress {
      to {}
      ports {
        port     = "5432"
        protocol = "TCP"
      }
    }
  }
}

# Create resource quota for the namespace
resource "kubernetes_resource_quota" "app_resource_quota" {
  count = var.enable_resource_quota ? 1 : 0

  metadata {
    name      = "${var.app_name}-resource-quota"
    namespace = kubernetes_namespace.app_namespace.metadata[0].name
    labels = {
      environment = var.environment
      application = var.app_name
    }
  }

  spec {
    hard = {
      "requests.cpu"    = var.resource_quota.requests_cpu
      "requests.memory" = var.resource_quota.requests_memory
      "limits.cpu"      = var.resource_quota.limits_cpu
      "limits.memory"   = var.resource_quota.limits_memory
      "pods"            = var.resource_quota.pods
      "services"        = var.resource_quota.services
    }
  }
}