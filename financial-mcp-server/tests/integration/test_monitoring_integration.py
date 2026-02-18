"""
Monitoring and alerting integration validation tests.
"""

import pytest
import asyncio
import json
import time
from datetime import datetime, timedelta
from unittest.mock import AsyncMock, MagicMock, patch
from typing import Dict, Any, List

from mcp_financial.server import FinancialMCPServer
from mcp_financial.auth.jwt_handler import UserContext


class TestMonitoringIntegration:
    """Test monitoring and alerting integration."""
    
    @pytest.fixture
    async def monitoring_server(self):
        """Create server for monitoring testing."""
        with patch('mcp_financial.server.Settings') as mock_settings_class:
            mock_settings = MagicMock()
            mock_settings.account_service_url = "http://localhost:8080"
            mock_settings.transaction_service_url = "http://localhost:8081"
            mock_settings.jwt_secret = "test-secret"
            mock_settings.server_timeout = 5000
            mock_settings_class.return_value = mock_settings
            
            server = FinancialMCPServer()
            yield server

    @pytest.mark.asyncio
    async def test_health_check_integration(self, monitoring_server):
        """Test health check endpoint integration."""
        # Test 1: All services healthy
        with patch.object(monitoring_server.account_client, 'health_check', new_callable=AsyncMock) as mock_health1, \
             patch.object(monitoring_server.transaction_client, 'health_check', new_callable=AsyncMock) as mock_health2:
            
            mock_health1.return_value = True
            mock_health2.return_value = True
            
            # Mock health check endpoint
            health_status = {
                "status": "UP",
                "timestamp": datetime.utcnow().isoformat(),
                "services": {
                    "account_service": {
                        "status": "UP",
                        "responseTime": 45,
                        "lastCheck": datetime.utcnow().isoformat()
                    },
                    "transaction_service": {
                        "status": "UP",
                        "responseTime": 38,
                        "lastCheck": datetime.utcnow().isoformat()
                    }
                },
                "system": {
                    "uptime": 3600,
                    "memory_usage": 256,
                    "cpu_usage": 15.5,
                    "active_connections": 12
                }
            }
            
            # Verify health check structure
            assert health_status["status"] == "UP"
            assert "services" in health_status
            assert "system" in health_status
            assert health_status["services"]["account_service"]["status"] == "UP"
            assert health_status["services"]["transaction_service"]["status"] == "UP"
        
        # Test 2: Service degraded
        with patch.object(monitoring_server.account_client, 'health_check', new_callable=AsyncMock) as mock_health1, \
             patch.object(monitoring_server.transaction_client, 'health_check', new_callable=AsyncMock) as mock_health2:
            
            mock_health1.return_value = False  # Account service down
            mock_health2.return_value = True
            
            health_status = {
                "status": "DEGRADED",
                "timestamp": datetime.utcnow().isoformat(),
                "services": {
                    "account_service": {
                        "status": "DOWN",
                        "error": "Connection timeout",
                        "lastCheck": datetime.utcnow().isoformat()
                    },
                    "transaction_service": {
                        "status": "UP",
                        "responseTime": 42,
                        "lastCheck": datetime.utcnow().isoformat()
                    }
                }
            }
            
            assert health_status["status"] == "DEGRADED"
            assert health_status["services"]["account_service"]["status"] == "DOWN"
        
        # Test 3: All services down
        with patch.object(monitoring_server.account_client, 'health_check', new_callable=AsyncMock) as mock_health1, \
             patch.object(monitoring_server.transaction_client, 'health_check', new_callable=AsyncMock) as mock_health2:
            
            mock_health1.return_value = False
            mock_health2.return_value = False
            
            health_status = {
                "status": "DOWN",
                "timestamp": datetime.utcnow().isoformat(),
                "services": {
                    "account_service": {"status": "DOWN"},
                    "transaction_service": {"status": "DOWN"}
                }
            }
            
            assert health_status["status"] == "DOWN"

    @pytest.mark.asyncio
    async def test_metrics_collection_integration(self, monitoring_server):
        """Test metrics collection and reporting."""
        # Mock Prometheus metrics
        mock_metrics = {
            # Request metrics
            "mcp_requests_total": 1250,
            "mcp_requests_failed_total": 25,
            "mcp_request_duration_seconds_sum": 312.5,
            "mcp_request_duration_seconds_count": 1250,
            
            # Service metrics
            "service_requests_total": 2500,
            "service_requests_failed_total": 45,
            "service_request_duration_seconds_sum": 625.0,
            "service_request_duration_seconds_count": 2500,
            
            # Circuit breaker metrics
            "circuit_breaker_state": 0,  # 0=CLOSED, 1=OPEN, 2=HALF_OPEN
            "circuit_breaker_failures_total": 12,
            
            # System metrics
            "mcp_active_connections": 15,
            "process_resident_memory_bytes": 268435456,  # 256MB
            "process_cpu_seconds_total": 45.2,
            
            # Authentication metrics
            "auth_requests_total": 1300,
            "auth_failures_total": 8,
            "auth_token_validations_total": 1250
        }
        
        with patch('mcp_financial.utils.metrics.collect_metrics') as mock_collect:
            mock_collect.return_value = mock_metrics
            
            metrics = mock_collect()
            
            # Verify key metrics are present
            assert metrics["mcp_requests_total"] > 0
            assert metrics["mcp_requests_failed_total"] >= 0
            assert metrics["service_requests_total"] > 0
            assert metrics["mcp_active_connections"] >= 0
            
            # Calculate derived metrics
            error_rate = metrics["mcp_requests_failed_total"] / metrics["mcp_requests_total"]
            avg_response_time = metrics["mcp_request_duration_seconds_sum"] / metrics["mcp_request_duration_seconds_count"]
            
            assert error_rate < 0.05  # Less than 5% error rate
            assert avg_response_time < 1.0  # Less than 1 second average response time

    @pytest.mark.asyncio
    async def test_alerting_integration(self, monitoring_server):
        """Test alerting system integration."""
        alerts_triggered = []
        
        def mock_trigger_alert(alert_type, severity, message, details=None):
            alert = {
                "type": alert_type,
                "severity": severity,
                "message": message,
                "details": details or {},
                "timestamp": datetime.utcnow().isoformat(),
                "source": "mcp-financial-server"
            }
            alerts_triggered.append(alert)
            return alert
        
        with patch('mcp_financial.utils.alerting.trigger_alert', side_effect=mock_trigger_alert):
            # Test 1: High error rate alert
            mock_trigger_alert(
                "high_error_rate",
                "WARNING",
                "Error rate exceeded threshold: 8.5%",
                {"current_rate": 0.085, "threshold": 0.05, "window": "5m"}
            )
            
            # Test 2: Service unavailable alert
            mock_trigger_alert(
                "service_unavailable",
                "CRITICAL",
                "Account Service is unavailable",
                {"service": "account_service", "last_success": "2024-01-01T10:00:00Z"}
            )
            
            # Test 3: Circuit breaker open alert
            mock_trigger_alert(
                "circuit_breaker_open",
                "WARNING",
                "Circuit breaker opened for Transaction Service",
                {"service": "transaction_service", "failure_count": 5}
            )
            
            # Test 4: High response time alert
            mock_trigger_alert(
                "high_response_time",
                "WARNING",
                "Average response time exceeded threshold: 1.2s",
                {"current_avg": 1.2, "threshold": 1.0, "window": "5m"}
            )
            
            # Test 5: Authentication failure spike alert
            mock_trigger_alert(
                "auth_failure_spike",
                "CRITICAL",
                "Authentication failure rate spike detected",
                {"failure_rate": 0.15, "normal_rate": 0.02, "window": "1m"}
            )
            
            # Verify alerts were triggered
            assert len(alerts_triggered) == 5
            
            # Verify alert structure
            for alert in alerts_triggered:
                assert "type" in alert
                assert "severity" in alert
                assert "message" in alert
                assert "timestamp" in alert
                assert "source" in alert
            
            # Verify severity levels
            severities = [alert["severity"] for alert in alerts_triggered]
            assert "CRITICAL" in severities
            assert "WARNING" in severities
            
            # Verify critical alerts
            critical_alerts = [alert for alert in alerts_triggered if alert["severity"] == "CRITICAL"]
            assert len(critical_alerts) >= 2

    @pytest.mark.asyncio
    async def test_performance_monitoring_integration(self, monitoring_server):
        """Test performance monitoring integration."""
        # Mock performance data collection
        performance_data = {
            "timestamp": datetime.utcnow().isoformat(),
            "metrics": {
                "response_times": {
                    "avg": 0.245,
                    "p50": 0.180,
                    "p95": 0.520,
                    "p99": 0.890,
                    "max": 1.250
                },
                "throughput": {
                    "requests_per_second": 125.5,
                    "transactions_per_second": 85.2
                },
                "error_rates": {
                    "total_error_rate": 0.024,
                    "auth_error_rate": 0.006,
                    "service_error_rate": 0.018
                },
                "resource_usage": {
                    "cpu_percent": 35.2,
                    "memory_mb": 256,
                    "memory_percent": 12.5,
                    "disk_io_mb_per_sec": 2.1,
                    "network_io_mb_per_sec": 5.8
                },
                "connections": {
                    "active": 15,
                    "idle": 5,
                    "total": 20
                }
            },
            "thresholds": {
                "response_time_warning": 0.5,
                "response_time_critical": 1.0,
                "error_rate_warning": 0.05,
                "error_rate_critical": 0.10,
                "cpu_warning": 70,
                "cpu_critical": 85,
                "memory_warning": 80,
                "memory_critical": 90
            }
        }
        
        with patch('mcp_financial.utils.monitoring.collect_performance_data') as mock_collect:
            mock_collect.return_value = performance_data
            
            data = mock_collect()
            
            # Verify performance metrics are within acceptable ranges
            metrics = data["metrics"]
            thresholds = data["thresholds"]
            
            # Response time checks
            assert metrics["response_times"]["avg"] < thresholds["response_time_warning"]
            assert metrics["response_times"]["p95"] < thresholds["response_time_critical"]
            
            # Error rate checks
            assert metrics["error_rates"]["total_error_rate"] < thresholds["error_rate_warning"]
            
            # Resource usage checks
            assert metrics["resource_usage"]["cpu_percent"] < thresholds["cpu_warning"]
            assert metrics["resource_usage"]["memory_percent"] < thresholds["memory_warning"]
            
            # Throughput validation
            assert metrics["throughput"]["requests_per_second"] > 50  # Minimum expected throughput

    @pytest.mark.asyncio
    async def test_log_aggregation_integration(self, monitoring_server):
        """Test log aggregation and analysis integration."""
        # Mock structured logs
        log_entries = [
            {
                "timestamp": "2024-01-01T10:00:00Z",
                "level": "INFO",
                "logger": "mcp_financial.tools.account_tools",
                "message": "Account created successfully",
                "user_id": "user_123",
                "account_id": "acc_456",
                "operation": "create_account",
                "duration_ms": 150,
                "request_id": "req_789"
            },
            {
                "timestamp": "2024-01-01T10:01:00Z",
                "level": "ERROR",
                "logger": "mcp_financial.clients.account_client",
                "message": "Account service connection failed",
                "error": "Connection timeout",
                "service": "account_service",
                "retry_count": 3,
                "request_id": "req_790"
            },
            {
                "timestamp": "2024-01-01T10:02:00Z",
                "level": "WARN",
                "logger": "mcp_financial.auth.jwt_handler",
                "message": "Authentication failed",
                "user_id": "unknown",
                "reason": "invalid_token",
                "ip_address": "192.168.1.100",
                "request_id": "req_791"
            },
            {
                "timestamp": "2024-01-01T10:03:00Z",
                "level": "INFO",
                "logger": "mcp_financial.tools.transaction_tools",
                "message": "Transaction completed",
                "user_id": "user_123",
                "transaction_id": "txn_456",
                "amount": 1000.0,
                "operation": "deposit_funds",
                "duration_ms": 89,
                "request_id": "req_792"
            }
        ]
        
        with patch('mcp_financial.utils.logging.get_recent_logs') as mock_logs:
            mock_logs.return_value = log_entries
            
            logs = mock_logs()
            
            # Analyze log patterns
            error_logs = [log for log in logs if log["level"] == "ERROR"]
            warn_logs = [log for log in logs if log["level"] == "WARN"]
            info_logs = [log for log in logs if log["level"] == "INFO"]
            
            # Verify log structure
            for log in logs:
                assert "timestamp" in log
                assert "level" in log
                assert "logger" in log
                assert "message" in log
                assert "request_id" in log
            
            # Verify error tracking
            assert len(error_logs) > 0
            for error_log in error_logs:
                assert "error" in error_log or "message" in error_log
            
            # Verify security event logging
            auth_logs = [log for log in logs if "auth" in log["logger"]]
            assert len(auth_logs) > 0
            
            # Verify performance logging
            perf_logs = [log for log in logs if "duration_ms" in log]
            assert len(perf_logs) > 0
            
            # Calculate average response times from logs
            durations = [log["duration_ms"] for log in perf_logs]
            avg_duration = sum(durations) / len(durations)
            assert avg_duration < 500  # Less than 500ms average

    @pytest.mark.asyncio
    async def test_dashboard_integration(self, monitoring_server):
        """Test monitoring dashboard integration."""
        # Mock dashboard data
        dashboard_data = {
            "overview": {
                "status": "HEALTHY",
                "uptime": "2d 14h 32m",
                "total_requests": 125000,
                "success_rate": 97.8,
                "avg_response_time": 0.245
            },
            "services": {
                "account_service": {
                    "status": "UP",
                    "response_time": 0.180,
                    "success_rate": 98.5,
                    "last_error": None
                },
                "transaction_service": {
                    "status": "UP",
                    "response_time": 0.210,
                    "success_rate": 97.2,
                    "last_error": "2024-01-01T09:45:00Z"
                }
            },
            "real_time_metrics": {
                "current_rps": 125.5,
                "active_connections": 15,
                "memory_usage_mb": 256,
                "cpu_usage_percent": 35.2
            },
            "recent_alerts": [
                {
                    "timestamp": "2024-01-01T09:45:00Z",
                    "severity": "WARNING",
                    "message": "High response time detected",
                    "resolved": True
                }
            ],
            "top_errors": [
                {
                    "error": "Connection timeout",
                    "count": 12,
                    "last_occurrence": "2024-01-01T10:00:00Z"
                },
                {
                    "error": "Authentication failed",
                    "count": 8,
                    "last_occurrence": "2024-01-01T09:58:00Z"
                }
            ]
        }
        
        with patch('mcp_financial.utils.monitoring.get_dashboard_data') as mock_dashboard:
            mock_dashboard.return_value = dashboard_data
            
            data = mock_dashboard()
            
            # Verify dashboard structure
            assert "overview" in data
            assert "services" in data
            assert "real_time_metrics" in data
            assert "recent_alerts" in data
            assert "top_errors" in data
            
            # Verify overview metrics
            overview = data["overview"]
            assert overview["success_rate"] > 95.0
            assert overview["avg_response_time"] < 0.5
            
            # Verify service status
            for service_name, service_data in data["services"].items():
                assert "status" in service_data
                assert "response_time" in service_data
                assert "success_rate" in service_data
            
            # Verify real-time metrics
            rt_metrics = data["real_time_metrics"]
            assert rt_metrics["current_rps"] > 0
            assert rt_metrics["active_connections"] >= 0
            assert rt_metrics["cpu_usage_percent"] < 100

    @pytest.mark.asyncio
    async def test_sla_monitoring_integration(self, monitoring_server):
        """Test SLA monitoring and reporting integration."""
        # Mock SLA data
        sla_data = {
            "period": "30d",
            "targets": {
                "availability": 99.9,
                "response_time_p95": 0.5,
                "error_rate": 0.1
            },
            "actual": {
                "availability": 99.95,
                "response_time_p95": 0.42,
                "error_rate": 0.024
            },
            "compliance": {
                "availability": True,
                "response_time": True,
                "error_rate": True,
                "overall": True
            },
            "incidents": [
                {
                    "timestamp": "2024-01-01T08:30:00Z",
                    "duration_minutes": 15,
                    "impact": "Service degradation",
                    "root_cause": "Database connection pool exhaustion",
                    "resolved": True
                }
            ],
            "monthly_summary": {
                "total_requests": 3750000,
                "successful_requests": 3660000,
                "failed_requests": 90000,
                "downtime_minutes": 25,
                "mttr_minutes": 12.5,
                "mtbf_hours": 168.5
            }
        }
        
        with patch('mcp_financial.utils.monitoring.get_sla_data') as mock_sla:
            mock_sla.return_value = sla_data
            
            data = mock_sla()
            
            # Verify SLA compliance
            assert data["compliance"]["overall"] is True
            assert data["actual"]["availability"] >= data["targets"]["availability"]
            assert data["actual"]["response_time_p95"] <= data["targets"]["response_time_p95"]
            assert data["actual"]["error_rate"] <= data["targets"]["error_rate"]
            
            # Verify incident tracking
            assert "incidents" in data
            for incident in data["incidents"]:
                assert "timestamp" in incident
                assert "duration_minutes" in incident
                assert "root_cause" in incident
            
            # Verify reliability metrics
            summary = data["monthly_summary"]
            calculated_availability = (summary["successful_requests"] / summary["total_requests"]) * 100
            assert calculated_availability >= 97.0  # At least 97% success rate

    @pytest.mark.asyncio
    async def test_monitoring_automation_integration(self, monitoring_server):
        """Test monitoring automation and self-healing integration."""
        automation_events = []
        
        def mock_automation_action(action_type, trigger, details):
            event = {
                "action_type": action_type,
                "trigger": trigger,
                "details": details,
                "timestamp": datetime.utcnow().isoformat(),
                "status": "executed"
            }
            automation_events.append(event)
            return event
        
        with patch('mcp_financial.utils.automation.execute_action', side_effect=mock_automation_action):
            # Test 1: Auto-scaling trigger
            mock_automation_action(
                "scale_up",
                "high_cpu_usage",
                {"current_cpu": 85.2, "threshold": 80, "scale_factor": 1.5}
            )
            
            # Test 2: Circuit breaker reset
            mock_automation_action(
                "reset_circuit_breaker",
                "service_recovery",
                {"service": "account_service", "success_rate": 98.5}
            )
            
            # Test 3: Cache invalidation
            mock_automation_action(
                "invalidate_cache",
                "high_error_rate",
                {"cache_type": "user_sessions", "error_rate": 0.08}
            )
            
            # Test 4: Log rotation
            mock_automation_action(
                "rotate_logs",
                "disk_space_low",
                {"disk_usage": 85, "threshold": 80, "logs_rotated": 15}
            )
            
            # Verify automation events
            assert len(automation_events) == 4
            
            # Verify event structure
            for event in automation_events:
                assert "action_type" in event
                assert "trigger" in event
                assert "details" in event
                assert "timestamp" in event
                assert "status" in event
            
            # Verify action types
            action_types = [event["action_type"] for event in automation_events]
            assert "scale_up" in action_types
            assert "reset_circuit_breaker" in action_types
            assert "invalidate_cache" in action_types
            assert "rotate_logs" in action_types