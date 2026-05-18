# Complete Setup & Execution Guide
# Enterprise IT Help Desk & Asset Management Platform

## Table of Contents
1. [System Requirements](#system-requirements)
2. [Quick Start (Docker)](#quick-start-docker)
3. [Manual Setup](#manual-setup)
4. [Application Access](#application-access)
5. [Testing the Application](#testing-the-application)
6. [Troubleshooting](#troubleshooting)
7. [Project Structure](#project-structure)

---

## System Requirements

### Minimum Requirements
- **CPU**: Dual-core processor
- **RAM**: 8 GB (16 GB recommended)
- **Disk Space**: 20 GB free space
- **Network**: Internet connection for downloading dependencies

### Software Requirements
- **Docker & Docker Compose** (for Docker setup)
  - Docker 20.10+
  - Docker Compose 1.29+
  
- **Manual Setup Requirements**
  - Java JDK 17+
  - Node.js 16+
  - npm 8+
  - MySQL 8.0+
  - Maven 3.8+
  - Git

---

## Quick Start (Docker) ⚡

### Step 1: Clone/Create Project
```bash
# Create project directory
mkdir enterprise-helpdesk
cd enterprise-helpdesk

# Copy all files from the provided project structure
# Ensure the following directory structure:
# ├── backend/
# ├── frontend/
# ├── docker/
# ├── database/
# ├── docker-compose.yml
# └── README.md
```

### Step 2: Build and Start Services
```bash
# Build and start all services
docker-compose up --build

# On first run, this will:
# 1. Build all microservices
# 2. Create MySQL databases
# 3. Initialize schema
# 4. Start all containers
# 5. Create sample data
```

### Step 3: Wait for Services to Be Ready
```bash
# Monitor the logs
docker-compose logs -f

# Wait for messages like:
# api-gateway       | Started ApiGatewayApplication
# auth-service      | Started AuthServiceApplication
# frontend          | ✓ 2 modules transformed
```

### Step 4: Access the Application
- **Frontend**: http://localhost:5173
- **API Gateway**: http://localhost:8000
- **Swagger API Docs**: http://localhost:8000/swagger-ui.html
- **API Health Check**: http://localhost:8000/api/auth/health

### Step 5: Stop Services
```bash
docker-compose down

# To remove all data and restart fresh:
docker-compose down -v
```

---

## Manual Setup

### Part 1: Database Setup

#### 1.1 Install MySQL
```bash
# macOS with Homebrew
brew install mysql

# Ubuntu/Debian
sudo apt-get install mysql-server

# Windows
# Download and install from https://dev.mysql.com/downloads/mysql/
```

#### 1.2 Start MySQL Service
```bash
# macOS
brew services start mysql

# Ubuntu/Linux
sudo systemctl start mysql

# Windows (using Command Prompt as Administrator)
net start MySQL80
```

#### 1.3 Initialize Database
```bash
# Connect to MySQL
mysql -u root -p

# Run the schema SQL script
# In MySQL prompt:
source path/to/database/schema.sql

# Verify creation:
SHOW DATABASES;
```

### Part 2: Backend Services Setup

#### 2.1 Set JAVA_HOME (if needed)
```bash
# macOS
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Linux/Ubuntu
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Windows (Command Prompt)
set JAVA_HOME=C:\Program Files\Java\jdk-17
```

#### 2.2 Build All Services
```bash
cd backend
mvn clean install

# This may take 5-10 minutes on first run
```

#### 2.3 Start Microservices (in separate terminals)

**Terminal 1: Start Auth Service**
```bash
cd backend/auth-service
mvn spring-boot:run
# Wait for: "Started AuthServiceApplication"
```

**Terminal 2: Start User Service**
```bash
cd backend/user-service
mvn spring-boot:run
```

**Terminal 3: Start Ticket Service**
```bash
cd backend/ticket-service
mvn spring-boot:run
```

**Terminal 4: Start Asset Service**
```bash
cd backend/asset-service
mvn spring-boot:run
```

**Terminal 5: Start Notification Service**
```bash
cd backend/notification-service
mvn spring-boot:run
```

**Terminal 6: Start API Gateway**
```bash
cd backend/api-gateway
mvn spring-boot:run
# Wait for: "Started ApiGatewayApplication"
```

### Part 3: Frontend Setup

#### 3.1 Install Dependencies
```bash
cd frontend
npm install
```

#### 3.2 Create .env File (Optional)
```bash
# Create frontend/.env
VITE_API_URL=http://localhost:8000/api
```

#### 3.3 Start Frontend
```bash
npm run dev

# Frontend will be available at: http://localhost:5173
```

---

## Application Access

### URL Overview

| Service | URL | Purpose |
|---------|-----|---------|
| Frontend | http://localhost:5173 | Web application |
| API Gateway | http://localhost:8000 | API entry point |
| Auth Service | http://localhost:8001 | User authentication |
| User Service | http://localhost:8002 | User management |
| Ticket Service | http://localhost:8003 | Ticket management |
| Asset Service | http://localhost:8004 | Asset management |
| Notification Service | http://localhost:8005 | Notifications |
| Swagger Docs | http://localhost:8000/swagger-ui.html | API documentation |
| MySQL | localhost:3306 | Database |

### Demo Credentials

| Role | Email | Password |
|------|-------|----------|
| **Admin** | admin@company.com | Admin@123456 |
| **Technician** | technician@company.com | Tech@123456 |
| **Employee** | employee@company.com | Emp@123456 |

*Note: These are salted and hashed in the database. The plain passwords above are provided only for login testing.*

---

## Testing the Application

### 1. Login Test
```bash
# Using curl to test login endpoint
curl -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@company.com",
    "password": "Admin@123456"
  }'

# Expected response:
# {
#   "token": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
#   "refreshToken": "...",
#   "userId": 1,
#   "email": "admin@company.com",
#   "firstName": "Admin",
#   "lastName": "User",
#   "role": "ADMIN"
# }
```

### 2. Create Ticket Test
```bash
# First, get the token from login
TOKEN="your_token_here"

curl -X POST http://localhost:8000/api/tickets \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "Test Ticket",
    "description": "This is a test ticket",
    "priority": "MEDIUM",
    "category": "Hardware"
  }'
```

### 3. Frontend UI Test
1. Open http://localhost:5173 in your browser
2. Login with demo credentials
3. Navigate through Dashboard, Tickets, Users, Assets
4. Create a new ticket
5. View ticket details

### 4. Using Postman
```bash
# Import the provided Postman collection
# File: postman/Helpdesk-API-Collection.postman_collection.json

# Or manually create requests in Postman:
# 1. Set Base URL: http://localhost:8000/api
# 2. Create Auth -> Login request
# 3. Use token for subsequent requests
```

---

## Troubleshooting

### Issue 1: MySQL Connection Error

**Error**: `Can't connect to MySQL server on 'localhost:3306'`

**Solution**:
```bash
# Check if MySQL is running
# macOS
brew services list | grep mysql

# Linux
sudo systemctl status mysql

# Verify credentials in application.yml
# Default: root password (or set in schema.sql)
```

### Issue 2: Port Already in Use

**Error**: `Address already in use`

**Solution**:
```bash
# Kill process on specific port (macOS/Linux)
lsof -i :8000
kill -9 <PID>

# Docker solution
docker ps  # List running containers
docker stop <container_id>
```

### Issue 3: Maven Build Failure

**Error**: `BUILD FAILURE`

**Solution**:
```bash
# Clear Maven cache
mvn clean
rm -rf ~/.m2/repository

# Rebuild
mvn clean install -U
```

### Issue 4: Frontend Blank Page

**Error**: Blank page after opening http://localhost:5173

**Solution**:
```bash
# Check browser console for errors (F12)
# Clear cache and reload
# Verify API is reachable:
curl http://localhost:8000/api/auth/health

# Check vite server logs
```

### Issue 5: CORS Errors

**Error**: `Access to XMLHttpRequest blocked by CORS policy`

**Solution**:
- CORS is configured in SecurityConfig.java
- Ensure API Gateway is running
- Check if request origin is allowed in CORS config

### Issue 6: Authentication Token Expired

**Solution**:
```bash
# Token expires after 15 minutes
# Automatic refresh via refresh token
# Or logout and login again
```

---

## Project Structure Reference

```
enterprise-helpdesk/
│
├── backend/
│   ├── pom.xml (parent)
│   ├── api-gateway/
│   │   ├── pom.xml
│   │   └── src/main/java/.../ApiGatewayApplication.java
│   ├── auth-service/
│   │   ├── pom.xml
│   │   └── src/main/java/.../AuthServiceApplication.java
│   ├── user-service/
│   ├── ticket-service/
│   ├── asset-service/
│   └── notification-service/
│
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.ts
│   ├── src/
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   ├── api/
│   │   ├── types/
│   │   ├── context/
│   │   ├── components/
│   │   └── pages/
│   └── public/
│
├── docker/
│   ├── Dockerfile.gateway
│   ├── Dockerfile.auth
│   ├── Dockerfile.user
│   ├── Dockerfile.ticket
│   ├── Dockerfile.asset
│   ├── Dockerfile.notification
│   └── Dockerfile.frontend
│
├── database/
│   └── schema.sql
│
├── docker-compose.yml
├── README.md
├── IMPLEMENTATION_GUIDE.md
└── SETUP_GUIDE.md (this file)
```

---

## Key Configuration Files

### JWT Configuration
Edit `backend/auth-service/src/main/resources/application.yml`:
```yaml
jwt:
  secret: your-super-secret-key-change-this-in-production
  expiration: 900000  # 15 minutes
  refresh-expiration: 604800000  # 7 days
```

### Database Configuration
All services use these connection details (configurable):
```
Host: localhost (or mysql in Docker)
Port: 3306
User: helpdesk_user
Password: helpdesk_pass
```

### Frontend API Configuration
Edit `frontend/.env`:
```
VITE_API_URL=http://localhost:8000/api
```

---

## Performance Optimization Tips

1. **Enable Query Caching** (MySQL):
   ```sql
   SET GLOBAL query_cache_type = 1;
   SET GLOBAL query_cache_size = 268435456;
   ```

2. **Add Database Indexes** (Already included in schema.sql):
   - Email lookups
   - Status filtering
   - Date range queries

3. **Frontend Bundle Optimization**:
   ```bash
   npm run build  # Creates optimized production build
   ```

4. **Use Production Database**:
   - Replace SQLite with MySQL in production
   - Use connection pooling
   - Enable query optimization

---

## Next Steps

### Development
1. Review the code structure
2. Understand microservice communication
3. Explore Spring Boot security features
4. Study React hooks and context API

### Production Deployment
1. Use environment variables for secrets
2. Set up CI/CD pipeline (GitHub Actions, Jenkins)
3. Use container orchestration (Kubernetes)
4. Set up monitoring and logging
5. Implement real email notifications
6. Use production-grade database

### Enhancement Ideas
1. Add message queue (RabbitMQ/Kafka)
2. Implement caching (Redis)
3. Add full-text search (Elasticsearch)
4. Real-time notifications (WebSocket)
5. File attachments for tickets
6. Advanced reporting and analytics
7. Mobile app (React Native)
8. API rate limiting

---

## Support & Documentation

- **Swagger API Docs**: http://localhost:8000/swagger-ui.html
- **README.md**: Project overview and architecture
- **IMPLEMENTATION_GUIDE.md**: Detailed technical implementation
- **Code Comments**: Inline documentation in source files

---

## Portfolio Project Highlights

This project demonstrates:
✅ Microservices architecture
✅ Spring Boot expertise
✅ React & TypeScript skills
✅ Docker containerization
✅ MySQL database design
✅ RESTful API development
✅ JWT authentication
✅ Role-based access control
✅ Professional UI/UX
✅ Enterprise-grade code quality

**Perfect for interviews and portfolio showcasing!**

---

## Estimated Timeline

- **Docker Setup**: 10-15 minutes
- **Manual Setup**: 30-45 minutes
- **First Test Login**: 50-60 minutes
- **Full Exploration**: 2-3 hours

---

## Contact & Help

For issues or questions:
1. Check the Troubleshooting section
2. Review code comments
3. Check application logs
4. Verify all prerequisites are installed

---

**Last Updated**: May 2026
**Version**: 1.0.0 - Production Ready
