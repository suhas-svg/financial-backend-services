# Variables for Development Environment

variable "app_name" {
  description = "Name of the application"
  type        = string
  default     = "account-service"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "namespace" {
  description = "Kubernetes namespace for the application"
  type        = string
  default     = "account-service-dev"
}

variable "monitoring_namespace" {
  description = "Kubernetes namespace for monitoring"
  type        = string
  default     = "monitoring-dev"
}

variable "kubernetes_context" {
  description = "Kubernetes context to use"
  type        = string
  default     = "docker-desktop"
}

variable "storage_class_name" {
  description = "Storage class name for persistent volumes"
  type        = string
  default     = "hostpath"
}

variable "enable_network_policy" {
  description = "Enable network policy for security"
  type        = bool
  default     = false  # Disabled for local development
}

variable "enable_resource_quota" {
  description = "Enable resource quota for the namespace"
  type        = bool
  default     = false  # Disabled for local development
}

variable "db_name" {
  description = "Name of the PostgreSQL database"
  type        = string
  default     = "myfirstdb"
}

variable "db_username" {
  description = "Username for the PostgreSQL database"
  type        = string
  default     = "postgres"
}

variable "postgres_version" {
  description = "PostgreSQL version to use"
  type        = string
  default     = "15-alpine"
}

variable "enable_grafana" {
  description = "Enable Grafana deployment"
  type        = bool
  default     = true
}

variable "enable_alertmanager" {
  description = "Enable Alertmanager deployment"
  type        = bool
  default     = false  # Disabled for development
}

variable "grafana_admin_password" {
  description = "Admin password for Grafana"
  type        = string
  default     = "admin"
  sensitive   = true
}

variable "common_labels" {
  description = "Common labels to apply to all resources"
  type        = map(string)
  default = {
    project     = "account-service"
    environment = "dev"
    managed-by  = "terraform"
  }
}