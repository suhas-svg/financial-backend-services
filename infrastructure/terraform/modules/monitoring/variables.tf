# Variables for Monitoring module

variable "app_name" {
  description = "Name of the application"
  type        = string
  default     = "account-service"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "monitoring_namespace" {
  description = "Kubernetes namespace for monitoring components"
  type        = string
  default     = "monitoring"
}

variable "app_namespace" {
  description = "Kubernetes namespace where the application is deployed"
  type        = string
  default     = "account-service"
}

variable "prometheus_chart_version" {
  description = "Version of the Prometheus Helm chart"
  type        = string
  default     = "51.2.0"
}

variable "prometheus_retention" {
  description = "Prometheus data retention period"
  type        = string
  default     = "15d"
}

variable "prometheus_storage_size" {
  description = "Storage size for Prometheus"
  type        = string
  default     = "50Gi"
}

variable "prometheus_resources" {
  description = "Resource requests and limits for Prometheus"
  type = object({
    requests = object({
      cpu    = string
      memory = string
    })
    limits = object({
      cpu    = string
      memory = string
    })
  })
  default = {
    requests = {
      cpu    = "500m"
      memory = "2Gi"
    }
    limits = {
      cpu    = "2"
      memory = "4Gi"
    }
  }
}

variable "enable_grafana" {
  description = "Enable Grafana deployment"
  type        = bool
  default     = true
}

variable "grafana_admin_password" {
  description = "Admin password for Grafana"
  type        = string
  default     = "admin"
  sensitive   = true
}

variable "grafana_storage_size" {
  description = "Storage size for Grafana"
  type        = string
  default     = "10Gi"
}

variable "grafana_resources" {
  description = "Resource requests and limits for Grafana"
  type = object({
    requests = object({
      cpu    = string
      memory = string
    })
    limits = object({
      cpu    = string
      memory = string
    })
  })
  default = {
    requests = {
      cpu    = "100m"
      memory = "128Mi"
    }
    limits = {
      cpu    = "500m"
      memory = "512Mi"
    }
  }
}

variable "enable_alertmanager" {
  description = "Enable Alertmanager deployment"
  type        = bool
  default     = true
}

variable "alertmanager_storage_size" {
  description = "Storage size for Alertmanager"
  type        = string
  default     = "10Gi"
}

variable "alertmanager_resources" {
  description = "Resource requests and limits for Alertmanager"
  type = object({
    requests = object({
      cpu    = string
      memory = string
    })
    limits = object({
      cpu    = string
      memory = string
    })
  })
  default = {
    requests = {
      cpu    = "100m"
      memory = "128Mi"
    }
    limits = {
      cpu    = "200m"
      memory = "256Mi"
    }
  }
}

variable "enable_node_exporter" {
  description = "Enable Node Exporter"
  type        = bool
  default     = true
}

variable "enable_kube_state_metrics" {
  description = "Enable Kube State Metrics"
  type        = bool
  default     = true
}

variable "storage_class_name" {
  description = "Storage class name for persistent volumes"
  type        = string
  default     = "standard"
}

variable "create_service_monitor" {
  description = "Create ServiceMonitor for the application"
  type        = bool
  default     = true
}

variable "create_alerting_rules" {
  description = "Create PrometheusRule for alerting"
  type        = bool
  default     = true
}