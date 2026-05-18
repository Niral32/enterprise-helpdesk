# Enterprise IT Help Desk & Asset Management Platform

A comprehensive, production-ready microservices-based IT ticketing and asset management system designed for enterprise environments. Built with Java Spring Boot, React, MySQL, and Docker.

## 📋 Project Overview

This platform enables:
- **Employees**: Create and track IT support tickets
- **Technicians**: Manage, assign, and resolve tickets
- **Admins**: Oversee users, assets, and generate reports
- **Asset Managers**: Manage IT inventory and asset assignments

## 🏗️ Architecture Overview

### Microservices Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     React Frontend (Port 5173)              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│             API Gateway Service (Port 8000)                 │
│         - Route management                                  │
│         - JWT validation                                    │
│         - CORS configuration                                │
└──┬───────────┬───────────┬────────────┬──────────┬──────────┘
   │           │           │            │          │
   ▼           ▼           ▼            ▼          ▼
┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────────┐
│Auth  │  │User  │  │Ticket│  │Asset │  │Notif │  │Databases │
│Svc   │  │Svc   │  │Svc   │  │Svc   │  │Svc   │  │(MySQL)   │
│8001  │  │8002  │  │8003  │  │8004  │  │8005  │  │          │
└──────┘  └──────┘  └──────┘  └──────┘  └──────┘  └──────────┘
```

## 🛠️ Tech Stack

### Backend
- **Framework**: Java 17, Spring Boot 3.x
- **Security**: Spring Security, JWT (JSON Web Tokens)
- **Database**: MySQL 8.0
- **ORM**: Spring Data JPA, Hibernate
- **API Documentation**: Swagger/OpenAPI 3.0
- **Testing**: JUnit 5, Mockito
- **Build**: Maven 3.8+

### Frontend
- **Framework**: React 18.x with TypeScript
- **Build Tool**: Vite
- **Styling**: Tailwind CSS
- **HTTP Client**: Axios
- **Routing**: React Router v6
- **Form Handling**: React Hook Form
- **State Management**: React Context API

### Infrastructure
- **Containerization**: Docker & Docker Compose
- **Database**: MySQL 8.0

## 📦 Microservices Overview

### 1. API Gateway Service (Port 8000)
- Central entry point for all requests
- Routing to microservices
- JWT token validation
- CORS handling
- Load balancing ready

### 2. Authentication Service (Port 8001)
- User registration and login
- JWT token generation and validation
- Password encryption (BCrypt)
- Role management (ADMIN, TECHNICIAN, EMPLOYEE)
- Token refresh mechanism

### 3. User Service (Port 8002)
- User profile management
- Department assignment
- User search and filtering
- Technician management
- Employee directory

### 4. Ticket Service (Port 8003)
- Create, read, update support tickets
- Ticket assignment to technicians
- Status tracking (OPEN → IN_PROGRESS → RESOLVED → CLOSED)
- Priority levels (LOW, MEDIUM, HIGH, CRITICAL)
- Comments and ticket history
- Advanced filtering and search

### 5. Asset Service (Port 8004)
- IT asset inventory management
- Asset assignment to employees
- Warranty and maintenance tracking
- Asset condition monitoring
- Asset types: Laptops, Desktops, Monitors, Phones, Printers, Network Devices

### 6. Notification Service (Port 8005)
- Email notification simulation
- Notification logging
- Event-driven architecture ready
- Notifications for: ticket creation, assignment, resolution, status changes

## 🗄️ Database Schema

Each microservice has its own database/schema:
- `auth_db`: User credentials and authentication data
- `user_db`: User profiles and management
- `ticket_db`: Support tickets and comments
- `asset_db`: Asset inventory
- `notification_db`: Notification logs

## 🔐 Security Features

- ✅ JWT-based authentication
- ✅ Role-based access control (RBAC)
- ✅ BCrypt password hashing
- ✅ Secure token expiration (15 min access, 7 day refresh)
- ✅ Protected API endpoints
- ✅ CORS configuration
- ✅ Input validation and sanitization

## 🎨 Frontend Features

### Authentication Pages
- Login with email/password
- User registration with role selection
- Password recovery (placeholder)

### Dashboard
- Real-time statistics (total tickets, open tickets, resolved, critical)
- Charts and visualizations
- Recent tickets overview
- Quick action buttons

### Ticket Management
- Create new tickets with categories and priorities
- View all tickets with advanced filtering
- Search by title, ID, or status
- Assign tickets (admin/technician only)
- Add comments and update status
- Ticket history and timeline

### User Management (Admin Only)
- View all users
- Search and filter users
- Update user roles and status
- Create new users

### Asset Management (Admin Only)
- Inventory of all IT assets
- Asset assignment to employees
- Track warranty and condition
- Asset history and audit trail

### User Profile
- View personal information
- Update password
- View assigned tickets
- View assigned assets

## 📊 Dashboard Metrics

- Total Tickets Created
- Open Tickets (requiring attention)
- Resolved Tickets (this month)
- Critical Tickets (high priority)
- Total Assets
- Recent Activity Feed

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Node.js 16+
- Docker & Docker Compose
- MySQL 8.0+ (or use Docker)
- Git

### Quick Start with Docker

```bash
# Clone the repository
git clone <repo-url>
cd enterprise-helpdesk

