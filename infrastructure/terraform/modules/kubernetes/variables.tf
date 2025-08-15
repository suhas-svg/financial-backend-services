# Variables for Kubernetes module

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

variable "namespace" {
  description = "Kubernetes namespace for the application"
  type        = string
  default     = "account-service"
}

variable "enable_network_policy" {
  description = "Enable network policy for security"
  type        = bool
  default     = true
}

variable "enable_resource_quota" {
  description = "Enable resource quota for the namespace"
  type        = bool
  default     = true
}

variable "resource_quota" {
  description = "Resource quota configuration"
  type = object({
    requests_cpu    = string
    requests_memory = string
    limits_cpu      = string
    limits_memory   = string
    pods            = string
    services        = string
  })
  default = {
    requests_cpu    = "2"
    requests_memory = "4Gi"
    limits_cpu      = "4"
    limits_memory   = "8Gi"
    pods            = "10"
    services        = "5"
  }
}

variable "labels" {
  description = "Additional labels to apply to resources"
  type        = map(string)
  default     = {}
}

variable "annotations" {
  description = "Additional annotations to apply to resources"
  type        = map(string)
  default     = {}
}