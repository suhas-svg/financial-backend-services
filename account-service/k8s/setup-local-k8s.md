# Local Kubernetes Setup Guide

## Option 1: Docker Desktop Kubernetes (Recommended for Windows)

1. **Enable Kubernetes in Docker Desktop:**
   - Open Docker Desktop
   - Go to Settings → Kubernetes
   - Check "Enable Kubernetes"
   - Click "Apply & Restart"
   - Wait for Kubernetes to start (green indicator)

2. **Verify Installation:**
   ```powershell
   kubectl cluster-info
   kubectl get nodes
   ```

## Option 2: Kind (Kubernetes in Docker)

1. **Install Kind:**
   ```powershell
   # Using Chocolatey
   choco install kind
   
   # Or download from: https://kind.sigs.k8s.io/docs/user/quick-start/
   ```

2. **Create Cluster:**
   ```powershell
   kind create cluster --name finance-cluster
   kubectl cluster-info --context kind-finance-cluster
   ```

## Option 3: Minikube

1. **Install Minikube:**
   ```powershell
   choco install minikube
   ```

2. **Start Cluster:**
   ```powershell
   minikube start
   kubectl cluster-info
   ```

## Testing Without Kubernetes (Alternative)

If you can't set up Kubernetes right now, you can test the production-ready configuration using Docker Compose with the same environment variables and security settings.

## Next Steps After Setup

1. Run the deployment script:
   ```powershell
   cd account-service/k8s
   .\deploy.ps1 -Action deploy
   ```

2. Test the application:
   ```powershell
   .\deploy.ps1 -Action test
   .\test-k8s.ps1
   ```

3. Monitor the deployment:
   ```powershell
   .\deploy.ps1 -Action status
   .\deploy.ps1 -Action logs
   ```