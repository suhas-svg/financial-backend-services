# Kubernetes Deployment Script for Account Service
param(
    [string]$Action = "deploy",
    [string]$Environment = "test"
)

Write-Host "=== Account Service Kubernetes Deployment ===" -ForegroundColor Green

switch ($Action.ToLower()) {
    "deploy" {
        Write-Host "Deploying to Kubernetes..." -ForegroundColor Yellow
        
        # Apply in order
        kubectl apply -f namespace.yaml
        kubectl apply -f configmap.yaml
        kubectl apply -f secrets.yaml
        kubectl apply -f rbac.yaml
        kubectl apply -f postgres-deployment.yaml
        
        # Wait for postgres to be ready
        Write-Host "Waiting for PostgreSQL to be ready..." -ForegroundColor Yellow
        kubectl wait --for=condition=ready pod -l app=postgres -n finance-services --timeout=300s
        
        # Deploy the application
        kubectl apply -f account-service-deployment.yaml
        kubectl apply -f network-policies.yaml
        
        # Wait for application to be ready
        Write-Host "Waiting for Account Service to be ready..." -ForegroundColor Yellow
        kubectl wait --for=condition=ready pod -l app=account-service -n finance-services --timeout=300s
        
        Write-Host "Deployment completed!" -ForegroundColor Green
    }
    
    "status" {
        Write-Host "Checking deployment status..." -ForegroundColor Yellow
        kubectl get all -n finance-services
        kubectl get networkpolicies -n finance-services
    }
    
    "logs" {
        Write-Host "Fetching application logs..." -ForegroundColor Yellow
        kubectl logs -l app=account-service -n finance-services --tail=50
    }
    
    "test" {
        Write-Host "Running connectivity tests..." -ForegroundColor Yellow
        # Port forward for testing
        Start-Job -ScriptBlock { kubectl port-forward svc/account-service 8080:8080 -n finance-services }
        Start-Job -ScriptBlock { kubectl port-forward svc/account-service-actuator 9001:9001 -n finance-services }
        
        Start-Sleep 5
        Write-Host "Services should be available at:"
        Write-Host "  - API: http://localhost:8080" -ForegroundColor Cyan
        Write-Host "  - Health: http://localhost:9001/actuator/health" -ForegroundColor Cyan
    }
    
    "cleanup" {
        Write-Host "Cleaning up deployment..." -ForegroundColor Red
        kubectl delete -f account-service-deployment.yaml --ignore-not-found=true
        kubectl delete -f postgres-deployment.yaml --ignore-not-found=true
        kubectl delete -f network-policies.yaml --ignore-not-found=true
        kubectl delete -f rbac.yaml --ignore-not-found=true
        kubectl delete -f secrets.yaml --ignore-not-found=true
        kubectl delete -f configmap.yaml --ignore-not-found=true
        kubectl delete -f namespace.yaml --ignore-not-found=true
        
        # Stop port forwarding jobs
        Get-Job | Where-Object { $_.Command -like "*kubectl port-forward*" } | Stop-Job
        Get-Job | Where-Object { $_.Command -like "*kubectl port-forward*" } | Remove-Job
        
        Write-Host "Cleanup completed!" -ForegroundColor Green
    }
    
    default {
        Write-Host "Usage: .\deploy.ps1 -Action [deploy|status|logs|test|cleanup]" -ForegroundColor Red
    }
}