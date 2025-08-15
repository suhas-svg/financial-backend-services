# Outputs for Production Environment

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

output "alertmanager_url" {
  description = "Alertmanager service URL"
  value       = module.monitoring.alertmanager_url
}

output "service_account_name" {
  description = "Name of the application service account"
  value       = module.kubernetes.service_account_name
}

output "network_policy_name" {
  description = "Name of the network policy"
  value       = module.kubernetes.network_policy_name
}

output "resource_quota_name" {
  description = "Name of the resource quota"
  value       = module.kubernetes.resource_quota_name
}

output "service_monitor_name" {
  description = "Name of the ServiceMonitor"
  value       = module.monitoring.service_monitor_name
}

output "prometheus_rule_name" {
  description = "Name of the PrometheusRule"
  value       = module.monitoring.prometheus_rule_name
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
      prometheus_url   = module.monitoring.prometheus_url
      grafana_url      = module.monitoring.grafana_url
      alertmanager_url = module.monitoring.alertmanager_url
    }
    kubernetes = {
      namespace            = module.kubernetes.namespace_name
      service_account      = module.kubernetes.service_account_name
      monitoring_namespace = module.monitoring.monitoring_namespace
      network_policy       = module.kubernetes.network_policy_name
      resource_quota       = module.kubernetes.resource_quota_name
      service_monitor      = module.monitoring.service_monitor_name
      prometheus_rule      = module.monitoring.prometheus_rule_name
    }
  }
}