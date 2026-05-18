package com.helpdesk.user.controller;

import com.helpdesk.user.dto.ChangeRoleRequest;
import com.helpdesk.user.dto.SyncPasswordRequest;
import com.helpdesk.user.dto.UserDTO;
import com.helpdesk.user.exception.UserNotFoundException;
import com.helpdesk.user.service.UserService;
import com.helpdesk.user.storage.AvatarStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User Controller - REST API endpoints for user management.
 * Static path segments ({@code /me}, {@code /search}, …) are declared before {@code /{id}} so they are not captured as ids.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

    private final UserService userService;
    private final AvatarStorageService avatarStorage;

    @Value("${helpdesk.internal-api.secret:}")
    private String internalApiSecret;

    /**
     * Internal bootstrap from auth-service (same network); not routed through the gateway from browsers.
     */
    @PostMapping("/bootstrap")
    @Operation(summary = "Bootstrap user after auth registration", description = "Creates user row with the same id as auth_db")
    public ResponseEntity<UserDTO> bootstrapFromAuth(@RequestBody UserDTO userDTO) {
        UserDTO created = userService.bootstrapFromAuth(userDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Legacy create in user_db only — does NOT add auth_db credentials (users cannot log in).
     * Prefer {@code POST /api/auth/admin/users} for staff accounts. Restricted to ADMIN when
     * the request comes through the gateway with {@code X-User-Role}.
     */
    @PostMapping
    @Operation(summary = "Create a new user (directory only)", description = "ADMIN only via gateway; does not create login credentials — use auth-service admin provision for accounts that must sign in.")
    public ResponseEntity<UserDTO> createUser(
            @RequestBody UserDTO userDTO,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        log.info("REST request to create user");
        if (requesterRole == null || !"ADMIN".equalsIgnoreCase(requesterRole.trim())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UserDTO createdUser = userService.createUser(userDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieve all users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        log.info("REST request to get all users");
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user profile", description = "Uses X-User-Id from API Gateway")
    public ResponseEntity<UserDTO> getCurrentUser(@RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long id = Long.parseLong(userIdHeader);
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Uses X-User-Id from API Gateway")
    public ResponseEntity<UserDTO> updateCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestBody UserDTO userDTO) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long id = Long.parseLong(userIdHeader);
        UserDTO updated = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/search")
    @Operation(summary = "Search users", description = "Search users by name or email")
    public ResponseEntity<List<UserDTO>> searchUsers(@RequestParam String query) {
        log.info("REST request to search users with query: {}", query);
        List<UserDTO> users = userService.searchUsers(query);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active users", description = "Retrieve all active users")
    public ResponseEntity<List<UserDTO>> getActiveUsers() {
        log.info("REST request to get active users");
        List<UserDTO> users = userService.getActiveUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Get user by email", description = "Retrieve a user by email")
    public ResponseEntity<UserDTO> getUserByEmail(@PathVariable String email) {
        log.info("REST request to get user with email: {}", email);
        UserDTO user = userService.getUserByEmail(email);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/role/{role}")
    @Operation(summary = "Get users by role", description = "Retrieve users filtered by role")
    public ResponseEntity<List<UserDTO>> getUsersByRole(@PathVariable String role) {
        log.info("REST request to get users by role: {}", role);
        List<UserDTO> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/department/{department}")
    @Operation(summary = "Get users by department", description = "Retrieve users filtered by department")
    public ResponseEntity<List<UserDTO>> getUsersByDepartment(@PathVariable String department) {
        log.info("REST request to get users from department: {}", department);
        List<UserDTO> users = userService.getUsersByDepartment(department);
        return ResponseEntity.ok(users);
    }

    /**
     * Server-to-server user directory lookup (e.g. ticket-service DTO enrichment).
     * Not intended for browsers; do not route through the public gateway without protection.
     * Requires {@code X-Internal-Secret} matching {@code helpdesk.internal-api.secret}.
     */
    @GetMapping("/internal/{id}")
    @Operation(summary = "(internal) Get user by id for enrichment", description = "Service-to-service only")
    public ResponseEntity<UserDTO> getUserForInternalEnrichment(
            @PathVariable Long id,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            log.warn("internal user lookup rejected: helpdesk.internal-api.secret is not configured");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (secret == null || !internalApiSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Auth-service uses this to detect “directory exists but no auth row” and to provision login.
     */
    @GetMapping("/internal/by-email")
    @Operation(summary = "(internal) Get user by email", description = "Service-to-service; requires X-Internal-Secret")
    public ResponseEntity<UserDTO> getUserInternalByEmail(
            @RequestParam String email,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (secret == null || !internalApiSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(userService.getUserByEmail(email));
        } catch (UserNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID",
            description = "EMPLOYEE: own id only. TECHNICIAN/ADMIN: any id (directory lookups).")
    public ResponseEntity<UserDTO> getUserById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        log.info("REST request to get user with ID: {}", id);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long requesterId = Long.parseLong(userIdHeader.trim());
        UserDTO user = userService.getUserByIdForCaller(id, requesterId, roleHeader);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user (ADMIN only via gateway)",
            description = "Updates firstName, lastName, department, phone, role, status. "
                    + "Email changes go through PATCH /{id}/email so they can be synced to auth-service.")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @RequestBody UserDTO userDTO,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        log.info("REST request to update user with ID: {}", id);
        if (requesterRole == null || !"ADMIN".equalsIgnoreCase(requesterRole.trim())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UserDTO updatedUser = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/{id}/email")
    @Operation(summary = "Admin: change a user's email",
            description = "Validates uniqueness in user_db and syncs the new email to auth_db. Body: {\"email\": \"...\"}.")
    public ResponseEntity<UserDTO> changeEmail(
            @PathVariable Long id,
            @RequestBody com.helpdesk.user.dto.ChangeEmailRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        return ResponseEntity.ok(userService.changeEmail(id, body.getEmail(), requesterRole));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user (ADMIN only via gateway)")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        log.info("REST request to delete user with ID: {}", id);
        if (requesterRole == null || !"ADMIN".equalsIgnoreCase(requesterRole.trim())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------
    // Admin user-management endpoints (HESK Priority 2).
    // All require X-User-Role = ADMIN; the gateway forwards that header
    // from the JWT. Service layer also re-validates as defense-in-depth.
    // ----------------------------------------------------------------

    @PostMapping("/admin")
    @Operation(summary = "Admin: create user (USER/TECHNICIAN/ADMIN)",
            description = "Allows ADMIN to create technicians or end-users. Requires X-User-Role=ADMIN.")
    public ResponseEntity<UserDTO> adminCreateUser(
            @RequestBody UserDTO userDTO,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        log.info("Admin-create-user request");
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.adminCreateUser(userDTO, role));
    }

    @PatchMapping("/{id}/block")
    @Operation(summary = "Admin: block (suspend) a user")
    public ResponseEntity<UserDTO> blockUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(userService.blockUser(id, role));
    }

    @PatchMapping("/{id}/unblock")
    @Operation(summary = "Admin: unblock a previously suspended user")
    public ResponseEntity<UserDTO> unblockUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(userService.unblockUser(id, role));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Admin: deactivate (soft-delete) a user")
    public ResponseEntity<UserDTO> deactivateUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(userService.deactivateUser(id, role));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Admin: change a user's role",
            description = "Body: {\"role\": \"TECHNICIAN\"}. Cannot demote the last active admin.")
    public ResponseEntity<UserDTO> changeRole(
            @PathVariable Long id,
            @RequestBody ChangeRoleRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(userService.changeRole(id, body.getRole(), role));
    }

    /**
     * Internal-only — called by auth-service after an admin password reset
     * to mirror the new hash into user_db. Not exposed via the gateway.
     */
    @PostMapping("/internal/sync-password")
    @Operation(summary = "(internal) Sync password hash from auth-service",
            description = "Server-to-server only; no role check. Should not be reachable through the gateway.")
    public ResponseEntity<UserDTO> syncPassword(@RequestBody SyncPasswordRequest body) {
        return ResponseEntity.ok(userService.syncPasswordHash(body.getUserId(), body.getNewPassword()));
    }

    /**
     * Auth-service: after creating auth credentials (enable sign-in), align directory + auth lifecycle.
     */
    @PatchMapping("/internal/activate-for-enabled-login/{id}")
    @Operation(summary = "(internal) Activate user for directory login", hidden = true)
    public ResponseEntity<Void> activateForEnabledLogin(
            @PathVariable Long id,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (secret == null || !internalApiSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.activateForEnabledLoginInternal(id);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------
    //  Avatar / profile photo endpoints (Bug 4)
    // ----------------------------------------------------------------

    /**
     * Upload the current user's avatar. Replaces any previous photo.
     * Path is /me/photo so users always change their OWN photo — admins
     * can use POST /{id}/photo if/when we expose it.
     */
    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload my profile photo",
            description = "JPG/PNG/GIF/WEBP, max 5 MB. Replaces any previous photo.")
    public ResponseEntity<UserDTO> uploadMyPhoto(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long uid = Long.parseLong(userIdHeader);
        String url = avatarStorage.save(uid, file);
        UserDTO updated = userService.setProfileImage(uid, url);
        return ResponseEntity.ok(updated);
    }

    /** Remove the current user's avatar. */
    @DeleteMapping("/me/photo")
    @Operation(summary = "Remove my profile photo")
    public ResponseEntity<UserDTO> deleteMyPhoto(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long uid = Long.parseLong(userIdHeader);
        UserDTO cur = userService.getUserById(uid);
        if (cur.getProfileImageUrl() != null) {
            avatarStorage.deleteByUrl(cur.getProfileImageUrl());
        }
        UserDTO updated = userService.setProfileImage(uid, null);
        return ResponseEntity.ok(updated);
    }

    /**
     * Serve an avatar by filename. Public-by-token (gateway already
     * enforces JWT). Returns 404 if file missing.
     */
    @GetMapping("/avatars/{filename:.+}")
    @Operation(summary = "Fetch an uploaded avatar by filename")
    public ResponseEntity<Resource> serveAvatar(@PathVariable String filename) {
        Resource res = avatarStorage.load(filename);
        if (res == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType ct = guessMediaType(filename);
        return ResponseEntity.ok()
            .contentType(ct)
            .header("Cache-Control", "private, max-age=3600")
            .body(res);
    }

    private static MediaType guessMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
