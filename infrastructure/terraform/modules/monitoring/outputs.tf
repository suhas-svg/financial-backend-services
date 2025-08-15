# Outputs for Monitoring module

output "monitoring_namespace" {
  description = "Name of the monitoring namespace"
  value       = kubernetes_namespace.monitoring.metadata[0].name
}

output "prometheus_release_name" {
  description = "Name of the Prometheus Helm release"
  value       = helm_release.prometheus.name
}

output "prometheus_service_name" {
  description = "Name of the Prometheus service"
  value       = "${helm_release.prometheus.name}-kube-prometheus-prometheus"
}

output "grafana_service_name" {
  description = "Name of the Grafana service"
  value       = "${helm_release.prometheus.name}-grafana"
}

output "alertmanager_service_name" {
  description = "Name of the Alertmanager service"
  value       = "${helm_release.prometheus.name}-kube-prometheus-alertmanager"
}

output "service_monitor_name" {
  description = "Name of the ServiceMonitor for the application"
  value       = var.create_service_monitor ? kubernetes_manifest.account_service_monitor[0].manifest.metadata.name : null
}

output "prometheus_rule_name" {
  description = "Name of the PrometheusRule for alerting"
  value       = var.create_alerting_rules ? kubernetes_manifest.account_service_alerts[0].manifest.metadata.name : null
}

output "grafana_admin_password" {
  description = "Grafana admin password"
  value       = var.grafana_admin_password
  sensitive   = true
}

output "prometheus_url" {
  description = "Prometheus service URL"
  value       = "http://${helm_release.prometheus.name}-kube-prometheus-prometheus.${kubernetes_namespace.monitoring.metadata[0].name}.svc.cluster.local:9090"
}

output "grafana_url" {
  description = "Grafana service URL"
  value       = "http://${helm_release.prometheus.name}-grafana.${kubernetes_namespace.monitoring.metadata[0].name}.svc.cluster.local:80"
}

output "alertmanager_url" {
  description = "Alertmanager service URL"
  value       = "http://${helm_release.prometheus.name}-kube-prometheus-alertmanager.${kubernetes_namespace.monitoring.metadata[0].name}.svc.cluster.local:9093"
}