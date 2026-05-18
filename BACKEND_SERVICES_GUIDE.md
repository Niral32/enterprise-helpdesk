# Complete Backend Microservices Implementation Guide

## Overview
This guide provides complete implementations for all remaining microservices. Each service follows the same architecture pattern as demonstrated in the Auth Service and User Service.

---

## 1. Ticket Service (Port 8003)

### Key Classes to Implement:

#### Entity: Ticket.java
```java
@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    private TicketPriority priority;
    
    @Column(nullable = false)
    private String category;
    
    @Enumerated(EnumType.STRING)
    private TicketStatus status;
    
    @Column(nullable = false)
    private Long createdBy;
    
    private Long assignedTo;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    private LocalDateTime closedAt;
    
    public enum TicketPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum TicketStatus {
        OPEN, IN_PROGRESS, RESOLVED, CLOSED
    }
}
```

#### Entity: Comment.java
```java
@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long ticketId;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(columnDefinition = "TEXT")
    private String commentText;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

#### Repository: TicketRepository.java
```java
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStatus(Ticket.TicketStatus status);
    List<Ticket> findByPriority(Ticket.TicketPriority priority);
    List<Ticket> findByCreatedBy(Long userId);
    List<Ticket> findByAssignedTo(Long userId);
    List<Ticket> findByCategory(String category);
    
    @Query("SELECT t FROM Ticket t WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Ticket> searchTickets(@Param("query") String query);
}
```

#### Service: TicketService.java
```java
@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    
    // CRUD Operations
    public TicketDTO createTicket(CreateTicketRequest request, Long userId) {
        Ticket ticket = Ticket.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .priority(Ticket.TicketPriority.valueOf(request.getPriority()))
            .category(request.getCategory())
            .status(Ticket.TicketStatus.OPEN)
            .createdBy(userId)
            .build();
        return TicketDTO.fromEntity(ticketRepository.save(ticket));
    }
    
    public List<TicketDTO> getAllTickets() {
        return ticketRepository.findAll().stream()
            .map(TicketDTO::fromEntity)
            .collect(Collectors.toList());
    }
    
    public TicketDTO getTicketById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));
        return TicketDTO.fromEntity(ticket);
    }
    
    public TicketDTO updateTicket(Long id, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));
        
        if (request.getStatus() != null) {
            ticket.setStatus(Ticket.TicketStatus.valueOf(request.getStatus()));
        }
        if (request.getAssignedTo() != null) {
            ticket.setAssignedTo(request.getAssignedTo());
        }
        if (request.getTitle() != null) {
            ticket.setTitle(request.getTitle());
        }
        
        return TicketDTO.fromEntity(ticketRepository.save(ticket));
    }
    
    public void deleteTicket(Long id) {
        ticketRepository.deleteById(id);
    }
    
    // Comment Operations
    public CommentDTO addComment(Long ticketId, CommentRequest request, Long userId) {
        Comment comment = Comment.builder()
            .ticketId(ticketId)
            .userId(userId)
            .commentText(request.getCommentText())
            .build();
        return CommentDTO.fromEntity(commentRepository.save(comment));
    }
    
    public List<CommentDTO> getTicketComments(Long ticketId) {
        return commentRepository.findByTicketId(ticketId).stream()
            .map(CommentDTO::fromEntity)
            .collect(Collectors.toList());
    }
    
    // Search and Filter
    public List<TicketDTO> searchTickets(String query) {
        return ticketRepository.searchTickets(query).stream()
            .map(TicketDTO::fromEntity)
            .collect(Collectors.toList());
    }
}
```

#### Controller: TicketController.java
```java
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TicketController {
    private final TicketService ticketService;
    
    @PostMapping
    public ResponseEntity<TicketDTO> createTicket(@RequestBody CreateTicketRequest request) {
        Long userId = 1L; // Get from security context in production
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ticketService.createTicket(request, userId));
    }
    
    @GetMapping
    public ResponseEntity<List<TicketDTO>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TicketDTO> getTicket(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<TicketDTO> updateTicket(@PathVariable Long id, 
                                                  @RequestBody UpdateTicketRequest request) {
        return ResponseEntity.ok(ticketService.updateTicket(id, request));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{ticketId}/comments")
    public ResponseEntity<CommentDTO> addComment(@PathVariable Long ticketId,
                                                 @RequestBody CommentRequest request) {
        Long userId = 1L; // Get from security context
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ticketService.addComment(ticketId, request, userId));
    }
    
    @GetMapping("/{ticketId}/comments")
    public ResponseEntity<List<CommentDTO>> getComments(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getTicketComments(ticketId));
    }
}
```

---

## 2. Asset Service (Port 8004)

### Key Implementation Pattern:

```java
@Entity
@Table(name = "assets")
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String assetId;
    
    @Column(nullable = false)
    private String assetName;
    
    @Column(nullable = false)
    private String assetType;
    
    private String serialNumber;
    private Long assignedTo;
    private LocalDate purchaseDate;
    private LocalDate warrantyExpiry;
    private String condition;
    
    @Enumerated(EnumType.STRING)
    private AssetStatus status;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    public enum AssetStatus {
        AVAILABLE, IN_USE, MAINTENANCE, RETIRED
    }
}
```

### Service Methods:
- `getAllAssets()` - List all assets
- `getAssetById(Long id)` - Get specific asset
- `createAsset(CreateAssetRequest)` - Create new asset
- `updateAsset(Long id, UpdateAssetRequest)` - Update asset
- `deleteAsset(Long id)` - Delete asset
- `assignAsset(Long assetId, Long userId)` - Assign to user
- `getAssetsByUser(Long userId)` - Get user's assets
- `getAssetsByType(String type)` - Filter by type
- `getAvailableAssets()` - List available only

---

## 3. Notification Service (Port 8005)

### Key Implementation Pattern:

```java
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long recipientId;
    
    @Column(nullable = false)
    private String notificationType; // TICKET_CREATED, TICKET_ASSIGNED, etc.
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    private String entityType; // TICKET, ASSET, etc.
    private Long entityId;
    
    @Column(nullable = false)
    private Boolean isRead = false;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    private LocalDateTime readAt;
}
```

### Service Methods:
- `sendNotification(NotificationRequest)` - Create notification
- `getNotifications(Long userId)` - Get user's notifications
- `markAsRead(Long notificationId)` - Mark as read
- `deleteNotification(Long id)` - Delete notification
- `onTicketCreated(TicketEvent)` - Event listener
- `onTicketAssigned(TicketAssignedEvent)` - Event listener
- `onTicketResolved(TicketResolvedEvent)` - Event listener

---

## Implementation Checklist

### For Each Service:

#### Structure
- [ ] Create `pom.xml` with dependencies
- [ ] Create `*ServiceApplication.java` main class
- [ ] Create entity classes in `entity/` package
- [ ] Create DTOs in `dto/` package
- [ ] Create repository in `repository/` package
- [ ] Create service in `service/` package
- [ ] Create controller in `controller/` package
- [ ] Create exceptions in `exception/` package
- [ ] Create configuration in `config/` package
- [ ] Create `application.yml` configuration

#### Service Pattern
```
1. Entity (JPA Entity with @Entity, @Table)
2. Repository (Spring Data JPA Repository)
3. DTO (Data Transfer Object for API)
4. Service (Business logic, transactions)
5. Controller (REST endpoints, HTTP handling)
6. Exception (Custom exceptions)
7. Configuration (Security, CORS, etc.)
```

#### Endpoint Pattern
```java
// List
@GetMapping
List<DTO> getAll()

