package com.helpdesk.user.service;

import com.helpdesk.user.dto.UserDTO;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.exception.UserNotFoundException;
import com.helpdesk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * User Service - Business logic for user management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;

    @Value("${helpdesk.auth-service.base-url:http://localhost:8001}")
    private String authServiceBaseUrl;

    @Value("${helpdesk.internal-api.secret:}")
    private String internalApiSecret;

    public UserDTO createUser(UserDTO userDTO) {
        log.info("Creating new user with email: {}", userDTO.getEmail());

        User.UserRole role = User.UserRole.valueOf(userDTO.getRole());
        if (role == User.UserRole.ADMIN) {
            throw new IllegalArgumentException(
                "Administrator accounts are provisioned automatically; additional admins cannot be created.");
        }

        User user = User.builder()
            .email(userDTO.getEmail())
            .firstName(userDTO.getFirstName())
            .lastName(userDTO.getLastName())
            .password(passwordEncoder.encode(userDTO.getPassword()))
            .role(User.UserRole.valueOf(userDTO.getRole()))
            .department(userDTO.getDepartment())
            .phone(userDTO.getPhone())
            .status(User.UserStatus.ACTIVE)
            .build();

        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());

        return UserDTO.fromEntity(savedUser);
    }

    /**
     * Called by auth-service after registration so user_db shares the same numeric id as auth_db (JWT userId).
     */
    public UserDTO bootstrapFromAuth(UserDTO userDTO) {
        log.info("Bootstrap user profile from auth for id={}, email={}", userDTO.getId(), userDTO.getEmail());

        if (userDTO.getId() == null || userDTO.getEmail() == null || userDTO.getPassword() == null) {
            throw new IllegalArgumentException("bootstrap requires id, email, and password");
        }

        if (userRepository.existsById(userDTO.getId())) {
            return UserDTO.fromEntity(userRepository.findById(userDTO.getId()).orElseThrow());
        }

        Optional<User> existingEmail = userRepository.findByEmail(userDTO.getEmail());
        if (existingEmail.isPresent()) {
            User u = existingEmail.get();
            if (u.getId().equals(userDTO.getId())) {
                return UserDTO.fromEntity(u);
            }
            throw new IllegalStateException("Email already exists under another user id");
        }

        User user = User.builder()
            .id(userDTO.getId())
            .email(userDTO.getEmail())
            .firstName(userDTO.getFirstName())
            .lastName(userDTO.getLastName())
            .password(passwordEncoder.encode(userDTO.getPassword()))
            .role(User.UserRole.valueOf(userDTO.getRole()))
            .department(userDTO.getDepartment())
            .phone(userDTO.getPhone())
            .status(User.UserStatus.ACTIVE)
            .build();

        User saved = userRepository.save(user);
        log.info("Bootstrapped user with ID: {}", saved.getId());
        return UserDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll().stream()
            .map(UserDTO::fromEntity)
            .map(UserService::stripPassword)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        log.info("Fetching user with ID: {}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        return stripPassword(UserDTO.fromEntity(user));
    }

    /**
     * EMPLOYEE may only load their own row; ADMIN and TECHNICIAN may load any user (for ticket directory UIs).
     */
    @Transactional(readOnly = true)
    public UserDTO getUserByIdForCaller(Long id, Long requesterId, String roleHeader) {
        assertCanViewUserDirectory(id, requesterId, roleHeader);
        return getUserById(id);
    }

    private void assertCanViewUserDirectory(Long targetUserId, Long requesterId, String roleHeader) {
        if (requesterId == null) {
            throw new IllegalArgumentException("Authentication required");
        }
        if (targetUserId.equals(requesterId)) {
            return;
        }
        String r = roleHeader == null ? "" : roleHeader.trim().toUpperCase();
        if ("ADMIN".equals(r) || "TECHNICIAN".equals(r)) {
            return;
        }
        throw new IllegalArgumentException("You may only view your own user profile.");
    }

    private static UserDTO stripPassword(UserDTO dto) {
        if (dto != null) {
            dto.setPassword(null);
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        log.info("Fetching user with email: {}", email);
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized)
            .or(() -> userRepository.findByEmailIgnoreCase(normalized))
            .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return UserDTO.fromEntity(user);
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public UserDTO updateUser(Long id, UserDTO userDTO) {
        log.info("Updating user with ID: {}", id);

        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));

        if (userDTO.getRole() != null) {
            User.UserRole newRole = User.UserRole.valueOf(userDTO.getRole());
            if (newRole == User.UserRole.ADMIN && user.getRole() != User.UserRole.ADMIN
                && userRepository.existsByRole(User.UserRole.ADMIN)) {
                throw new IllegalArgumentException("Only one administrator account is allowed.");
            }
            if (user.getRole() == User.UserRole.ADMIN && newRole != User.UserRole.ADMIN) {
                throw new IllegalArgumentException("The administrator role cannot be removed.");
            }
        }

        boolean nameChanged = false;
        if (userDTO.getFirstName() != null && !userDTO.getFirstName().equals(user.getFirstName())) {
            user.setFirstName(userDTO.getFirstName());
            nameChanged = true;
        }
        if (userDTO.getLastName() != null && !userDTO.getLastName().equals(user.getLastName())) {
            user.setLastName(userDTO.getLastName());
            nameChanged = true;
        }
        if (userDTO.getDepartment() != null) user.setDepartment(userDTO.getDepartment());
        if (userDTO.getPhone() != null) user.setPhone(userDTO.getPhone());
        if (userDTO.getRole() != null) user.setRole(User.UserRole.valueOf(userDTO.getRole()));
        if (userDTO.getStatus() != null) user.setStatus(User.UserStatus.valueOf(userDTO.getStatus()));

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully with ID: {}", id);

        // Keep auth_db in sync so the login response and JWT carry the new
        // display name. Best-effort: if the call fails we still return the
        // user_db update so the page reflects it; next attempt will re-sync.
        if (nameChanged) {
            syncAuthName(id, updatedUser.getFirstName(), updatedUser.getLastName());
        }

        return UserDTO.fromEntity(updatedUser);
    }

    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);

        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        if (user.getRole() == User.UserRole.ADMIN) {
            throw new IllegalArgumentException("The administrator account cannot be deleted.");
        }

        userRepository.deleteById(id);
        log.info("User deleted successfully with ID: {}", id);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> searchUsers(String query) {
        log.info("Searching users with query: {}", query);
        return userRepository.searchUsers(query).stream()
            .map(UserDTO::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getUsersByRole(String role) {
        log.info("Fetching users with role: {}", role);
        return userRepository.findByRole(User.UserRole.valueOf(role)).stream()
            .map(UserDTO::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getUsersByDepartment(String department) {
        log.info("Fetching users from department: {}", department);
        return userRepository.findByDepartment(department).stream()
            .map(UserDTO::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getActiveUsers() {
        log.info("Fetching active users");
        return userRepository.findByStatus(User.UserStatus.ACTIVE).stream()
            .map(UserDTO::fromEntity)
            .collect(Collectors.toList());
    }

    // --------------------------------------------------------------------
    //  Admin user-management — semantic operations called by AdminUsers UI.
    //  Each method takes the requester's role string (X-User-Role from the
    //  gateway) and rejects non-admin callers. The controller is the
    //  enforcement point but we double-check here so service-to-service
    //  calls can't bypass authorization.
    // --------------------------------------------------------------------

    /** Throws if the requester is not an administrator. */
    private void requireAdmin(String requesterRole) {
        if (requesterRole == null || !"ADMIN".equalsIgnoreCase(requesterRole.trim())) {
            throw new IllegalArgumentException("Only administrators may perform this action.");
        }
    }

    /**
     * Admin-driven user creation. Unlike the public {@link #createUser},
     * this can create TECHNICIAN or ADMIN accounts (subject to the single-
     * admin invariant).
     *
     * NOTE: Spec says "only ADMIN can create another ADMIN". The platform
     * already restricts ADMIN to a single bootstrapped account; if you want
     * to allow more than one admin, remove the guard below.
     */
    public UserDTO adminCreateUser(UserDTO userDTO, String requesterRole) {
        requireAdmin(requesterRole);
        log.info("Admin-creating user: email={}, role={}", userDTO.getEmail(), userDTO.getRole());

        User.UserRole role = User.UserRole.valueOf(userDTO.getRole());
        if (role == User.UserRole.ADMIN && userRepository.existsByRole(User.UserRole.ADMIN)) {
            throw new IllegalArgumentException("Only one administrator account is allowed.");
        }

        User user = User.builder()
            .email(userDTO.getEmail())
            .firstName(userDTO.getFirstName())
            .lastName(userDTO.getLastName())
            .password(passwordEncoder.encode(userDTO.getPassword()))
            .role(role)
            .department(userDTO.getDepartment())
            .phone(userDTO.getPhone())
            .status(User.UserStatus.ACTIVE)
            .build();

        return UserDTO.fromEntity(userRepository.save(user));
    }

    /**
     * Block (suspend) a user. Cannot block the last active administrator.
     */
    public UserDTO blockUser(Long id, String requesterRole) {
        requireAdmin(requesterRole);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));

        guardLastActiveAdmin(user, "block");
        user.setStatus(User.UserStatus.SUSPENDED);
        User saved = userRepository.save(user);
        log.info("Blocked user id={} email={}", saved.getId(), saved.getEmail());
        syncAuthAccountState(saved.getId(), false, "SUSPENDED");
        return UserDTO.fromEntity(saved);
    }

    /** Restore a SUSPENDED or INACTIVE account to ACTIVE. */
    public UserDTO unblockUser(Long id, String requesterRole) {
        requireAdmin(requesterRole);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        user.setStatus(User.UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        log.info("Unblocked user id={} email={}", saved.getId(), saved.getEmail());
        syncAuthAccountState(saved.getId(), true, "ACTIVE");
        return UserDTO.fromEntity(saved);
    }

    /** Soft-deactivate (INACTIVE). User row preserved so historical tickets stay valid. */
    public UserDTO deactivateUser(Long id, String requesterRole) {
        requireAdmin(requesterRole);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));

        guardLastActiveAdmin(user, "deactivate");
        user.setStatus(User.UserStatus.INACTIVE);
        User saved = userRepository.save(user);
        log.info("Deactivated user id={} email={}", saved.getId(), saved.getEmail());
        syncAuthAccountState(saved.getId(), false, "INACTIVE");
        return UserDTO.fromEntity(saved);
    }

    /**
     * Admin-driven email change. Validates uniqueness in user_db and mirrors
     * the new email into auth_db (login uses auth_db). If the auth-service
     * sync fails, we roll back the user_db change so the two stores can't
     * diverge silently.
     */
    public UserDTO changeEmail(Long id, String newEmailRaw, String requesterRole) {
        requireAdmin(requesterRole);
        if (newEmailRaw == null || newEmailRaw.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        String newEmail = normalizeEmail(newEmailRaw);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        if (newEmail.equals(normalizeEmail(user.getEmail()))) {
            return UserDTO.fromEntity(user);
        }
        Optional<User> clash = userRepository.findByEmail(newEmail)
            .or(() -> userRepository.findByEmailIgnoreCase(newEmail));
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw new IllegalArgumentException("Email is already in use.");
        }
        String previous = user.getEmail();
        user.setEmail(newEmail);
        userRepository.save(user);
        try {
            syncAuthEmail(user.getId(), newEmail);
        } catch (RuntimeException ex) {
            // Roll back so user_db and auth_db stay aligned.
            user.setEmail(previous);
            userRepository.save(user);
            throw new IllegalStateException(
                "Email change failed when syncing to auth-service; rolled back. " + ex.getMessage());
        }
        return UserDTO.fromEntity(user);
    }

    /**
     * Change a user's role. Cannot demote the last admin and cannot promote
     * to ADMIN if one already exists.
     */
    public UserDTO changeRole(Long id, String newRoleStr, String requesterRole) {
        requireAdmin(requesterRole);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));

        User.UserRole newRole = User.UserRole.valueOf(newRoleStr);
        if (user.getRole() == User.UserRole.ADMIN && newRole != User.UserRole.ADMIN) {
            // Demoting an admin — protect the last one.
            long activeAdmins = userRepository.countByRoleAndStatus(
                    User.UserRole.ADMIN, User.UserStatus.ACTIVE);
            if (activeAdmins <= 1) {
                throw new IllegalArgumentException(
                        "Cannot demote the only active administrator.");
            }
        }
        if (newRole == User.UserRole.ADMIN
                && user.getRole() != User.UserRole.ADMIN
                && userRepository.existsByRole(User.UserRole.ADMIN)) {
            throw new IllegalArgumentException("Only one administrator account is allowed.");
        }
        user.setRole(newRole);
        User saved = userRepository.save(user);
        log.info("Changed role for user id={} → {}", saved.getId(), newRole);
        // Push to auth_db so the next JWT carries the new role. Existing
        // tokens keep the stale role until they expire / refresh.
        syncAuthRole(saved.getId(), newRole.name());
        return UserDTO.fromEntity(saved);
    }

    /**
     * Internal endpoint — called by auth-service after an admin-driven password
     * reset to keep the user_db copy of the password hash in sync.
     *
     * No role check: this is server-to-server only and not exposed via the
     * gateway. The {@code rawPassword} is encoded here.
     */
    public UserDTO syncPasswordHash(Long id, String rawPassword) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        user.setPassword(passwordEncoder.encode(rawPassword));
        log.info("Synced new password hash for user id={}", user.getId());
        return UserDTO.fromEntity(userRepository.save(user));
    }

    /**
     * Called by auth-service when an admin uses "Enable sign-in" so the directory row
     * is ACTIVE (matches admin intent) and auth_db stays in sync.
     */
    @Transactional
    public void activateForEnabledLoginInternal(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
        log.info("Activate directory user id={} for enabled login (previous status={})", userId, user.getStatus());
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);
        syncAuthAccountState(user.getId(), true, "ACTIVE");
    }

    /**
     * Throws if the operation would leave zero ACTIVE administrators.
     * Operation name is just used for the error message.
     */
    private void guardLastActiveAdmin(User user, String operation) {
        if (user.getRole() != User.UserRole.ADMIN) return;
        long activeAdmins = userRepository.countByRoleAndStatus(
                User.UserRole.ADMIN, User.UserStatus.ACTIVE);
        if (user.getStatus() == User.UserStatus.ACTIVE && activeAdmins <= 1) {
            throw new IllegalArgumentException(
                "Cannot " + operation + " the only active administrator.");
        }
    }

    /**
     * Save (or clear) the profile image URL for the given user. Called by
     * the upload controller after the file has been written to disk. Mirrors
     * the URL into auth_db so AuthResponse can include it on next login.
     */
    @Transactional
    public UserDTO setProfileImage(Long userId, String url) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
        user.setProfileImageUrl(url);
        User saved = userRepository.save(user);
        log.info("Set profile image for userId={} url={}", userId, url);
        syncAuthProfileImage(userId, url);
        return UserDTO.fromEntity(saved);
    }

    /** Push the new role to auth_db; failures are logged but don't fail the user-side write. */
    private void syncAuthRole(Long userId, String newRoleName) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            log.debug("Skipping auth role sync: helpdesk.internal-api.secret is not set");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("role", newRoleName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", internalApiSecret);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            String url = authServiceBaseUrl.replaceAll("/$", "") + "/api/auth/internal/sync-role";
            restTemplate.postForEntity(url, entity, Void.class);
            log.info("Synced auth_db role for userId={} -> {}", userId, newRoleName);
        } catch (RestClientException ex) {
            log.warn("Could not sync auth_db role for userId={}: {}", userId, ex.getMessage());
        }
    }

    /** Push first/last name changes to auth_db (best-effort). */
    private void syncAuthName(Long userId, String firstName, String lastName) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", internalApiSecret);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            String url = authServiceBaseUrl.replaceAll("/$", "") + "/api/auth/internal/sync-name";
            restTemplate.postForEntity(url, entity, Void.class);
            log.info("Synced auth_db name for userId={}", userId);
        } catch (RestClientException ex) {
            log.warn("Could not sync auth_db name for userId={}: {}", userId, ex.getMessage());
        }
    }

    /** Push a profile-image URL change to auth_db (best-effort). */
    private void syncAuthProfileImage(Long userId, String url) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("profileImageUrl", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", internalApiSecret);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            String fullUrl = authServiceBaseUrl.replaceAll("/$", "") + "/api/auth/internal/sync-profile-image";
            restTemplate.postForEntity(fullUrl, entity, Void.class);
        } catch (RestClientException ex) {
            log.warn("Could not sync auth_db profile image for userId={}: {}", userId, ex.getMessage());
        }
    }

    /**
     * Push an email change into auth_db so the next login succeeds with the new address.
     * Throws if the call fails so the caller can roll back user_db.
     */
    private void syncAuthEmail(Long userId, String newEmail) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            throw new IllegalStateException("Internal API secret not configured; cannot sync email.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("email", newEmail);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", internalApiSecret);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String url = authServiceBaseUrl.replaceAll("/$", "") + "/api/auth/internal/sync-email";
        restTemplate.postForEntity(url, entity, Void.class);
        log.info("Synced auth_db email for userId={}", userId);
    }

    /**
     * Keep auth_db login flags aligned with user_db (same numeric user id as JWT).
     */
    private void syncAuthAccountState(Long userId, boolean active, String authStatusEnumName) {
        if (internalApiSecret == null || internalApiSecret.isBlank()) {
            log.debug("Skipping auth sync: helpdesk.internal-api.secret is not set");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("active", active);
        body.put("status", authStatusEnumName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", internalApiSecret);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            String url = authServiceBaseUrl.replaceAll("/$", "") + "/api/auth/internal/account-sync";
            restTemplate.postForEntity(url, entity, Void.class);
            log.info("Synced auth_db for userId={} active={} status={}", userId, active, authStatusEnumName);
        } catch (RestClientException ex) {
            log.warn("Could not sync auth_db for userId={}: {}", userId, ex.getMessage());
        }
    }
}
