# Postman Collection - Enterprise Help Desk API

## Import Collection

1. Open Postman
2. Click **Import** button (top-left)
3. Select **Helpdesk-API-Collection.postman_collection.json**
4. Collection will be imported with all endpoints

## Authentication

### Step 1: Login
```
POST /api/auth/login
Body:
{
  "email": "admin@company.com",
  "password": "Admin@123456"
}
```

**Response will include:**
- `token` - Use this in Authorization header
- `refreshToken` - Use to refresh expired tokens

### Step 2: Use Token
In Postman:
1. Go to **Tests** tab of Login request
2. Add script to auto-set token:
```javascript
var jsonData = pm.response.json();
pm.environment.set("jwt_token", jsonData.token);
```

3. Set **Authorization** header in collection:
   - Type: Bearer Token
   - Token: `{{jwt_token}}`

---

## Environment Variables

Create an environment in Postman:

```json
{
  "name": "Helpdesk Development",
  "values": [
    {
      "key": "base_url",
      "value": "http://localhost:8000/api",
      "enabled": true
    },
    {
      "key": "jwt_token",
      "value": "",
      "enabled": true
    }
  ]
}
```

---

## API Endpoints by Service

### Authentication Service (Port 8001)

#### 1. Register
```
POST {{base_url}}/auth/register
Content-Type: application/json

{
  "email": "newuser@company.com",
  "firstName": "John",
  "lastName": "Doe",
  "password": "SecurePass@123",
  "role": "EMPLOYEE",
  "department": "Sales"
}
```

#### 2. Login
```
POST {{base_url}}/auth/login
Content-Type: application/json

{
  "email": "admin@company.com",
  "password": "Admin@123456"
}
```

#### 3. Refresh Token
```
POST {{base_url}}/auth/refresh
Content-Type: application/json

{
  "refreshToken": "your_refresh_token"
}
```

#### 4. Validate Token
```
GET {{base_url}}/auth/validate?token=your_token
Authorization: Bearer {{jwt_token}}
```

#### 5. Health Check
```
GET {{base_url}}/auth/health
```

---

### User Service (Port 8002)

#### 1. Get All Users
```
GET {{base_url}}/users
Authorization: Bearer {{jwt_token}}
```

#### 2. Get User by ID
```
GET {{base_url}}/users/1
Authorization: Bearer {{jwt_token}}
```

#### 3. Get User by Email
```
GET {{base_url}}/users/email/admin@company.com
Authorization: Bearer {{jwt_token}}
```

#### 4. Create User
```
POST {{base_url}}/users
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "email": "newtech@company.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "role": "TECHNICIAN",
  "department": "IT",
  "status": "ACTIVE"
}
```

#### 5. Update User
```
PUT {{base_url}}/users/1
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "firstName": "John",
  "lastName": "Updated",
  "department": "Management"
}
```

#### 6. Delete User
```
DELETE {{base_url}}/users/1
Authorization: Bearer {{jwt_token}}
```

#### 7. Search Users
```
GET {{base_url}}/users/search?q=john
Authorization: Bearer {{jwt_token}}
```

#### 8. Get Users by Department
```
GET {{base_url}}/users/department/IT
Authorization: Bearer {{jwt_token}}
```

#### 9. Get Active Users
```
GET {{base_url}}/users/status/active
Authorization: Bearer {{jwt_token}}
```

---

### Ticket Service (Port 8003)

#### 1. Get All Tickets
```
GET {{base_url}}/tickets
Authorization: Bearer {{jwt_token}}
```

#### 2. Get Ticket by ID
```
GET {{base_url}}/tickets/1
Authorization: Bearer {{jwt_token}}
```

#### 3. Create Ticket
```
POST {{base_url}}/tickets
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "title": "Network Connection Issue",
  "description": "Cannot access office network from desk 5",
  "priority": "HIGH",
  "category": "Network"
}
```

#### 4. Update Ticket
```
PUT {{base_url}}/tickets/1
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "status": "IN_PROGRESS",
  "assignedTo": 2,
  "title": "Network Connection Issue - Updated"
}
```

#### 5. Delete Ticket
```
DELETE {{base_url}}/tickets/1
Authorization: Bearer {{jwt_token}}
```

#### 6. Search Tickets
```
GET {{base_url}}/tickets/search?query=network
Authorization: Bearer {{jwt_token}}
```

#### 7. Add Comment to Ticket
```
POST {{base_url}}/tickets/1/comments
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "commentText": "Investigated the network issue. Problem is with switch port 5."
}
```

#### 8. Get Ticket Comments
```
GET {{base_url}}/tickets/1/comments
Authorization: Bearer {{jwt_token}}
```

---

### Asset Service (Port 8004)

#### 1. Get All Assets
```
GET {{base_url}}/assets
Authorization: Bearer {{jwt_token}}
```

#### 2. Get Asset by ID
```
GET {{base_url}}/assets/1
Authorization: Bearer {{jwt_token}}
```

