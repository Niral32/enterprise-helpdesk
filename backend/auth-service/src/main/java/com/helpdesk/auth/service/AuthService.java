package com.helpdesk.auth.service;

import com.helpdesk.auth.dto.AdminProvisionRequest;
import com.helpdesk.auth.dto.AdminProvisionResponse;
import com.helpdesk.auth.dto.AuthRequest;
import com.helpdesk.auth.dto.AuthResponse;
import com.helpdesk.auth.dto.DirectoryUserSnapshot;
import com.helpdesk.auth.dto.EnableDirectoryLoginRequest;
import com.helpdesk.auth.dto.InternalAccountSyncRequest;
import com.helpdesk.auth.dto.RegisterRequest;
import com.helpdesk.auth.dto.TokenRefreshRequest;
import com.helpdesk.auth.entity.User;
import com.helpdesk.auth.exception.AuthException;
import com.helpdesk.auth.exception.UserAlreadyExistsException;
import com.helpdesk.auth.repository.UserRepository;
import com.helpdesk.auth.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Authentication service - handles user registration, login, and token management
 */
@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;
    private final String userServiceBaseUrl;

    @Value("${helpdesk.internal-api.secret:}")
    private String internalApiSecret;

    @PersistenceContext
    private EntityManager entityManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RestTemplate restTemplate,
            @Value("${helpdesk.user-service.base-url:http://localhost:8002}") String userServiceBaseUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.restTemplate = restTemplate;
        this.userServiceBaseUrl = userServiceBaseUrl.replaceAll("/$", "");
    }

    /**
     * Register a new user
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        log.info("Registering new user with email: {}", email);

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }

        // Create new user — public registration is always end-user (EMPLOYEE); never trust client role
        User user = request.toUser();
        user.setEmail(email);
        if (request.getRole() != null && !request.getRole().isBlank()
            && !User.UserRole.EMPLOYEE.name().equalsIgnoreCase(request.getRole().trim())) {
            log.warn("Ignoring requested role '{}' for public registration of {}", request.getRole(), request.getEmail());
        }
        user.setRole(User.UserRole.EMPLOYEE);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Save user
        user = userRepository.save(user);
        log.info("User registered successfully with id: {}", user.getId());

        syncUserProfileToDirectory(user, request.getPassword());

        // Generate tokens
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getId().toString(), user.getRole().toString());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), user.getId().toString());

        return AuthResponse.fromUser(user, token, refreshToken, jwtTokenProvider.getExpirationTime());
    }

    /**
     * Admin creates a user that can log in: row in auth_db + bootstrap to user_db (same id as JWT).
     */
    @Transactional
    public AdminProvisionResponse adminProvisionUser(AdminProvisionRequest request, String requesterRole) {
        if (requesterRole == null || !"ADMIN".equalsIgnoreCase(requesterRole.trim())) {
            throw new AuthException("Only administrators may create users this way.");
        }

        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }

        User.UserRole role;
        try {
            role = User.UserRole.valueOf(request.getRole().trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid role: " + request.getRole());
        }

        User user = User.builder()
            .email(email)
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(role)
            .status(User.UserStatus.ACTIVE)
            .department(request.getDepartment())
            .phone(request.getPhone())
            .active(true)
            .build();

        user = userRepository.save(user);
        log.info("Admin provisioned user id={} email={} role={}", user.getId(), email, role);

        syncUserProfileToDirectory(user, request.getPassword());

        return AdminProvisionResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .role(user.getRole().name())
            .build();
    }

    /**
     * Called by user-service to mirror an admin email change into auth_db.
     * Uniqueness has already been validated in user_db; we revalidate here.
     */
    @Transactional
    public void applyEmailSync(Long userId, String newEmailRaw) {
        String newEmail = normalizeEmail(newEmailRaw);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthException("User not found with id: " + userId));
        Optional<User> clash = userRepository.findByEmail(newEmail);
        if (clash.isPresent() && !clash.get().getId().equals(userId)) {
            throw new UserAlreadyExistsException("Email already in use in auth_db.");
        }
        user.setEmail(newEmail);
        userRepository.save(user);
        log.info("Applied email sync for userId={} -> {}", userId, newEmail);
    }

    /**
     * Mirror first/last name updates from user-service. Login uses these
     * fields to populate the AuthResponse, so without this the header shows
     * the stale name forever.
     */
    @Transactional
    public void applyNameSync(Long userId, String firstName, String lastName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthException("User not found with id: " + userId));
        if (firstName != null && !firstName.isBlank()) user.setFirstName(firstName);
        if (lastName != null && !lastName.isBlank()) user.setLastName(lastName);
        userRepository.save(user);
        log.info("Applied name sync for userId={} -> {} {}", userId, firstName, lastName);
    }

    /**
     * Mirror a role change made in user-service. Future logins issue a JWT
     * with the new role. Existing tokens keep their old role until they
     * expire / refresh (the refresh path re-reads from auth_db so it picks
     * the new role up). Cannot demote/promote to ADMIN here — that's enforced
     * upstream in user-service.changeRole.
     */
    @Transactional
    public void applyRoleSync(Long userId, String roleStr) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthException("User not found with id: " + userId));
        User.UserRole role;
        try {
            role = User.UserRole.valueOf(roleStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AuthException("Invalid role: " + roleStr);
        }
        user.setRole(role);
        userRepository.save(user);
        log.info("Applied role sync for userId={} -> {}", userId, role);
    }

    /**
     * Mirror a profile-image URL update so AuthResponse can include the
     * latest avatar on next login (no extra round-trip needed).
     */
    @Transactional
    public void applyProfileImageSync(Long userId, String profileImageUrl) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthException("User not found with id: " + userId));
        user.setProfileImageUrl(profileImageUrl);
        userRepository.save(user);
        log.info("Applied profile-image sync for userId={} url={}", userId, profileImageUrl);
    }

    /**
     * Called by user-service when profile status changes so login matches directory.
     */
    @Transactional
    public void applyAccountSync(InternalAccountSyncRequest body) {
        User user = userRepository.findById(body.getUserId())
            .orElseThrow(() -> new AuthException("User not found with id: " + body.getUserId()));
        user.setActive(body.getActive());
        user.setStatus(body.getStatus());
        userRepository.save(user);
        log.info("Synced auth account id={} active={} status={}", body.getUserId(), body.getActive(), body.getStatus());
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Login user with email and password
     */
    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        String email = normalizeEmail(request.getEmail());
        log.info("Login attempt for email: {}", email);

        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            found = userRepository.findByEmailIgnoreCase(email);
        }
        if (found.isEmpty()) {
            if (internalApiSecret != null && !internalApiSecret.isBlank()
                    && fetchDirectoryUserSnapshot(email).isPresent()) {
                throw new AuthException(
                    "An employee profile exists for this email, but sign-in is not activated. "
                        + "Ask an administrator to open Manage Users and use \"Enable sign-in\", "
                        + "or register if you have not been added to the directory yet.",
                    403);
            }
            throw new AuthException("Invalid email or password");
        }
        User user = found.get();

        if (!Boolean.TRUE.equals(user.getActive()) || user.getStatus() != User.UserStatus.ACTIVE) {
            throw new AuthException(loginDeniedForAccountStateMessage(user), 403);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Invalid password attempt for email: {}", request.getEmail());
            throw new AuthException("Invalid email or password");
        }

        log.info("User logged in successfully: {}", email);

        // Generate tokens
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getId().toString(), user.getRole().toString());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), user.getId().toString());

        return AuthResponse.fromUser(user, token, refreshToken, jwtTokenProvider.getExpirationTime());
    }

    /**
     * Clear reason for 403 when the row exists but login is not allowed (synced from user-service, etc.).
     */
    private static String loginDeniedForAccountStateMessage(User user) {
        if (!Boolean.TRUE.equals(user.getActive())) {
            return "Account is blocked or deactivated. Contact administrator.";
        }
        return switch (user.getStatus()) {
            case SUSPENDED -> "Account is blocked.";
            case INACTIVE -> "Account is deactivated.";
            case PENDING -> "Account is pending activation.";
            case ACTIVE -> "Invalid email or password";
        };
    }

    /**
     * Refresh JWT token using refresh token
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        log.info("Attempting to refresh token");

        String refreshToken = request.getRefreshToken();

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthException("Invalid or expired refresh token");
        }

        // Get email from refresh token
        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        if (email == null) {
            throw new AuthException("Could not extract email from refresh token");
        }

        User user = userRepository.findByEmail(email)
            .or(() -> userRepository.findByEmailIgnoreCase(email))
            .orElseThrow(() -> new AuthException("Invalid or expired refresh token"));

        if (!Boolean.TRUE.equals(user.getActive()) || user.getStatus() != User.UserStatus.ACTIVE) {
            throw new AuthException(loginDeniedForAccountStateMessage(user), 403);
        }

        log.info("Token refreshed successfully for user: {}", email);

        // Generate new tokens
        String newAccessToken = jwtTokenProvider.generateToken(user.getEmail(), user.getId().toString(), user.getRole().toString());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), user.getId().toString());

        return AuthResponse.fromUser(user, newAccessToken, newRefreshToken, jwtTokenProvider.getExpirationTime());
    }

    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    /**
     * Get user details from token
     */
    @Transactional(readOnly = true)
    public User getUserFromToken(String token) {
        String email = jwtTokenProvider.getEmailFromToken(token);
        if (email == null) {
            throw new AuthException("Invalid token");
        }

        return userRepository.findByEmail(email)
            .orElseThrow(() -> new AuthException("User not found"));
    }

    /**
     * Admin-driven password reset.
     *
     * Encodes and stores the new hash in {@code auth_db.users}, then mirrors
     * it to {@code user_db} via the user-service internal endpoint so both
     * stores stay consistent. Both writes happen, but a failure of the sync
     * call is logged and not bubbled up — the next admin reset (or a future
     * background reconcile) will re-sync. Login uses auth_db, so the
     * reset is effective immediately.
     *
     * @param requesterRole role from the X-User-Role header (defense-in-depth)
     */
    @Transactional
    public void adminResetPassword(Long targetUserId, String newPassword, String requesterRole) {
        if (requesterRole == null || !"ADMIN".equalsIgnoreCase(requesterRole.trim())) {
            throw new AuthException("Only administrators may reset passwords for other users.");
        }

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new AuthException("User not found with id: " + targetUserId));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Admin reset password for user id={} email={}", user.getId(), user.getEmail());
        mirrorPasswordToUserService(user.getId(), newPassword);
    }

    /**
     * Admin: create auth credentials for an existing user_db row (directory-only / legacy users).
     * Inserts into auth_db with the same {@code id} as the directory for JWT / ticket consistency.
     */
    @Transactional
    public void adminEnableDirectoryLogin(EnableDirectoryLoginRequest request, String requesterRole) {
        if (requesterRole == null || !"ADMIN".equalsIgnoreCase(requesterRole.trim())) {
            throw new AuthException("Only administrators may enable sign-in.");
        }
        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent() || userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new UserAlreadyExistsException(
                "Sign-in is already enabled for this email. Use Reset password from the user list instead.");
        }
        DirectoryUserSnapshot dir = fetchDirectoryUserSnapshot(email)
            .orElseThrow(() -> new AuthException(
                "No directory profile found for this email. Create the user first or check the spelling.",
                404));
        if (dir.getId() == null) {
            throw new AuthException("Directory profile is invalid (missing id).", 500);
        }

        userRepository.findById(dir.getId()).ifPresent(existing -> {
            String existingEmail = normalizeEmail(existing.getEmail());
            if (!email.equals(existingEmail)) {
                throw new AuthException(
                    "Cannot enable sign-in: auth_db already has user id "
                        + dir.getId()
                        + " for another email ("
                        + existing.getEmail()
                        + "). auth_db and user_db ids must match the same person. Delete or repair the conflicting "
                        + "row in auth_db, or fix directory/user data before trying again.",
                    409);
            }
        });

        String raw = request.getNewPassword();
        String encoded = passwordEncoder.encode(raw);
        try {
            insertAuthUserFromDirectoryNative(dir, email, encoded);
            entityManager.flush();
        } catch (RuntimeException ex) {
            // Surface the real cause (most common is "Data truncated for
            // column 'role'" when the auth_db schema predates the VARCHAR(32)
            // migration). Without this, the admin sees a generic 500.
            log.error("Enable sign-in failed for id={} email={} role={}: {}",
                dir.getId(), email, dir.getRole(), ex.getMessage(), ex);
            throw new AuthException(
                "Could not create the sign-in account: " + rootCauseMessage(ex)
                    + ". If this mentions a truncated column, run the database/migrations/01-widen-enum-columns.sql migration and try again.",
                500);
        }
        mirrorPasswordToUserService(dir.getId(), raw);
        activateDirectoryUserForLogin(dir.getId());
        log.info("Admin enabled directory login for id={} email={}", dir.getId(), email);
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    private Optional<DirectoryUserSnapshot> fetchDirectoryUserSnapshot(String normalizedEmail) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            return Optional.empty();
        }
        try {
            var uri = UriComponentsBuilder.fromHttpUrl(userServiceBaseUrl + "/api/users/internal/by-email")
                .queryParam("email", normalizedEmail)
                .build(true)
                .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalApiSecret);
            ResponseEntity<DirectoryUserSnapshot> resp = restTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(headers), DirectoryUserSnapshot.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return Optional.of(resp.getBody());
            }
        } catch (RestClientException ex) {
            log.debug("Directory lookup for email {} failed: {}", normalizedEmail, ex.getMessage());
        }
        return Optional.empty();
    }

    private void insertAuthUserFromDirectoryNative(DirectoryUserSnapshot dir, String emailNormalized, String encodedPassword) {
        User.UserRole role;
        String roleStr = dir.getRole();
        if (roleStr == null || roleStr.isBlank()) {
            role = User.UserRole.EMPLOYEE;
        } else {
            try {
                role = User.UserRole.valueOf(roleStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                role = User.UserRole.EMPLOYEE;
            }
        }
        /*
         * Signing in is explicitly enabled by an admin: store ACTIVE + active=true in auth_db.
         * Directory rows may still be PENDING/SUSPENDED/inconsistent until user-service activation runs.
         */
        User.UserStatus status = User.UserStatus.ACTIVE;
        boolean active = true;

        String fn = dir.getFirstName() != null ? dir.getFirstName() : "";
        String ln = dir.getLastName() != null ? dir.getLastName() : "";
        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);

        entityManager.createNativeQuery(
            "INSERT INTO users (id, email, first_name, last_name, password, role, status, department, phone, active, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .setParameter(1, dir.getId())
            .setParameter(2, emailNormalized)
            .setParameter(3, fn)
            .setParameter(4, ln)
            .setParameter(5, encodedPassword)
            .setParameter(6, role.name())
            .setParameter(7, status.name())
            .setParameter(8, dir.getDepartment())
            .setParameter(9, dir.getPhone())
            .setParameter(10, active)
            .setParameter(11, ts)
            .setParameter(12, ts)
            .executeUpdate();
    }

    /**
     * Align user_db to ACTIVE and push the same flags to auth via account-sync (idempotent).
     */
    private void activateDirectoryUserForLogin(Long userId) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalApiSecret);
            restTemplate.exchange(
                userServiceBaseUrl + "/api/users/internal/activate-for-enabled-login/" + userId,
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                Void.class);
            log.info("Activated directory user id={} after enable sign-in", userId);
        } catch (RestClientException ex) {
            log.warn("Could not activate directory user id={} after enable sign-in ({}); auth_db row is still ACTIVE",
                userId, ex.getMessage());
        }
    }

    private void mirrorPasswordToUserService(Long userId, String rawPassword) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", userId);
            body.put("newPassword", rawPassword);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(
                userServiceBaseUrl + "/api/users/internal/sync-password", entity, Void.class);
            log.info("Synced new password hash to user-service for id={}", userId);
        } catch (RestClientException ex) {
            log.warn("Could not sync password to user-service for id={} ({}); auth_db is authoritative",
                userId, ex.getMessage());
        }
    }

    /**
     * Replicates the account into user-service so JWT userIds match rows in user_db (tickets, admin UI).
     */
    private void syncUserProfileToDirectory(User user, String rawPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("password", rawPassword);
        body.put("firstName", user.getFirstName());
        body.put("lastName", user.getLastName());
        body.put("role", user.getRole().toString());
        body.put("department", user.getDepartment());
        body.put("phone", user.getPhone());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(userServiceBaseUrl + "/api/users/bootstrap", entity, Void.class);
            log.info("User profile synced to user-service for id={}", user.getId());
        } catch (RestClientException ex) {
            log.warn("Could not sync user to user-service (user_db may be missing this account): {}", ex.getMessage());
        }
    }

    /**
     * Creates the bootstrap administrator when none exists (once per empty auth database).
     */
    @Transactional
    public void ensureBootstrapAdmin(
            String email,
            String rawPassword,
            String firstName,
            String lastName,
            String department) {
        if (userRepository.existsByRole(User.UserRole.ADMIN)) {
            log.debug("Bootstrap admin skipped: an administrator already exists");
            return;
        }
        String emailNorm = normalizeEmail(email);
        if (userRepository.findByEmail(emailNorm).isPresent()) {
            log.warn("Bootstrap admin skipped: email {} is already registered", emailNorm);
            return;
        }

        User user = User.builder()
            .email(emailNorm)
            .firstName(firstName)
            .lastName(lastName)
            .password(passwordEncoder.encode(rawPassword))
            .role(User.UserRole.ADMIN)
            .status(User.UserStatus.ACTIVE)
            .department(department)
            .active(true)
            .build();
        user = userRepository.save(user);
        log.info("Created default administrator account id={} email={}", user.getId(), emailNorm);
        log.warn(
            "SECURITY: Change the default administrator password after first login (email {}). "
                + "Override credentials via BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD or helpdesk.bootstrap-admin.*.",
            emailNorm);
        syncUserProfileToDirectory(user, rawPassword);
    }
}
