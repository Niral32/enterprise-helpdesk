package com.helpdesk.auth.controller;

import com.helpdesk.auth.dto.AdminProvisionRequest;
import com.helpdesk.auth.dto.AdminProvisionResponse;
import com.helpdesk.auth.dto.AdminResetPasswordRequest;
import com.helpdesk.auth.dto.AuthRequest;
import com.helpdesk.auth.dto.EnableDirectoryLoginRequest;
import com.helpdesk.auth.dto.AuthResponse;
import com.helpdesk.auth.dto.RegisterRequest;
import com.helpdesk.auth.dto.TokenRefreshRequest;
import com.helpdesk.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * Handles user registration, login, and token refresh operations
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User authentication endpoints")
public class AuthController {
    // CORS is handled centrally in SecurityConfig (allowed origins are explicit
    // there). Avoid @CrossOrigin(origins = "*") because it conflicts with
    // allowCredentials=true and creates unnecessary preflight churn.

    private final AuthService authService;

    /**
     * Register new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    @Operation(summary = "Register new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request for email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Login user
     * POST /api/auth/login
     */
    @PostMapping("/login")
    @Operation(summary = "Login user")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh JWT token
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        log.info("Token refresh request");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate JWT token
     * GET /api/auth/validate
     */
    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token")
    public ResponseEntity<Boolean> validateToken(@RequestParam String token) {
        boolean isValid = authService.validateToken(token);
        return ResponseEntity.ok(isValid);
    }

    /**
     * Health check endpoint
     * GET /api/auth/health
     */
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth service is running");
    }

    /**
     * Admin-driven password reset for another user.
     * PATCH /api/auth/admin/reset-password/{userId}
     *
     * Requires X-User-Role=ADMIN (forwarded by the gateway). The service layer
     * also re-validates the role; controller header check is fast-fail only.
     */
    @PatchMapping("/admin/reset-password/{userId}")
    @Operation(summary = "Admin reset password for another user",
            description = "Encodes the new password, saves in auth_db, and syncs to user_db.")
    public ResponseEntity<Void> adminResetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody AdminResetPasswordRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        log.info("Admin reset-password requested for userId={} by role={}", userId, requesterRole);
        authService.adminResetPassword(userId, request.getNewPassword(), requesterRole);
        return ResponseEntity.noContent().build();
    }

    /**
     * Admin creates login-capable user (same id in auth_db and user_db).
     * POST /api/auth/admin/users
     */
    @PostMapping("/admin/users")
    @Operation(summary = "Admin: provision user with credentials",
            description = "Requires X-User-Role=ADMIN. Creates auth row and bootstraps user-service.")
    public ResponseEntity<AdminProvisionResponse> adminCreateUser(
            @Valid @RequestBody AdminProvisionRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        AdminProvisionResponse created = authService.adminProvisionUser(request, requesterRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * For users who exist in user_db only (no auth row): create matching auth credentials with the same user id.
     */
    @PostMapping("/admin/enable-directory-login")
    @Operation(summary = "Admin: enable sign-in for existing directory user",
            description = "Requires X-User-Role=ADMIN. Sets initial password and syncs hash to user-service.")
    public ResponseEntity<Void> adminEnableDirectoryLogin(
            @Valid @RequestBody EnableDirectoryLoginRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        authService.adminEnableDirectoryLogin(request, requesterRole);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
