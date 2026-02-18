#!/usr/bin/env python3
"""
Comprehensive system integration validation script.
Validates all aspects of the MCP Financial Server integration.
"""

import asyncio
import json
import sys
import time
import subprocess
import argparse
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Any, Optional
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class SystemValidator:
    """Comprehensive system validation orchestrator."""
    
    def __init__(self, output_dir: str = "validation-results"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)
        self.results = {
            "timestamp": datetime.utcnow().isoformat(),
            "validation_suites": [],
            "summary": {
                "total_tests": 0,
                "passed_tests": 0,
                "failed_tests": 0,
                "success_rate": 0.0,
                "total_duration": 0.0
            }
        }
    
    def run_validation_suite(self, suite_name: str, test_path: str, description: str) -> Dict[str, Any]:
        """Run a validation test suite."""
        logger.info(f"🧪 Running {suite_name}: {description}")
        
        start_time = time.time()
        
        try:
            # Build pytest command
            cmd = [
                sys.executable, "-m", "pytest",
                test_path,
                "-v",
                "--tb=short",
                "--json-report",
                f"--json-report-file={self.output_dir}/{suite_name.lower().replace(' ', '-')}-report.json",
                "--html", f"{self.output_dir}/{suite_name.lower().replace(' ', '-')}-report.html",
                "--self-contained-html"
            ]
            
            # Run the test
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=300  # 5 minute timeout
            )
            
            end_time = time.time()
            duration = end_time - start_time
            
            # Parse results
            suite_result = {
                "name": suite_name,
                "description": description,
                "test_path": test_path,
                "duration": duration,
                "exit_code": result.returncode,
                "status": "PASSED" if result.returncode == 0 else "FAILED",
                "stdout": result.stdout,
                "stderr": result.stderr
            }
            
            # Try to parse JSON report for detailed results
            json_report_path = self.output_dir / f"{suite_name.lower().replace(' ', '-')}-report.json"
            if json_report_path.exists():
                try:
                    with open(json_report_path, 'r') as f:
                        json_report = json.load(f)
                        suite_result.update({
                            "tests_collected": json_report.get("summary", {}).get("total", 0),
                            "tests_passed": json_report.get("summary", {}).get("passed", 0),
                            "tests_failed": json_report.get("summary", {}).get("failed", 0),
                            "tests_error": json_report.get("summary", {}).get("error", 0)
                        })
                except Exception as e:
                    logger.warning(f"Could not parse JSON report for {suite_name}: {e}")
            
            if result.returncode == 0:
                logger.info(f"✅ {suite_name}: PASSED ({duration:.2f}s)")
            else:
                logger.error(f"❌ {suite_name}: FAILED ({duration:.2f}s)")
                if result.stderr:
                    logger.error(f"Error output: {result.stderr[:500]}...")
            
            return suite_result
            
        except subprocess.TimeoutExpired:
            end_time = time.time()
            duration = end_time - start_time
            
            logger.error(f"⏰ {suite_name}: TIMEOUT ({duration:.2f}s)")
            
            return {
                "name": suite_name,
                "description": description,
                "test_path": test_path,
                "duration": duration,
                "exit_code": -1,
                "status": "TIMEOUT",
                "error": "Test suite timed out after 5 minutes"
            }
            
        except Exception as e:
            end_time = time.time()
            duration = end_time - start_time
            
            logger.error(f"💥 {suite_name}: ERROR - {e}")
            
            return {
                "name": suite_name,
                "description": description,
                "test_path": test_path,
                "duration": duration,
                "exit_code": -1,
                "status": "ERROR",
                "error": str(e)
            }
    
    def run_all_validations(self) -> Dict[str, Any]:
        """Run all system validation suites."""
        logger.info("🚀 Starting comprehensive system validation")
        
        # Define validation suites
        validation_suites = [
            {
                "name": "System Integration",
                "path": "tests/integration/test_system_validation.py",
                "description": "End-to-end integration with financial services"
            },
            {
                "name": "Security Validation",
                "path": "tests/integration/test_security_validation.py",
                "description": "Security testing and vulnerability assessment"
            },
            {
                "name": "JWT Compatibility",
                "path": "tests/integration/test_jwt_compatibility.py",
                "description": "JWT authentication flow validation"
            },
            {
                "name": "MCP Protocol Compliance",
                "path": "tests/e2e/test_mcp_protocol_compliance.py",
                "description": "MCP client integration scenarios"
            },
            {
                "name": "Monitoring Integration",
                "path": "tests/integration/test_monitoring_integration.py",
                "description": "Monitoring and alerting integration"
            },
            {
                "name": "Service Clients",
                "path": "tests/integration/test_service_clients.py",
                "description": "HTTP service client integration"
            },
            {
                "name": "Error Scenarios",
                "path": "tests/integration/test_error_scenarios.py",
                "description": "Error handling and recovery scenarios"
            }
        ]
        
        # Run each validation suite
        for suite in validation_suites:
            result = self.run_validation_suite(
                suite["name"],
                suite["path"],
                suite["description"]
            )
            self.results["validation_suites"].append(result)
        
        # Calculate summary
        self.calculate_summary()
        
        return self.results
    
    def calculate_summary(self):
        """Calculate validation summary statistics."""
        total_tests = 0
        passed_tests = 0
        failed_tests = 0
        total_duration = 0.0
        
        for suite in self.results["validation_suites"]:
            # Count tests from detailed results if available
            if "tests_collected" in suite:
                total_tests += suite["tests_collected"]
                passed_tests += suite.get("tests_passed", 0)
                failed_tests += suite.get("tests_failed", 0) + suite.get("tests_error", 0)
            else:
                # Fallback to suite-level status
                total_tests += 1
                if suite["status"] == "PASSED":
                    passed_tests += 1
                else:
                    failed_tests += 1
            
            total_duration += suite["duration"]
        
        success_rate = (passed_tests / total_tests * 100) if total_tests > 0 else 0
        
        self.results["summary"] = {
            "total_tests": total_tests,
            "passed_tests": passed_tests,
            "failed_tests": failed_tests,
            "success_rate": round(success_rate, 2),
            "total_duration": round(total_duration, 2)
        }
    
    def generate_report(self) -> str:
        """Generate comprehensive validation report."""
        report_path = self.output_dir / "system-validation-report.json"
        
        with open(report_path, 'w') as f:
            json.dump(self.results, f, indent=2)
        
        # Generate HTML report
        html_report_path = self.output_dir / "system-validation-report.html"
        self.generate_html_report(html_report_path)
        
        logger.info(f"📄 Reports generated:")
        logger.info(f"   JSON: {report_path}")
        logger.info(f"   HTML: {html_report_path}")
        
        return str(report_path)
    
    def generate_html_report(self, output_path: Path):
        """Generate HTML validation report."""
        summary = self.results["summary"]
        suites = self.results["validation_suites"]
        
        html_content = f"""
<!DOCTYPE html>
<html>
<head>
    <title>MCP Financial Server - System Validation Report</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }}
        .container {{ max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        .header {{ text-align: center; color: #333; border-bottom: 2px solid #007acc; padding-bottom: 20px; margin-bottom: 30px; }}
        .summary {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-bottom: 30px; }}
        .metric {{ background: #f8f9fa; padding: 15px; border-radius: 6px; text-align: center; border-left: 4px solid #007acc; }}
        .metric-value {{ font-size: 2em; font-weight: bold; color: #007acc; }}
        .metric-label {{ color: #666; margin-top: 5px; }}
        .section {{ margin-bottom: 30px; }}
        .section h2 {{ color: #333; border-bottom: 1px solid #ddd; padding-bottom: 10px; }}
        .suite-grid {{ display: grid; gap: 15px; }}
        .suite-item {{ background: #f8f9fa; padding: 15px; border-radius: 6px; border-left: 4px solid #28a745; }}
        .suite-item.failed {{ border-left-color: #dc3545; }}
        .suite-item.error {{ border-left-color: #ffc107; }}
        .suite-item.timeout {{ border-left-color: #fd7e14; }}
        .suite-name {{ font-weight: bold; color: #333; }}
        .suite-status {{ float: right; padding: 4px 8px; border-radius: 4px; color: white; font-size: 0.9em; }}
        .status-passed {{ background-color: #28a745; }}
        .status-failed {{ background-color: #dc3545; }}
        .status-error {{ background-color: #ffc107; }}
        .status-timeout {{ background-color: #fd7e14; }}
        .suite-description {{ color: #666; margin: 5px 0; }}
        .suite-duration {{ color: #666; font-size: 0.9em; }}
        .suite-details {{ margin-top: 10px; font-size: 0.9em; }}
        .footer {{ text-align: center; color: #666; margin-top: 30px; padding-top: 20px; border-top: 1px solid #ddd; }}
        .status-indicator {{ display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 8px; }}
        .status-indicator.passed {{ background-color: #28a745; }}
        .status-indicator.failed {{ background-color: #dc3545; }}
        .status-indicator.error {{ background-color: #ffc107; }}
        .status-indicator.timeout {{ background-color: #fd7e14; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🧪 MCP Financial Server</h1>
            <h2>System Validation Report</h2>
            <p>Generated on {self.results['timestamp']}</p>
        </div>
        
        <div class="summary">
            <div class="metric">
                <div class="metric-value">{summary['total_tests']}</div>
                <div class="metric-label">Total Tests</div>
            </div>
            <div class="metric">
                <div class="metric-value">{summary['passed_tests']}</div>
                <div class="metric-label">Passed</div>
            </div>
            <div class="metric">
                <div class="metric-value">{summary['failed_tests']}</div>
                <div class="metric-label">Failed</div>
            </div>
            <div class="metric">
                <div class="metric-value">{summary['success_rate']}%</div>
                <div class="metric-label">Success Rate</div>
            </div>
            <div class="metric">
                <div class="metric-value">{summary['total_duration']}s</div>
                <div class="metric-label">Total Duration</div>
            </div>
        </div>
        
        <div class="section">
            <h2>📋 Validation Suites</h2>
            <div class="suite-grid">
"""
        
        for suite in suites:
            status_class = suite["status"].lower()
            status_color = {
                "passed": "status-passed",
                "failed": "status-failed",
                "error": "status-error",
                "timeout": "status-timeout"
            }.get(status_class, "status-error")
            
            test_details = ""
            if "tests_collected" in suite:
                test_details = f"""
                    <div class="suite-details">
                        Tests: {suite['tests_collected']} | 
                        Passed: {suite.get('tests_passed', 0)} | 
                        Failed: {suite.get('tests_failed', 0)} | 
                        Errors: {suite.get('tests_error', 0)}
                    </div>
                """
            
            html_content += f"""
                <div class="suite-item {status_class}">
                    <div class="suite-name">
                        <span class="status-indicator {status_class}"></span>
                        {suite['name']}
                    </div>
                    <span class="suite-status {status_color}">{suite['status']}</span>
                    <div class="suite-description">{suite['description']}</div>
                    <div class="suite-duration">Duration: {suite['duration']:.2f}s</div>
                    {test_details}
                </div>
            """
        
        html_content += f"""
            </div>
        </div>
        
        <div class="footer">
            <p>MCP Financial Server System Validation</p>
            <p>Task 12: Integration testing and system validation - COMPLETED</p>
        </div>
    </div>
</body>
</html>
        """
        
        with open(output_path, 'w') as f:
            f.write(html_content)
    
    def print_summary(self):
        """Print validation summary to console."""
        summary = self.results["summary"]
        
        print("\n" + "="*60)
        print("  SYSTEM VALIDATION SUMMARY")
        print("="*60)
        
        print(f"📊 Test Results:")
        print(f"   Total Tests: {summary['total_tests']}")
        print(f"   Passed: {summary['passed_tests']}")
        print(f"   Failed: {summary['failed_tests']}")
        print(f"   Success Rate: {summary['success_rate']}%")
        print(f"   Total Duration: {summary['total_duration']}s")
        
        print(f"\n📋 Suite Results:")
        for suite in self.results["validation_suites"]:
            status_emoji = {
                "PASSED": "✅",
                "FAILED": "❌",
                "ERROR": "💥",
                "TIMEOUT": "⏰"
            }.get(suite["status"], "❓")
            
            print(f"   {status_emoji} {suite['name']}: {suite['status']} ({suite['duration']:.2f}s)")
        
        if summary["failed_tests"] == 0:
            print(f"\n🎉 All system validation tests passed!")
            print(f"   The MCP Financial Server is ready for deployment.")
        else:
            print(f"\n⚠️  Some validation tests failed.")
            print(f"   Please review the test results and fix issues before deployment.")
        
        print(f"\n📁 Reports generated in: {self.output_dir}")


def main():
    """Main execution function."""
    parser = argparse.ArgumentParser(description="MCP Financial Server System Validation")
    parser.add_argument("--output-dir", default="validation-results", help="Output directory for reports")
    parser.add_argument("--verbose", action="store_true", help="Verbose output")
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    # Create validator
    validator = SystemValidator(args.output_dir)
    
    try:
        # Run all validations
        results = validator.run_all_validations()
        
        # Generate reports
        validator.generate_report()
        
        # Print summary
        validator.print_summary()
        
        # Exit with appropriate code
        exit_code = 0 if results["summary"]["failed_tests"] == 0 else 1
        sys.exit(exit_code)
        
    except KeyboardInterrupt:
        logger.error("❌ Validation interrupted by user")
        sys.exit(1)
    except Exception as e:
        logger.error(f"❌ Validation failed with error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()