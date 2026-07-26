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

Write-Host "Release authority policy passed: one workflow, one stable required check, legacy E2E quarantined."
