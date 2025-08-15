# Variables for Database module

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
  description = "Kubernetes namespace for the database"
  type        = string
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

variable "db_password" {
  description = "Password for the PostgreSQL database (leave empty to generate random)"
  type        = string
  default     = ""
  sensitive   = true
}

variable "postgres_version" {
  description = "PostgreSQL version to use"
  type        = string
  default     = "15-alpine"
}

variable "db_storage_size" {
  description = "Size of the database storage"
  type        = string
  default     = "10Gi"
}

variable "storage_class_name" {
  description = "Storage class name for the database PVC"
  type        = string
  default     = "standard"
}

variable "db_max_connections" {
  description = "Maximum number of database connections"
  type        = string
  default     = "100"
}

variable "db_shared_buffers" {
  description = "PostgreSQL shared_buffers setting"
  type        = string
  default     = "256MB"
}

variable "db_effective_cache_size" {
  description = "PostgreSQL effective_cache_size setting"
  type        = string
  default     = "1GB"
}

variable "db_resources" {
  description = "Resource requests and limits for the database"
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
      cpu    = "250m"
      memory = "512Mi"
    }
    limits = {
      cpu    = "1"
      memory = "2Gi"
    }
  }
}