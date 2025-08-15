# Variables for Production Environment

variable "app_name" {
  description = "Name of the application"
  type        = string
  default     = "account-service"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "namespace" {
  description = "Kubernetes namespace for the application"
  type        = string
  default     = "account-service-prod"
}

variable "monitoring_namespace" {
  description = "Kubernetes namespace for monitoring"
  type        = string
  default     = "monitoring-prod"
}

variable "kubernetes_context" {
  description = "Kubernetes context to use"
  type        = string
  default     = "production-cluster"
}

variable "storage_class_name" {
  description = "Storage class name for persistent volumes"
  type        = string
  default     = "fast-ssd"
}

variable "enable_network_policy" {
  description = "Enable network policy for security"
  type        = bool
  default     = true  # Always enabled for production
}

variable "enable_resource_quota" {
  description = "Enable resource quota for the namespace"
  type        = bool
  default     = true  # Always enabled for production
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
  default     = true  # Always enabled for production
}

variable "grafana_admin_password" {
  description = "Admin password for Grafana"
  type        = string
  sensitive   = true
  validation {
    condition     = length(var.grafana_admin_password) >= 12
    error_message = "Grafana admin password must be at least 12 characters long for production."
  }
}

variable "common_labels" {
  description = "Common labels to apply to all resources"
  type        = map(string)
  default = {
    project     = "account-service"
    environment = "prod"
    managed-by  = "terraform"
    criticality = "high"
  }
}