package com.helpdesk.auth.controller;

import com.helpdesk.auth.dto.InternalAccountSyncRequest;
import com.helpdesk.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server sync from user-service. Protected by a shared secret (not JWT),
 * because the gateway permits all {@code /api/auth/**} paths for browser clients.
 */
@RestController
@RequestMapping("/api/auth/internal")
@RequiredArgsConstructor
@Hidden
public class InternalAccountController {

    private final AuthService authService;

    @Value("${helpdesk.internal-api.secret:}")
    private String expectedSecret;

    @PostMapping("/account-sync")
    public ResponseEntity<Void> syncAccount(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody InternalAccountSyncRequest body) {
        if (expectedSecret == null || expectedSecret.isBlank()
                || secret == null || !expectedSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        authService.applyAccountSync(body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync-email")
    public ResponseEntity<Void> syncEmail(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody java.util.Map<String, Object> body) {
        if (expectedSecret == null || expectedSecret.isBlank()
                || secret == null || !expectedSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Object uid = body.get("userId");
        Object email = body.get("email");
        if (uid == null || email == null) {
            return ResponseEntity.badRequest().build();
        }
        authService.applyEmailSync(Long.valueOf(uid.toString()), email.toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * Mirror first/last name edits made in user-service so the next login's
     * JWT (and AuthResponse) carries the updated display name. The Header
     * component reads its name from the login response; without this sync,
     * the header still shows the old name even after a profile edit.
     */
    @PostMapping("/sync-name")
    public ResponseEntity<Void> syncName(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody java.util.Map<String, Object> body) {
        if (expectedSecret == null || expectedSecret.isBlank()
                || secret == null || !expectedSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Object uid = body.get("userId");
        if (uid == null) {
            return ResponseEntity.badRequest().build();
        }
        Object fn = body.get("firstName");
        Object ln = body.get("lastName");
        authService.applyNameSync(
            Long.valueOf(uid.toString()),
            fn == null ? null : fn.toString(),
            ln == null ? null : ln.toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * Mirror a role change made in user-service into auth_db so the next
     * JWT issued at login carries the right role. Without this, an admin
     * promoting an EMPLOYEE → TECHNICIAN would have to manually edit auth_db.
     */
    @PostMapping("/sync-role")
    public ResponseEntity<Void> syncRole(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody java.util.Map<String, Object> body) {
        if (expectedSecret == null || expectedSecret.isBlank()
                || secret == null || !expectedSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Object uid = body.get("userId");
        Object role = body.get("role");
        if (uid == null || role == null) {
            return ResponseEntity.badRequest().build();
        }
        authService.applyRoleSync(Long.valueOf(uid.toString()), role.toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * Set / clear a profile-image URL in auth_db so the login response can
     * embed it on next sign-in. The actual bytes live in user-service's
     * storage; auth-service only knows the URL.
     */
    @PostMapping("/sync-profile-image")
    public ResponseEntity<Void> syncProfileImage(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody java.util.Map<String, Object> body) {
        if (expectedSecret == null || expectedSecret.isBlank()
                || secret == null || !expectedSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Object uid = body.get("userId");
        Object url = body.get("profileImageUrl");
        if (uid == null) {
            return ResponseEntity.badRequest().build();
        }
        authService.applyProfileImageSync(Long.valueOf(uid.toString()),
                url == null ? null : url.toString());
        return ResponseEntity.noContent().build();
    }
}
