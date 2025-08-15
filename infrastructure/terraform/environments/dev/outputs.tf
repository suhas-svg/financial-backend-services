# Outputs for Development Environment

output "namespace_name" {
  description = "Name of the application namespace"
  value       = module.kubernetes.namespace_name
}

output "monitoring_namespace" {
  description = "Name of the monitoring namespace"
  value       = module.monitoring.monitoring_namespace
}

output "database_host" {
  description = "Database host"
  value       = module.database.db_host
}

output "database_port" {
  description = "Database port"
  value       = module.database.db_port
}

output "database_name" {
  description = "Database name"
  value       = module.database.db_name
}

output "database_secret_name" {
  description = "Name of the database credentials secret"
  value       = module.database.db_secret_name
}

output "prometheus_url" {
  description = "Prometheus service URL"
  value       = module.monitoring.prometheus_url
}

output "grafana_url" {
  description = "Grafana service URL"
  value       = module.monitoring.grafana_url
}

output "grafana_admin_password" {
  description = "Grafana admin password"
  value       = module.monitoring.grafana_admin_password
  sensitive   = true
}

output "service_account_name" {
  description = "Name of the application service account"
  value       = module.kubernetes.service_account_name
}

# Connection information for the application
output "connection_info" {
  description = "Connection information for the application"
  value = {
    database = {
      host   = module.database.db_host
      port   = module.database.db_port
      name   = module.database.db_name
      secret = module.database.db_secret_name
    }
    monitoring = {
      prometheus_url = module.monitoring.prometheus_url
      grafana_url    = module.monitoring.grafana_url
    }
    kubernetes = {
      namespace           = module.kubernetes.namespace_name
      service_account     = module.kubernetes.service_account_name
      monitoring_namespace = module.monitoring.monitoring_namespace
    }
  }
}