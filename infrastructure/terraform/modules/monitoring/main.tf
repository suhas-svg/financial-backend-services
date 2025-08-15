# Monitoring Module for Prometheus and Grafana
# This module sets up monitoring infrastructure using Prometheus and Grafana

terraform {
  required_version = ">= 1.0"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
  }
}

# Create monitoring namespace
resource "kubernetes_namespace" "monitoring" {
  metadata {
    name = var.monitoring_namespace
    labels = {
      environment = var.environment
      component   = "monitoring"
    }
  }
}

# Install Prometheus using Helm
resource "helm_release" "prometheus" {
  name       = "prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name
  version    = var.prometheus_chart_version

  values = [
    yamlencode({
      prometheus = {
        prometheusSpec = {
          retention = var.prometheus_retention
          storageSpec = {
            volumeClaimTemplate = {
              spec = {
                storageClassName = var.storage_class_name
                accessModes      = ["ReadWriteOnce"]
                resources = {
                  requests = {
                    storage = var.prometheus_storage_size
                  }
                }
              }
            }
          }
          resources = var.prometheus_resources
          serviceMonitorSelectorNilUsesHelmValues = false
          podMonitorSelectorNilUsesHelmValues     = false
          ruleSelectorNilUsesHelmValues           = false
        }
      }
      
      grafana = {
        enabled = var.enable_grafana
        adminPassword = var.grafana_admin_password
        persistence = {
          enabled = true
          storageClassName = var.storage_class_name
          size = var.grafana_storage_size
        }
        resources = var.grafana_resources
        dashboardProviders = {
          "dashboardproviders.yaml" = {
            apiVersion = 1
            providers = [
              {
                name = "default"
                orgId = 1
                folder = ""
                type = "file"
                disableDeletion = false
                editable = true
                options = {
                  path = "/var/lib/grafana/dashboards/default"
                }
              }
            ]
          }
        }
        dashboards = {
          default = {
            "spring-boot-dashboard" = {
              gnetId = 12900
              revision = 1
              datasource = "Prometheus"
            }
            "jvm-dashboard" = {
              gnetId = 4701
              revision = 1
              datasource = "Prometheus"
            }
            "kubernetes-cluster-dashboard" = {
              gnetId = 7249
              revision = 1
              datasource = "Prometheus"
            }
          }
        }
      }
      
      alertmanager = {
        enabled = var.enable_alertmanager
        alertmanagerSpec = {
          storage = {
            volumeClaimTemplate = {
              spec = {
                storageClassName = var.storage_class_name
                accessModes      = ["ReadWriteOnce"]
                resources = {
                  requests = {
                    storage = var.alertmanager_storage_size
                  }
                }
              }
            }
          }
          resources = var.alertmanager_resources
        }
      }
      
      nodeExporter = {
        enabled = var.enable_node_exporter
      }
      
      kubeStateMetrics = {
        enabled = var.enable_kube_state_metrics
      }
    })
  ]

  depends_on = [kubernetes_namespace.monitoring]
}

# Create ServiceMonitor for the account service
resource "kubernetes_manifest" "account_service_monitor" {
  count = var.create_service_monitor ? 1 : 0
  
  manifest = {
    apiVersion = "monitoring.coreos.com/v1"
    kind       = "ServiceMonitor"
    metadata = {
      name      = "${var.app_name}-service-monitor"
      namespace = kubernetes_namespace.monitoring.metadata[0].name
      labels = {
        environment = var.environment
        application = var.app_name
        component   = "monitoring"
      }
    }
    spec = {
      selector = {
        matchLabels = {
          app = var.app_name
        }
      }
      namespaceSelector = {
        matchNames = [var.app_namespace]
      }
      endpoints = [
        {
          port = "actuator"
          path = "/actuator/prometheus"
          interval = "30s"
          scrapeTimeout = "10s"
        }
      ]
    }
  }

  depends_on = [helm_release.prometheus]
}

# Create PrometheusRule for alerting
resource "kubernetes_manifest" "account_service_alerts" {
  count = var.create_alerting_rules ? 1 : 0
  
  manifest = {
    apiVersion = "monitoring.coreos.com/v1"
    kind       = "PrometheusRule"
    metadata = {
      name      = "${var.app_name}-alerts"
      namespace = kubernetes_namespace.monitoring.metadata[0].name
      labels = {
        environment = var.environment
        application = var.app_name
        component   = "monitoring"
        prometheus  = "kube-prometheus"
        role        = "alert-rules"
      }
    }
    spec = {
      groups = [
        {
          name = "${var.app_name}-alerts"
          rules = [
            {
              alert = "AccountServiceDown"
              expr  = "up{job=\"${var.app_name}\"} == 0"
              for   = "1m"
              labels = {
                severity = "critical"
                service  = var.app_name
              }
              annotations = {
                summary     = "Account Service is down"
                description = "Account Service has been down for more than 1 minute"
              }
            },
            {
              alert = "AccountServiceHighErrorRate"
              expr  = "rate(http_server_requests_seconds_count{job=\"${var.app_name}\",status=~\"5..\"}[5m]) > 0.1"
              for   = "2m"
              labels = {
                severity = "warning"
                service  = var.app_name
              }
              annotations = {
                summary     = "High error rate in Account Service"
                description = "Account Service error rate is above 10% for more than 2 minutes"
              }
            },
            {
              alert = "AccountServiceHighResponseTime"
              expr  = "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job=\"${var.app_name}\"}[5m])) > 2"
              for   = "5m"
              labels = {
                severity = "warning"
                service  = var.app_name
              }
              annotations = {
                summary     = "High response time in Account Service"
                description = "Account Service 95th percentile response time is above 2 seconds for more than 5 minutes"
              }
            },
            {
              alert = "AccountServiceHighMemoryUsage"
              expr  = "jvm_memory_used_bytes{job=\"${var.app_name}\",area=\"heap\"} / jvm_memory_max_bytes{job=\"${var.app_name}\",area=\"heap\"} > 0.8"
              for   = "5m"
              labels = {
                severity = "warning"
                service  = var.app_name
              }
              annotations = {
                summary     = "High memory usage in Account Service"
                description = "Account Service heap memory usage is above 80% for more than 5 minutes"
              }
            }
          ]
        }
      ]
    }
  }

  depends_on = [helm_release.prometheus]
}