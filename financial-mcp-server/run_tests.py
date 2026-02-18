#!/usr/bin/env python3
"""
Comprehensive test execution script for MCP Financial Server.
"""

import os
import sys
import subprocess
import argparse
import time
from pathlib import Path
from typing import List, Dict, Any


def setup_environment():
    """Setup test environment and dependencies."""
    print("🔧 Setting up test environment...")
    
    # Ensure test directories exist
    test_dirs = [
        "tests/unit",
        "tests/integration", 
        "tests/e2e",
        "tests/performance",
        "tests/coverage",
        "tests/reports"
    ]
    
    for test_dir in test_dirs:
        Path(test_dir).mkdir(parents=True, exist_ok=True)
    
    # Create __init__.py files if they don't exist
    for test_dir in test_dirs[:4]:  # Exclude coverage and reports
        init_file = Path(test_dir) / "__init__.py"
        if not init_file.exists():
            init_file.write_text("# Test package\n")
    
    print("✅ Test environment setup complete")


def run_linting():
    """Run code linting and formatting checks."""
    print("🔍 Running code quality checks...")
    
    commands = [
        ["black", "--check", "src/", "tests/"],
        ["isort", "--check-only", "src/", "tests/"],
        ["flake8", "src/", "tests/"],
        ["mypy", "src/mcp_financial/"]
    ]
    
    for cmd in commands:
        try:
            result = subprocess.run(cmd, capture_output=True, text=True)
            if result.returncode != 0:
                print(f"❌ {cmd[0]} failed:")
                print(result.stdout)
                print(result.stderr)
                return False
            else:
                print(f"✅ {cmd[0]} passed")
        except FileNotFoundError:
            print(f"⚠️  {cmd[0]} not found, skipping...")
    
    return True


