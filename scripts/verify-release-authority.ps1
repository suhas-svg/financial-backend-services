[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$workflowRoot = Join-Path $repoRoot ".github\workflows"
$active = @(Get-ChildItem -LiteralPath $workflowRoot -File |
    Where-Object { $_.Extension -in ".yml", ".yaml" })

if ($active.Count -ne 1 -or $active[0].Name -ne "release-authority.yml") {
    $names = ($active.Name | Sort-Object) -join ", "
    throw "Exactly one active workflow is allowed: release-authority.yml. Found: $names"
}

$workflow = Get-Content -LiteralPath $active[0].FullName -Raw
$requiredContracts = @(
    "Frontend Lint, Tests, Build, Accessibility",
    "service-verification:",
    "fresh-migrations:",
    "duplicate-concurrency-recovery:",
    "Helm and Terraform Policy",
    "Canonical Synthetic API and Browser E2E",
    "Container Scan and SBOM",
    "Backend Dependency Scan",
    "Full-History Secret Scan",
    "release-authority:",
    "name: Required Acceptance"
)
foreach ($contract in $requiredContracts) {
    if (-not $workflow.Contains($contract)) {
        throw "Canonical release authority is missing required contract: $contract"
    }
}

$forbiddenLegacyReferences = @(
    "e2e-tests/",
    "docker-compose-e2e.yml",
    "docker-compose-full-e2e.yml",
    "financial-mcp-server"
)
foreach ($reference in $forbiddenLegacyReferences) {
    if ($workflow.Contains($reference)) {
        throw "Canonical release authority references quarantined or out-of-scope runtime: $reference"
    }
}

$legacyPackage = Get-Content -LiteralPath (Join-Path $repoRoot "e2e-tests\package.json") -Raw |
    ConvertFrom-Json
if ($legacyPackage.scripts.test -ne "node scripts/quarantined.js") {
    throw "The obsolete e2e-tests harness must remain fail-closed."
}

$trackedRuntimeEnv = & git -C $repoRoot ls-files -- "e2e-tests/.env.e2e"
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect tracked legacy runtime environment files."
}
$deletedRuntimeEnv = & git -C $repoRoot ls-files --deleted -- "e2e-tests/.env.e2e"
if ($trackedRuntimeEnv -and -not $deletedRuntimeEnv) {
    throw "Tracked legacy runtime environment file e2e-tests/.env.e2e must remain removed."
}

$actionReferences = [regex]::Matches($workflow, 'uses:\s*([^\s@]+)@([^\s#]+)')
foreach ($reference in $actionReferences) {
    $action = $reference.Groups[1].Value
    $revision = $reference.Groups[2].Value
    if ($revision -notmatch '^[0-9a-f]{40}$') {
        throw "GitHub Action must use an immutable 40-character commit SHA: $action@$revision"
    }
}

$dockerfiles = @(
    "Dockerfile.test-runner",
    "account-service/Dockerfile",
    "transaction-service/Dockerfile",
    "frontend/Dockerfile.sandbox",
    "infrastructure/synthetic-sandbox/gateway/Dockerfile"
)
foreach ($dockerfile in $dockerfiles) {
    $content = Get-Content -LiteralPath (Join-Path $repoRoot $dockerfile) -Raw
    foreach ($base in [regex]::Matches($content, '(?m)^FROM\s+([^\s]+)')) {
        $image = $base.Groups[1].Value
        if ($image -notmatch '@sha256:[0-9a-f]{64}$') {
            throw "Docker base image must be digest-pinned: $dockerfile uses $image"
        }
    }
}

$compose = Get-Content -LiteralPath (Join-Path $repoRoot "docker-compose.synthetic-sandbox.yml") -Raw
foreach ($match in [regex]::Matches($compose, '(?m)^\s+image:\s+([^\s]+)')) {
    $image = $match.Groups[1].Value
    if (-not $image.StartsWith("financial-") -and $image -notmatch '@sha256:[0-9a-f]{64}$') {
        throw "Synthetic sandbox external image must be digest-pinned: $image"
    }
}

$accountDockerfile = Get-Content -LiteralPath (Join-Path $repoRoot "account-service/Dockerfile") -Raw
if ($accountDockerfile.Contains("ARG APP_VERSION=latest")) {
    throw "Account image metadata must not default to latest."
}
if (-not $accountDockerfile.Contains('org.opencontainers.image.source="https://github.com/suhas-svg/financial-backend-services"')) {
    throw "Account image source metadata does not identify the authoritative repository."
}
if (-not $workflow.Contains("check-surefire-retries.ps1") -or
    -not (Test-Path (Join-Path $repoRoot "scripts/test-surefire-retry-governance.ps1"))) {
    throw "Surefire retry-only success governance is not wired into acceptance."
}

Write-Host "Supply-chain policy passed: actions and release images are immutable; retry-only test success is governed."
Write-Host "Release authority policy passed: one workflow, one stable required check, legacy E2E quarantined."