// Get by ID
@GetMapping("/{id}")
DTO getById(@PathVariable Long id)

// Create
@PostMapping
DTO create(@RequestBody CreateRequest)

// Update
@PutMapping("/{id}")
DTO update(@PathVariable Long id, @RequestBody UpdateRequest)

// Delete
@DeleteMapping("/{id}")
void delete(@PathVariable Long id)

// Search
@GetMapping("/search")
List<DTO> search(@RequestParam String query)

// Health
@GetMapping("/health")
String health()
```

---

## DTO Structure Pattern

Each service should have:

1. **Entity DTO** - All fields from entity
2. **Create Request** - Required fields only
3. **Update Request** - Optional fields
4. **Response DTO** - What gets returned

Example:
```java
// UserDTO - Full entity
public class UserDTO {
    Long id;
    String email;
    String firstName;
    String lastName;
    String role;
}

// CreateUserRequest - Creation only
public class CreateUserRequest {
    @NotBlank String email;
    @NotBlank String firstName;
    @NotBlank String lastName;
}

// UpdateUserRequest - Update only
public class UpdateUserRequest {
    String firstName;
    String lastName;
    String status;
}
```

---

## Configuration Files Pattern

Each service needs `application.yml`:

```yaml
spring:
  application:
    name: service-name
  datasource:
    url: jdbc:mysql://mysql:3306/service_db
    username: helpdesk_user
    password: helpdesk_pass
  jpa:
    hibernate:
      ddl-auto: update

server:
  port: PORT_NUMBER