def run_security_checks():
    """Run security vulnerability checks."""
    print("🔒 Running security checks...")
    
    try:
        # Check for known vulnerabilities in dependencies
        result = subprocess.run(
            ["safety", "check", "--json"],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            print("❌ Security vulnerabilities found:")
            print(result.stdout)
            return False
        else:
            print("✅ No security vulnerabilities found")
    
    except FileNotFoundError:
        print("⚠️  Safety not found, skipping security checks...")
    
    try:
        # Check for secrets in code
        result = subprocess.run(
            ["bandit", "-r", "src/", "-f", "json"],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            print("❌ Security issues found:")
            print(result.stdout)
            return False
        else:
            print("✅ No security issues found")
    
    except FileNotFoundError:
        print("⚠️  Bandit not found, skipping security scan...")
    
    return True


def run_unit_tests(verbose: bool = False) -> bool:
    """Run unit tests."""
    print("🧪 Running unit tests...")
    
    cmd = [
        "python", "-m", "pytest",
        "tests/unit/",
        "-v" if verbose else "-q",
        "--cov=mcp_financial",
        "--cov-report=term-missing",
        "--cov-report=html:tests/coverage/unit",
        "--cov-fail-under=80",
        "-m", "unit"
    ]
    
    result = subprocess.run(cmd)
    return result.returncode == 0


def run_integration_tests(verbose: bool = False) -> bool:
    """Run integration tests."""
    print("🔗 Running integration tests...")
    
    cmd = [
        "python", "-m", "pytest",
        "tests/integration/",
        "-v" if verbose else "-q",
        "-m", "integration"
    ]
    
    result = subprocess.run(cmd)
    return result.returncode == 0


def run_e2e_tests(verbose: bool = False) -> bool:
    """Run end-to-end tests."""
    print("🎯 Running end-to-end tests...")
    
    cmd = [
        "python", "-m", "pytest",
        "tests/e2e/",
        "-v" if verbose else "-q",
        "-m", "e2e"
    ]
    
    result = subprocess.run(cmd)
    return result.returncode == 0


def run_performance_tests(verbose: bool = False) -> bool:
    """Run performance tests."""
    print("⚡ Running performance tests...")
    
    cmd = [
        "python", "-m", "pytest",
        "tests/performance/",
        "-v" if verbose else "-q",
        "-s"  # Don't capture output for performance tests
    ]
    
    result = subprocess.run(cmd)
    return result.returncode == 0


def generate_coverage_report():
    """Generate comprehensive coverage report."""
    print("📊 Generating coverage report...")
    
    cmd = [
        "python", "-m", "pytest",
        "tests/unit/",
        "tests/integration/",
        "--cov=mcp_financial",
        "--cov-report=html:tests/coverage/html",
        "--cov-report=xml:tests/coverage/coverage.xml",
        "--cov-report=term"
    ]
    
    result = subprocess.run(cmd)
    
    if result.returncode == 0:
        print("✅ Coverage report generated successfully")
        print("📁 HTML report: tests/coverage/html/index.html")
        print("📁 XML report: tests/coverage/coverage.xml")
    else:
        print("❌ Failed to generate coverage report")
    
    return result.returncode == 0


def run_all_tests(verbose: bool = False) -> Dict[str, bool]:
    """Run all test suites."""
    print("🚀 Running complete test suite...")
    
    results = {}
    
    # Run test suites in order
    test_suites = [
        ("Unit Tests", lambda: run_unit_tests(verbose)),
        ("Integration Tests", lambda: run_integration_tests(verbose)),
        ("End-to-End Tests", lambda: run_e2e_tests(verbose)),
        ("Performance Tests", lambda: run_performance_tests(verbose))
    ]
    
    for suite_name, test_func in test_suites:
        print(f"\n{'='*60}")
        print(f"Running {suite_name}")
        print(f"{'='*60}")
        
        start_time = time.time()
        success = test_func()
        duration = time.time() - start_time
        
        results[suite_name] = success
        
        if success:
            print(f"✅ {suite_name} PASSED ({duration:.2f}s)")
        else:
            print(f"❌ {suite_name} FAILED ({duration:.2f}s)")
    
    return results


def print_summary(results: Dict[str, bool], total_duration: float):
    """Print test execution summary."""
    print(f"\n{'='*80}")
    print("🎯 TEST EXECUTION SUMMARY")
    print(f"{'='*80}")
    
    total_suites = len(results)
    passed_suites = sum(1 for success in results.values() if success)
    failed_suites = total_suites - passed_suites
    
    print(f"Total Duration: {total_duration:.2f} seconds")
    print(f"Total Suites: {total_suites}")
    print(f"Passed: {passed_suites}")
    print(f"Failed: {failed_suites}")
    
    print(f"\n📋 Detailed Results:")
    for suite_name, success in results.items():
        status_icon = "✅" if success else "❌"
        print(f"  {status_icon} {suite_name}")
    
    overall_success = all(results.values())
    print(f"\n🏆 Overall Result: {'✅ PASSED' if overall_success else '❌ FAILED'}")
    
    return overall_success


def main():
    """Main test execution function."""
    parser = argparse.ArgumentParser(description="MCP Financial Server Test Runner")
    parser.add_argument(
        "test_type",
        nargs="?",
        choices=["unit", "integration", "e2e", "performance", "all", "lint", "security", "coverage"],
        default="all",
        help="Type of tests to run"
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Verbose output"
    )
    parser.add_argument(
        "--no-setup",
        action="store_true",
        help="Skip environment setup"
    )
    parser.add_argument(
        "--no-lint",
        action="store_true",
        help="Skip linting checks"
    )
    parser.add_argument(
        "--no-security",
        action="store_true",
        help="Skip security checks"
    )
    
    args = parser.parse_args()
    
    print("🧪 MCP Financial Server - Test Runner")
    print("=" * 50)
    
    # Setup environment
    if not args.no_setup:
        setup_environment()
    
    # Run linting checks
    if not args.no_lint and args.test_type in ["all", "lint"]:
        if not run_linting():
            print("❌ Linting checks failed")
            if args.test_type == "lint":
                sys.exit(1)
    
    # Run security checks
    if not args.no_security and args.test_type in ["all", "security"]:
        if not run_security_checks():
            print("❌ Security checks failed")
            if args.test_type == "security":
                sys.exit(1)
    
    # Run tests based on type
    start_time = time.time()
    
    if args.test_type == "unit":
        success = run_unit_tests(args.verbose)
    elif args.test_type == "integration":
        success = run_integration_tests(args.verbose)
    elif args.test_type == "e2e":
        success = run_e2e_tests(args.verbose)
    elif args.test_type == "performance":
        success = run_performance_tests(args.verbose)
    elif args.test_type == "coverage":
        success = generate_coverage_report()
    elif args.test_type == "all":
        results = run_all_tests(args.verbose)
        total_duration = time.time() - start_time
        success = print_summary(results, total_duration)
        
        # Generate coverage report
        print(f"\n{'='*60}")
        generate_coverage_report()
    else:
        print(f"Unknown test type: {args.test_type}")
        sys.exit(1)
    
    # Exit with appropriate code
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()