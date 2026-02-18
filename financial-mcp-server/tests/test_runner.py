"""
Comprehensive test runner for MCP Financial Server.
"""

import pytest
import sys
import os
import asyncio
import time
from pathlib import Path
from typing import Dict, List, Any
import json

# Add src directory to Python path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'src'))


class MCPTestRunner:
    """Comprehensive test runner with reporting and metrics."""
    
    def __init__(self):
        self.test_results = {}
        self.start_time = None
        self.end_time = None
    
    def run_unit_tests(self) -> Dict[str, Any]:
        """Run all unit tests."""
        print("🧪 Running Unit Tests...")
        
        unit_args = [
            "-v",
            "--tb=short",
            "--cov=mcp_financial",
            "--cov-report=term-missing",
            "--cov-report=html:tests/coverage/unit",
            "-m", "unit",
            "tests/unit/"
        ]
        
        result = pytest.main(unit_args)
        
        return {
            "status": "passed" if result == 0 else "failed",
            "exit_code": result,
            "test_type": "unit"
        }
    
    def run_integration_tests(self) -> Dict[str, Any]:
        """Run all integration tests."""
        print("🔗 Running Integration Tests...")
        
        integration_args = [
            "-v",
            "--tb=short",
            "-m", "integration",
            "tests/integration/"
        ]
        
        result = pytest.main(integration_args)
        
        return {
            "status": "passed" if result == 0 else "failed",
            "exit_code": result,
            "test_type": "integration"
        }
    
    def run_e2e_tests(self) -> Dict[str, Any]:
        """Run all end-to-end tests."""
        print("🎯 Running End-to-End Tests...")
        
        e2e_args = [
            "-v",
            "--tb=short",
            "-m", "e2e",
            "tests/e2e/"
        ]
        
        result = pytest.main(e2e_args)
        
        return {
            "status": "passed" if result == 0 else "failed",
            "exit_code": result,
            "test_type": "e2e"
        }
    
    def run_performance_tests(self) -> Dict[str, Any]:
        """Run performance and load tests."""
        print("⚡ Running Performance Tests...")
        
        perf_args = [
            "-v",
            "--tb=short",
            "-s",  # Don't capture output for performance tests
            "tests/performance/"
        ]
        
        result = pytest.main(perf_args)
        
        return {
            "status": "passed" if result == 0 else "failed",
            "exit_code": result,
            "test_type": "performance"
        }
    
    def run_all_tests(self) -> Dict[str, Any]:
        """Run all test suites."""
        print("🚀 Running Complete Test Suite...")
        self.start_time = time.time()
        
        # Run test suites in order
        test_suites = [
            ("Unit Tests", self.run_unit_tests),
            ("Integration Tests", self.run_integration_tests),
            ("End-to-End Tests", self.run_e2e_tests),
            ("Performance Tests", self.run_performance_tests)
        ]
        
        results = {}
        overall_status = "passed"
        
        for suite_name, test_func in test_suites:
            print(f"\n{'='*60}")
            print(f"Running {suite_name}")
            print(f"{'='*60}")
            
            try:
                result = test_func()
                results[suite_name] = result
                
                if result["status"] == "failed":
                    overall_status = "failed"
                    print(f"❌ {suite_name} FAILED")
                else:
                    print(f"✅ {suite_name} PASSED")
                    
            except Exception as e:
                print(f"❌ {suite_name} ERROR: {str(e)}")
                results[suite_name] = {
                    "status": "error",
                    "error": str(e),
                    "test_type": suite_name.lower().replace(" ", "_")
                }
                overall_status = "failed"
        
        self.end_time = time.time()
        
        # Generate summary report
        summary = self.generate_summary_report(results, overall_status)
        self.save_test_report(summary)
        
        return summary
    
    def generate_summary_report(self, results: Dict[str, Any], overall_status: str) -> Dict[str, Any]:
        """Generate comprehensive test summary report."""
        total_duration = self.end_time - self.start_time if self.start_time and self.end_time else 0
        
        summary = {
            "overall_status": overall_status,
            "total_duration_seconds": round(total_duration, 2),
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "test_suites": results,
            "statistics": {
                "total_suites": len(results),
                "passed_suites": sum(1 for r in results.values() if r.get("status") == "passed"),
                "failed_suites": sum(1 for r in results.values() if r.get("status") == "failed"),
                "error_suites": sum(1 for r in results.values() if r.get("status") == "error")
            }
        }
        
        return summary
    
    def save_test_report(self, summary: Dict[str, Any]) -> None:
        """Save test report to file."""
        reports_dir = Path("tests/reports")
        reports_dir.mkdir(exist_ok=True)
        
        timestamp = time.strftime("%Y%m%d_%H%M%S")
        report_file = reports_dir / f"test_report_{timestamp}.json"
        
        with open(report_file, 'w') as f:
            json.dump(summary, f, indent=2)
        
        print(f"\n📊 Test report saved to: {report_file}")
    
    def print_summary(self, summary: Dict[str, Any]) -> None:
        """Print formatted test summary."""
        print(f"\n{'='*80}")
        print("🎯 TEST EXECUTION SUMMARY")
        print(f"{'='*80}")
        
        print(f"Overall Status: {'✅ PASSED' if summary['overall_status'] == 'passed' else '❌ FAILED'}")
        print(f"Total Duration: {summary['total_duration_seconds']} seconds")
        print(f"Timestamp: {summary['timestamp']}")
        
        print(f"\n📈 Statistics:")
        stats = summary['statistics']
        print(f"  Total Suites: {stats['total_suites']}")
        print(f"  Passed: {stats['passed_suites']}")
        print(f"  Failed: {stats['failed_suites']}")
        print(f"  Errors: {stats['error_suites']}")
        
        print(f"\n📋 Test Suite Results:")
        for suite_name, result in summary['test_suites'].items():
            status_icon = "✅" if result['status'] == 'passed' else "❌"
            print(f"  {status_icon} {suite_name}: {result['status'].upper()}")
        
        print(f"\n{'='*80}")


