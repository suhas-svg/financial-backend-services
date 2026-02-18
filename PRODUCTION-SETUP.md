# 🏦 Financial Backend Services - Production Setup

This guide covers the production deployment of the Financial Backend Services with Account Service on port 8080 and Transaction Service on port 8081.

## 🚀 Quick Start

### Prerequisites
- Docker and Docker Compose installed
- Ports 8080, 8081, 5432, and 5433 available

### 1. Start Production Services
```powershell
# Start all services
./start-production.ps1

# Or manually with Docker Compose
docker-compose up -d --build
```

### 2. Verify Services
- **Account Service**: http://localhost:8080/actuator/health
- **Transaction Service**: http://localhost:8081/actuator/health

### 3. Stop Services
```powershell
# Stop all services
./stop-production.ps1

# Or manually
docker-compose down
```

## 📊 Service Configuration

### Account Service
- **Port**: 8080
- **Database**: PostgreSQL on port 5432 (`myfirstdb`)
- **Health Check**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/prometheus

### Transaction Service
- **Port**: 8081
- **Database**: PostgreSQL on port 5433 (`transactiondb`)
- **Health Check**: http://localhost:8081/actuator/health
- **Metrics**: http://localhost:8081/actuator/prometheus

## 🔐 Security Configuration

### Environment Variables
Update the `.env` file with secure values:

```env
# CHANGE THESE IN PRODUCTION!
POSTGRES_PASSWORD=your-secure-postgres-password-here
JWT_SECRET=your-secure-jwt-secret-here
```

### JWT Secret
Generate a secure JWT secret:
```powershell
# Generate a secure 256-bit key
[System.Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

## 🗄️ Database Information

### Account Database (myfirstdb)
- **Host**: localhost
- **Port**: 5432
- **Database**: myfirstdb
- **User**: postgres

### Transaction Database (transactiondb)
- **Host**: localhost
- **Port**: 5433
- **Database**: transactiondb
- **User**: postgres

## 📝 Useful Commands

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f account-service
docker-compose logs -f transaction-service
```

### Service Management
```bash
# Restart a specific service
docker-compose restart account-service

# Rebuild and restart
docker-compose up -d --build account-service

# Check service status
docker-compose ps
```

### Database Access
```bash
# Connect to account database
docker exec -it account-postgres psql -U postgres -d myfirstdb

# Connect to transaction database
docker exec -it transaction-postgres psql -U postgres -d transactiondb
```

## 🔍 Monitoring

### Health Endpoints
- Account Service: http://localhost:8080/actuator/health
- Transaction Service: http://localhost:8081/actuator/health

### Metrics Endpoints
- Account Service: http://localhost:8080/actuator/metrics
- Transaction Service: http://localhost:8081/actuator/metrics

### Prometheus Metrics
- Account Service: http://localhost:8080/actuator/prometheus
- Transaction Service: http://localhost:8081/actuator/prometheus

## 🚨 Troubleshooting

### Port Conflicts
If ports are in use, check what's using them:
```powershell
netstat -an | findstr :8080
netstat -an | findstr :8081
```

### Database Connection Issues
1. Ensure PostgreSQL containers are healthy:
   ```bash
   docker-compose ps
   ```

2. Check database logs:
   ```bash
   docker-compose logs account-postgres
   docker-compose logs transaction-postgres
   ```

### Service Communication Issues
1. Verify both services are in the same network:
   ```bash
   docker network ls
   docker network inspect financial-network
   ```

2. Test service connectivity:
   ```bash
   docker exec -it transaction-service curl http://account-service:8080/actuator/health
   ```

## 🔄 Updates and Maintenance

### Update Services
```bash
# Pull latest images and rebuild
docker-compose pull
docker-compose up -d --build

# Or rebuild specific service
docker-compose build account-service
docker-compose up -d account-service
```

### Backup Databases
```bash
# Backup account database
docker exec account-postgres pg_dump -U postgres myfirstdb > account_backup.sql

# Backup transaction database
docker exec transaction-postgres pg_dump -U postgres transactiondb > transaction_backup.sql
```

### Clean Up
```bash
# Remove stopped containers and unused images
docker system prune

# Remove all data (WARNING: This will delete all data!)
docker-compose down -v
docker system prune -a
```

## 📋 Production Checklist

- [ ] Updated `.env` file with secure passwords
- [ ] Changed default JWT secret
- [ ] Verified all services start successfully
- [ ] Tested health endpoints
- [ ] Confirmed database connectivity
- [ ] Set up monitoring/alerting (if needed)
- [ ] Configured backup strategy
- [ ] Documented access credentials securely

## 🎯 API Endpoints

### Account Service (Port 8080)
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `GET /api/accounts` - List accounts
- `POST /api/accounts` - Create account
- `GET /api/accounts/{id}` - Get account
- `PUT /api/accounts/{id}` - Update account
- `DELETE /api/accounts/{id}` - Delete account

### Transaction Service (Port 8081)
- `POST /api/transactions/deposit` - Make deposit
- `POST /api/transactions/withdraw` - Make withdrawal
- `POST /api/transactions/transfer` - Transfer funds
- `GET /api/transactions` - Transaction history
- `GET /api/transactions/search` - Search transactions

---

**🏦 Your Financial Backend Services are ready for production! 💰**