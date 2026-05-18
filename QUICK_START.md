# ⚡ Quick Start Guide - 5 Minutes to Running Application

## Option 1: Docker (Easiest - Recommended) ⭐

```bash
# 1. Navigate to project directory
cd enterprise-helpdesk

# 2. Start all services (one command!)
docker-compose up --build

# 3. Wait ~30 seconds for all services to start

# 4. Open in browser
# Frontend: http://localhost:5173
# API Docs: http://localhost:8000/swagger-ui.html

# 5. Login with demo credentials
# Email: admin@company.com
# Password: Admin@123456
```

**That's it! ✅**

---

## Option 2: Manual Setup (If Docker unavailable)

### Prerequisites (must be installed):
- Java 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.8+

### Steps:

**Terminal 1: Database**
```bash
# Connect to MySQL and run:
mysql -u root -p < database/schema.sql
```

**Terminal 2-7: Start Services** (one command each in separate terminals)
```bash
# Terminal 2
cd backend/auth-service && mvn spring-boot:run

# Terminal 3
cd backend/user-service && mvn spring-boot:run

# Terminal 4
cd backend/ticket-service && mvn spring-boot:run

# Terminal 5
cd backend/asset-service && mvn spring-boot:run

# Terminal 6
cd backend/notification-service && mvn spring-boot:run

# Terminal 7
cd backend/api-gateway && mvn spring-boot:run
```

**Terminal 8: Frontend**
```bash
cd frontend
npm install
npm run dev
```

**Access**: http://localhost:5173

---

## Demo Credentials

| Role | Email | Password |
|------|-------|----------|
| Admin | admin@company.com | Admin@123456 |
| Technician | technician@company.com | Tech@123456 |
| Employee | employee@company.com | Emp@123456 |

---

## Verify Everything is Working

```bash
# Check all services
curl http://localhost:8000/api/auth/health

# Should respond: "Auth service is running"
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Port already in use | `docker-compose down` and try again |
| MySQL connection error | Check MySQL is running: `brew services list` |
| Frontend blank page | Press F12, check console errors. Hard refresh (Cmd+Shift+R) |
| API not responding | Wait 30 seconds after `docker-compose up` |

---

## Next Steps

1. **Explore Dashboard**: View tickets and statistics
2. **Create Ticket**: Test the creation workflow
3. **Check API Docs**: http://localhost:8000/swagger-ui.html
4. **Review Code**: Check the implementation
5. **Deploy**: Follow SETUP_GUIDE.md for production

---

## Key URLs

| Service | URL |
|---------|-----|
| Web App | http://localhost:5173 |
| API | http://localhost:8000 |
| API Docs | http://localhost:8000/swagger-ui.html |
| Auth Service | http://localhost:8001 |
| MySQL | localhost:3306 |

---

## Stop the Application

```bash
# Docker
docker-compose down

# Manual (just kill the terminal windows or Ctrl+C)
```

---

## Full Documentation

- **README.md** - Project overview
- **IMPLEMENTATION_GUIDE.md** - Technical details
- **SETUP_GUIDE.md** - Complete setup instructions

---

**You're all set! 🎉**