#### 3. Create Asset
```
POST {{base_url}}/assets
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "assetName": "Dell Latitude 7420",
  "assetType": "Laptop",
  "serialNumber": "DL123456",
  "purchaseDate": "2022-01-15",
  "warrantyExpiry": "2025-01-15",
  "condition": "Good",
  "status": "AVAILABLE"
}
```

#### 4. Update Asset
```
PUT {{base_url}}/assets/1
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "condition": "Fair",
  "status": "MAINTENANCE"
}
```

#### 5. Delete Asset
```
DELETE {{base_url}}/assets/1
Authorization: Bearer {{jwt_token}}
```

#### 6. Assign Asset to User
```
PUT {{base_url}}/assets/1/assign
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "assignedTo": 2
}
```

#### 7. Get User's Assets
```
GET {{base_url}}/assets/user/2
Authorization: Bearer {{jwt_token}}
```

---

### Notification Service (Port 8005)

#### 1. Get Notifications
```
GET {{base_url}}/notifications
Authorization: Bearer {{jwt_token}}
```

#### 2. Create Notification
```
POST {{base_url}}/notifications
Content-Type: application/json
Authorization: Bearer {{jwt_token}}

{
  "recipientId": 1,
  "notificationType": "TICKET_ASSIGNED",
  "title": "New Ticket Assignment",
  "message": "You have been assigned to ticket #1001",
  "entityType": "TICKET",
  "entityId": 1001
}
```

#### 3. Mark Notification as Read
```
PUT {{base_url}}/notifications/1/read
Authorization: Bearer {{jwt_token}}
```

---

## Testing Workflow

### 1. Test Authentication Flow
1. **Register** new user
2. **Login** with credentials
3. **Copy JWT token** from response
4. **Set token** in Postman environment
5. **Validate token** endpoint

### 2. Test User Management
1. **Get all users**
2. **Create new user**
3. **Get specific user**
4. **Update user**
5. **Search users**
6. **Delete user**

### 3. Test Ticket Management
1. **Create ticket**
2. **Get all tickets**
3. **Get ticket by ID**
4. **Update ticket status**
5. **Add comments**
6. **Get comments**

### 4. Test Asset Management
1. **Create asset**
2. **Get all assets**
3. **Assign asset to user**
4. **Get user's assets**
5. **Update asset condition**

### 5. Test Notifications
1. **Create notification**
2. **Get notifications**
3. **Mark as read**

---

## Demo Credentials

Use these for testing:

| Email | Password | Role |
|-------|----------|------|
| admin@company.com | Admin@123456 | ADMIN |
| technician@company.com | Tech@123456 | TECHNICIAN |
| employee@company.com | Emp@123456 | EMPLOYEE |

---

## Common Response Codes

| Code | Meaning |
|------|---------|
| 200 | OK - Request successful |
| 201 | Created - Resource created |
| 204 | No Content - Successful delete |
| 400 | Bad Request - Invalid input |
| 401 | Unauthorized - Invalid/missing token |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource not found |
| 500 | Server Error |

---

## API Documentation

Access Swagger UI at: **http://localhost:8000/swagger-ui.html**

All endpoints are documented with:
- Request/response schemas
- Example payloads
- Parameter descriptions
- Error responses

---

## Performance Tips

1. **Pagination**
   - Add `?page=0&pageSize=20` to list endpoints
   - Reduces data transfer

2. **Filtering**
   - Use query parameters to filter results
   - Reduces response size

3. **Field Selection**
   - Request only needed fields
   - Server returns only selected fields

4. **Caching**
   - Cache GET requests for frequently accessed data
   - Use `Cache-Control` headers

---

## Troubleshooting

### 401 Unauthorized
- Token has expired → Use refresh endpoint
- Token is missing → Set in Authorization header
- Token is invalid → Login again to get new token

### 403 Forbidden
- Role doesn't have permission → Check user role
- Endpoint requires higher privilege → Use admin account

### 404 Not Found
- Resource ID doesn't exist → Verify ID is correct
- Wrong endpoint URL → Check path

### 500 Server Error
- Check server logs
- Verify all required fields are provided
- Ensure data format is correct

---

## Tips for Testing

1. **Use Pre-request Scripts** - Set up data before request
2. **Use Test Scripts** - Validate responses
3. **Use Environments** - Switch between dev/test/prod
4. **Monitor Network** - Check request/response details
5. **Use Collections** - Organize related endpoints

---

## Useful Postman Features

- **Collections** - Organize tests
- **Environments** - Manage variables
- **Tests** - Assert response validity
- **Pre-requests** - Setup before request
- **Documentation** - Auto-generate docs
- **Mock Servers** - Test without backend
- **Monitoring** - Run collections scheduled

---

For questions or issues, refer to:
- Swagger API Docs: http://localhost:8000/swagger-ui.html
- README.md - Project overview
- SETUP_GUIDE.md - Setup instructions

Happy testing! 🚀
