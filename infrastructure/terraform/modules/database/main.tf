# Database Module for PostgreSQL
# This module creates PostgreSQL database resources in Kubernetes

terraform {
  required_version = ">= 1.0"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
  }
}

# Generate random password for database
resource "random_password" "db_password" {
  length  = 16
  special = true
}

# Create secret for database credentials
resource "kubernetes_secret" "db_credentials" {
  metadata {
    name      = "${var.app_name}-db-credentials"
    namespace = var.namespace
    labels = {
      environment = var.environment
      application = var.app_name
      component   = "database"
    }
  }

  data = {
    username = var.db_username
    password = var.db_password != "" ? var.db_password : random_password.db_password.result
    database = var.db_name
    host     = "${var.app_name}-postgres"
    port     = "5432"
  }

  type = "Opaque"
}

# Create ConfigMap for database configuration
resource "kubernetes_config_map" "db_config" {
  metadata {
    name      = "${var.app_name}-db-config"
    namespace = var.namespace
    labels = {
      environment = var.environment
      application = var.app_name
      component   = "database"
    }
  }

  data = {
    POSTGRES_DB                = var.db_name
    POSTGRES_USER              = var.db_username
    POSTGRES_MAX_CONNECTIONS   = var.db_max_connections
    POSTGRES_SHARED_BUFFERS    = var.db_shared_buffers
    POSTGRES_EFFECTIVE_CACHE_SIZE = var.db_effective_cache_size
  }
}

# Create PersistentVolumeClaim for database storage
resource "kubernetes_persistent_volume_claim" "db_storage" {
  metadata {
    name      = "${var.app_name}-db-storage"
    namespace = var.namespace
    labels = {
      environment = var.environment
      application = var.app_name
      component   = "database"
    }
  }

  spec {
    access_modes = ["ReadWriteOnce"]
    
    resources {
      requests = {
        storage = var.db_storage_size
      }
    }

    storage_class_name = var.storage_class_name
  }
}

# Create PostgreSQL Deployment
resource "kubernetes_deployment" "postgres" {
  metadata {
    name      = "${var.app_name}-postgres"
    namespace = var.namespace
    labels = {
      environment = var.environment
      application = var.app_name
      component   = "database"
      app         = "${var.app_name}-postgres"
    }
  }

  spec {
    replicas = 1
    
    selector {
      match_labels = {
        app = "${var.app_name}-postgres"
      }
    }

    template {
      metadata {
        labels = {
          environment = var.environment
          application = var.app_name
          component   = "database"
          app         = "${var.app_name}-postgres"
        }
      }

      spec {
        container {
          name  = "postgres"
          image = "postgres:${var.postgres_version}"

          port {
            container_port = 5432
            name          = "postgres"
          }

          env_from {
            config_map_ref {
              name = kubernetes_config_map.db_config.metadata[0].name
            }
          }

          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.db_credentials.metadata[0].name
                key  = "password"
              }
            }
          }

          volume_mount {
            name       = "postgres-storage"
            mount_path = "/var/lib/postgresql/data"
          }

          resources {
            requests = {
              cpu    = var.db_resources.requests.cpu
              memory = var.db_resources.requests.memory
            }
            limits = {
              cpu    = var.db_resources.limits.cpu
              memory = var.db_resources.limits.memory
            }
          }

          liveness_probe {
            exec {
              command = ["pg_isready", "-U", var.db_username, "-d", var.db_name]
            }
            initial_delay_seconds = 30
            period_seconds        = 10
            timeout_seconds       = 5
            failure_threshold     = 3
          }

          readiness_probe {
            exec {
              command = ["pg_isready", "-U", var.db_username, "-d", var.db_name]
            }
            initial_delay_seconds = 5
            period_seconds        = 5
            timeout_seconds       = 3
            failure_threshold     = 3
          }
        }

        volume {
          name = "postgres-storage"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.db_storage.metadata[0].name
          }
        }

        restart_policy = "Always"
      }
    }
  }
}

# Create PostgreSQL Service
resource "kubernetes_service" "postgres" {
  metadata {
    name      = "${var.app_name}-postgres"
    namespace = var.namespace
    labels = {
      environment = var.environment
      application = var.app_name
      component   = "database"
    }
  }

  spec {
    selector = {
      app = "${var.app_name}-postgres"
    }

    port {
      name        = "postgres"
      port        = 5432
      target_port = 5432
      protocol    = "TCP"
    }

    type = "ClusterIP"
  }
}