logging:
  level:
    com.helpdesk: DEBUG
```

---

## Testing Pattern

Create tests in `src/test/java`:

```java
@SpringBootTest
public class TicketServiceTest {
    
    @Mock
    private TicketRepository ticketRepository;
    
    @InjectMocks
    private TicketService ticketService;
    
    @Test
    public void testCreateTicket() {
        // Arrange
        CreateTicketRequest request = new CreateTicketRequest(...);
        
        // Act
        TicketDTO result = ticketService.createTicket(request, 1L);
        
        // Assert
        assertNotNull(result);
        assertEquals("Test Title", result.getTitle());
    }
}
```

---

## Dependencies Required

All services should have in `pom.xml`:

```xml
<!-- Spring Boot Starters -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>

<!-- Swagger -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

---

## Common Patterns Used

### 1. Custom Exceptions
```java
public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String message) {
        super(message);
    }
}
```

### 2. Global Exception Handler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TicketNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(404, ex.getMessage()));
    }
}
```

### 3. Service Layer Pattern
```java
@Service
@RequiredArgsConstructor
@Transactional
public class TicketService {
    private final TicketRepository repository;
    
    public List<TicketDTO> getAll() {
        return repository.findAll().stream()
            .map(TicketDTO::fromEntity)
            .collect(Collectors.toList());
    }
}
```

### 4. Controller Pattern
```java
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TicketController {
    private final TicketService service;
    
    @PostMapping
    public ResponseEntity<TicketDTO> create(@RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(request));
    }
}
```

---

## Database Indexing Strategy

For performance, create indexes:

```sql
-- Ticket Service indexes
CREATE INDEX idx_ticket_status ON tickets(status);
CREATE INDEX idx_ticket_priority ON tickets(priority);
CREATE INDEX idx_ticket_created_by ON tickets(created_by);
CREATE INDEX idx_ticket_assigned_to ON tickets(assigned_to);
CREATE INDEX idx_ticket_created_at ON tickets(created_at DESC);

-- Asset Service indexes
CREATE INDEX idx_asset_type ON assets(asset_type);
CREATE INDEX idx_asset_status ON assets(status);
CREATE INDEX idx_asset_assigned_to ON assets(assigned_to);

-- Notification Service indexes
CREATE INDEX idx_notification_recipient ON notifications(recipient_id);
CREATE INDEX idx_notification_is_read ON notifications(is_read);
CREATE INDEX idx_notification_created ON notifications(created_at DESC);
```

---

## Naming Conventions

- **Classes**: PascalCase (TicketService, UserDTO)
- **Methods**: camelCase (createTicket, getAllUsers)
- **Variables**: camelCase (userId, ticketId)
- **Constants**: UPPER_SNAKE_CASE (DEFAULT_PAGE_SIZE, MAX_RETRIES)
- **Database**: snake_case (user_id, created_at)
- **Packages**: lowercase.dotted (com.helpdesk.ticket)

---

## Build & Run Commands

```bash
# Build all services
mvn clean install

# Build specific service
mvn -f backend/ticket-service/pom.xml clean install

# Run service
mvn spring-boot:run -f backend/ticket-service/pom.xml

# Run tests
mvn test

# Package for Docker
mvn package -DskipTests
```

---

## Deployment Checklist

- [ ] All services build successfully
- [ ] All tests pass
- [ ] API documentation generated
- [ ] Docker images build
- [ ] Docker Compose runs all services
- [ ] Database migrations execute
- [ ] API endpoints respond correctly
- [ ] Frontend connects successfully
- [ ] Security configured (CORS, HTTPS in prod)
- [ ] Logging configured
- [ ] Error handling working
- [ ] Validation implemented
- [ ] Authorization checks in place

---

## Performance Optimization

1. **Database**
   - Add indexes on frequently queried columns
   - Use pagination for list endpoints
   - Implement query caching

2. **API**
   - Implement response compression
   - Use pagination (default 20 items per page)
   - Cache frequently accessed data

3. **Code**
   - Use @Transactional(readOnly = true) for read operations
   - Implement lazy loading for relationships
   - Use batch operations for bulk updates

---

## Security Checklist

- [ ] JWT token validation
- [ ] Role-based access control
- [ ] Input validation on all endpoints
- [ ] SQL injection prevention (use JPA)
- [ ] Password hashing (BCrypt)
- [ ] HTTPS in production
- [ ] CORS properly configured
- [ ] Rate limiting implemented
- [ ] Sensitive data not logged

---

**All services follow this same pattern and architecture. Implement each one consistently for a professional, maintainable codebase.**