class CoverageAnalyzer:
    """Analyze test coverage and generate reports."""
    
    @staticmethod
    def generate_coverage_report():
        """Generate comprehensive coverage report."""
        print("📊 Generating Coverage Report...")
        
        coverage_args = [
            "--cov=mcp_financial",
            "--cov-report=html:tests/coverage/html",
            "--cov-report=xml:tests/coverage/coverage.xml",
            "--cov-report=term-missing",
            "--cov-fail-under=80",  # Require 80% coverage
            "tests/unit/",
            "tests/integration/"
        ]
        
        result = pytest.main(coverage_args)
        
        if result == 0:
            print("✅ Coverage requirements met (≥80%)")
        else:
            print("❌ Coverage requirements not met (<80%)")
        
        return result == 0


class TestValidator:
    """Validate test quality and completeness."""
    
    @staticmethod
    def validate_test_structure():
        """Validate test directory structure and naming conventions."""
        print("🔍 Validating Test Structure...")
        
        required_dirs = [
            "tests/unit",
            "tests/integration", 
            "tests/e2e",
            "tests/performance"
        ]
        
        missing_dirs = []
        for dir_path in required_dirs:
            if not Path(dir_path).exists():
                missing_dirs.append(dir_path)
        
        if missing_dirs:
            print(f"❌ Missing test directories: {missing_dirs}")
            return False
        
        print("✅ Test structure validation passed")
        return True
    
    @staticmethod
    def validate_test_naming():
        """Validate test file naming conventions."""
        print("📝 Validating Test Naming Conventions...")
        
        test_files = []
        for test_dir in ["tests/unit", "tests/integration", "tests/e2e", "tests/performance"]:
            if Path(test_dir).exists():
                test_files.extend(Path(test_dir).glob("**/*.py"))
        
        invalid_files = []
        for file_path in test_files:
            if not file_path.name.startswith("test_") and file_path.name != "__init__.py":
                invalid_files.append(str(file_path))
        
        if invalid_files:
            print(f"❌ Invalid test file names: {invalid_files}")
            return False
        
        print("✅ Test naming validation passed")
        return True


def main():
    """Main test execution function."""
    print("🧪 MCP Financial Server - Comprehensive Test Suite")
    print("=" * 60)
    
    # Validate test setup
    validator = TestValidator()
    if not validator.validate_test_structure():
        sys.exit(1)
    
    if not validator.validate_test_naming():
        sys.exit(1)
    
    # Run tests
    runner = MCPTestRunner()
    
    # Check command line arguments
    if len(sys.argv) > 1:
        test_type = sys.argv[1].lower()
        
        if test_type == "unit":
            result = runner.run_unit_tests()
        elif test_type == "integration":
            result = runner.run_integration_tests()
        elif test_type == "e2e":
            result = runner.run_e2e_tests()
        elif test_type == "performance":
            result = runner.run_performance_tests()
        elif test_type == "coverage":
            analyzer = CoverageAnalyzer()
            success = analyzer.generate_coverage_report()
            sys.exit(0 if success else 1)
        else:
            print(f"Unknown test type: {test_type}")
            print("Available options: unit, integration, e2e, performance, coverage, all")
            sys.exit(1)
        
        sys.exit(0 if result["status"] == "passed" else 1)
    
    else:
        # Run all tests
        summary = runner.run_all_tests()
        runner.print_summary(summary)
        
        # Generate coverage report
        analyzer = CoverageAnalyzer()
        analyzer.generate_coverage_report()
        
        sys.exit(0 if summary["overall_status"] == "passed" else 1)


if __name__ == "__main__":
    main()