# Start all services with Docker Compose
docker-compose up -d

# Services will be available at:
# Frontend: http://localhost:5173
# API Gateway: http://localhost:8000
# API Docs: http://localhost:8000/swagger-ui.html
```

### Manual Setup

#### Backend Setup
```bash
cd backend
mvn clean install
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8000"
```

#### Frontend Setup
```bash
cd frontend
npm install
npm run dev
```

## 📚 API Documentation

Full Swagger/OpenAPI documentation available at:
- `http://localhost:8000/swagger-ui.html`

### Key API Endpoints

**Authentication**
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login
- `POST /api/auth/refresh` - Refresh JWT token

**Tickets**
- `GET /api/tickets` - List all tickets
- `POST /api/tickets` - Create new ticket
- `GET /api/tickets/{id}` - Get ticket details
- `PUT /api/tickets/{id}` - Update ticket
- `POST /api/tickets/{id}/comments` - Add comment

**Users**
- `GET /api/users` - List all users
- `POST /api/users` - Create user
- `GET /api/users/{id}` - Get user details
- `PUT /api/users/{id}` - Update user

**Assets**
- `GET /api/assets` - List all assets
- `POST /api/assets` - Create asset
- `PUT /api/assets/{id}/assign` - Assign asset to user

## 📋 Test Credentials

**Admin Account**
- Email: admin@company.com
- Password: Admin@123456

**Technician Account**
- Email: technician@company.com
- Password: Tech@123456

**Employee Account**
- Email: employee@company.com
- Password: Emp@123456

## 🧪 Testing

### Run Unit Tests
```bash
mvn test
```

### Run Integration Tests
```bash
mvn verify
```

### Postman Collection
Import `postman-collection.json` into Postman for API testing.

## 📁 Project Structure

```
enterprise-helpdesk/
├── backend/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── user-service/
│   ├── ticket-service/
│   ├── asset-service/
│   └── notification-service/
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   └── types/
│   └── package.json
├── docker-compose.yml
├── Dockerfile.gateway
├── Dockerfile.auth
├── Dockerfile.user
├── Dockerfile.ticket
├── Dockerfile.asset
├── Dockerfile.notification
├── database/
│   ├── schema.sql
│   └── init-data.sql
└── README.md
```

## 🔄 Workflow Example

1. **Employee creates a ticket**
   - Visits dashboard → Click "Create Ticket"
   - Fills form (title, description, category, priority)
   - Submits → Ticket created with OPEN status

2. **Notification sent**
   - Notification service detects new ticket
   - Logs notification in database
   - (In production: sends email to admins)

3. **Admin assigns ticket**
   - Admin views tickets dashboard
   - Finds the new ticket
   - Assigns to a technician

4. **Technician updates ticket**
   - Technician receives assignment notification
   - Updates status to IN_PROGRESS
   - Adds comments with troubleshooting steps

5. **Employee reviews and closes**
   - Employee sees status update notification
   - Reviews technician's comments
   - Confirms resolution and closes ticket

## 💡 Interview Talking Points

- **Microservices Design**: Explain the rationale for service decomposition and independence
- **Authentication & Security**: Discuss JWT, token refresh, password hashing, RBAC
- **Scalability**: Services can scale independently; database per service; API Gateway handles routing
- **Frontend Architecture**: Component-based design, custom hooks, state management, error handling
- **Docker Deployment**: Containerization for consistent environments, orchestration via Docker Compose
- **Database Design**: Normalized schemas, proper indexing, relationships, data integrity
- **Testing Strategy**: Unit tests for business logic, integration tests for services
- **Error Handling**: Global exception handlers, meaningful error messages, proper HTTP status codes

## 🚀 Performance Considerations

- JWT token caching on frontend
- Database query optimization with proper indexes
- API response pagination
- Frontend component lazy loading
- Docker resource limits configured
- Connection pooling for database

## 📈 Future Enhancement Ideas

- Message queue (RabbitMQ/Kafka) for async notifications
- Redis caching layer
- Service discovery (Eureka/Consul)
- API rate limiting
- Advanced audit logging
- Real email integration
- Mobile app
- Analytics dashboard
- ML-based ticket categorization
- SLA tracking

## 🤝 Contributing

This is a portfolio project. For enhancements, follow these practices:
- Feature branches for new features
- Pull request reviews
- Comprehensive testing
- Clean code standards

## 📄 License

Personal portfolio project.

## 👨‍💼 Portfolio Project Information

**Best For**: Full Stack Java Developer Interviews
- Demonstrates understanding of microservices architecture
- Shows proficiency in Java Spring Boot ecosystem
- Displays modern frontend development skills (React, TypeScript)
- Proves DevOps knowledge (Docker, containerization)
- Showcases best practices (security, testing, documentation)

## 📞 Support

For questions or issues with setup, refer to the project structure and individual service README files.

---

**Last Updated**: May 2026
**Version**: 1.0.0 (Production Ready)
