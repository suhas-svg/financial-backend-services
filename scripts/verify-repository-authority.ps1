#!/usr/bin/env pwsh

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$forbidden = @(
    "docker-compose.yml",
    "PRODUCTION-SETUP.md",
    "start-production.ps1",
    "account-service/k8s",
    "financial-mcp-server/Dockerfile",
    "financial-mcp-server/docker-compose.yml",
    "financial-mcp-server/docker-compose.dev.yml",
    "financial-mcp-server/docker-compose.staging.yml",
    "financial-mcp-server/docker-compose.prod.yml",
    "financial-mcp-server/pyproject.toml",
    "financial-mcp-server/requirements.txt",
    "financial-mcp-server/Makefile",
    "financial-mcp-server/setup-dev.ps1",
    "financial-mcp-server/setup-dev.sh"
)

$present = @($forbidden | Where-Object { Test-Path (Join-Path $root $_) })
if ($present.Count -gt 0) {
    throw "Unsupported deployment paths returned: $($present -join ', ')"
}

$required = @(
    "docker-compose.synthetic-sandbox.yml",
    "docs/deployment-authority.md",
    "financial-mcp-server/ARCHIVED.md",
    "infrastructure/helm/account-service/Chart.yaml",
    "infrastructure/helm/transaction-service/Chart.yaml"
)

$missing = @($required | Where-Object { -not (Test-Path (Join-Path $root $_)) })
if ($missing.Count -gt 0) {
    throw "Deployment authority is incomplete: $($missing -join ', ')"
}

Write-Host "Repository deployment authority is singular and fail-closed."
