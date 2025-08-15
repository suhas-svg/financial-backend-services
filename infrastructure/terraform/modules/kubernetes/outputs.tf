# Outputs for Kubernetes module

output "namespace_name" {
  description = "Name of the created namespace"
  value       = kubernetes_namespace.app_namespace.metadata[0].name
}

output "service_account_name" {
  description = "Name of the created service account"
  value       = kubernetes_service_account.app_service_account.metadata[0].name
}

output "cluster_role_name" {
  description = "Name of the created cluster role"
  value       = kubernetes_cluster_role.app_cluster_role.metadata[0].name
}

output "cluster_role_binding_name" {
  description = "Name of the created cluster role binding"
  value       = kubernetes_cluster_role_binding.app_cluster_role_binding.metadata[0].name
}

output "network_policy_name" {
  description = "Name of the created network policy"
  value       = var.enable_network_policy ? kubernetes_network_policy.app_network_policy[0].metadata[0].name : null
}

output "resource_quota_name" {
  description = "Name of the created resource quota"
  value       = var.enable_resource_quota ? kubernetes_resource_quota.app_resource_quota[0].metadata[0].name : null
}

output "namespace_labels" {
  description = "Labels applied to the namespace"
  value       = kubernetes_namespace.app_namespace.metadata[0].labels
}

output "namespace_annotations" {
  description = "Annotations applied to the namespace"
  value       = kubernetes_namespace.app_namespace.metadata[0].annotations
}