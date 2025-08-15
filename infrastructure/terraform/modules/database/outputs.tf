# Outputs for Database module

output "db_secret_name" {
  description = "Name of the database credentials secret"
  value       = kubernetes_secret.db_credentials.metadata[0].name
}

output "db_config_map_name" {
  description = "Name of the database configuration ConfigMap"
  value       = kubernetes_config_map.db_config.metadata[0].name
}

output "db_service_name" {
  description = "Name of the database service"
  value       = kubernetes_service.postgres.metadata[0].name
}

output "db_deployment_name" {
  description = "Name of the database deployment"
  value       = kubernetes_deployment.postgres.metadata[0].name
}

output "db_pvc_name" {
  description = "Name of the database PVC"
  value       = kubernetes_persistent_volume_claim.db_storage.metadata[0].name
}

output "db_host" {
  description = "Database host (service name)"
  value       = kubernetes_service.postgres.metadata[0].name
}

output "db_port" {
  description = "Database port"
  value       = 5432
}

output "db_name" {
  description = "Database name"
  value       = var.db_name
}

output "db_username" {
  description = "Database username"
  value       = var.db_username
  sensitive   = true
}