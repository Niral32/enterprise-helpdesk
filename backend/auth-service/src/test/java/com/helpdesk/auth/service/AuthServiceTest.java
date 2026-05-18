package com.helpdesk.auth.service;

import com.helpdesk.auth.dto.AdminProvisionRequest;
import com.helpdesk.auth.dto.AdminProvisionResponse;
import com.helpdesk.auth.dto.AuthRequest;
import com.helpdesk.auth.dto.AuthResponse;
import com.helpdesk.auth.dto.InternalAccountSyncRequest;
import com.helpdesk.auth.dto.RegisterRequest;
import com.helpdesk.auth.dto.TokenRefreshRequest;
import com.helpdesk.auth.entity.User;
import com.helpdesk.auth.exception.AuthException;
import com.helpdesk.auth.exception.UserAlreadyExistsException;
import com.helpdesk.auth.repository.UserRepository;
import com.helpdesk.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService unit tests.
 *
 * Strategy:
 *   - Mock the repository, password encoder, JWT provider, and REST template.
 *     These tests are about logic, not integration; integration tests live
 *     under a `@SpringBootTest` class with Testcontainers (Day 2).
 *   - Each public method has at least one happy-path test and at least one
 *     failure-mode test. The failure modes matter more for interviewers —
 *     they show you thought about the error cases.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RestTemplate restTemplate;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(
            userRepository,
            passwordEncoder,
            jwtTokenProvider,
            restTemplate,
            "http://user-service:8002"
        );
        // Default JWT stubs used across many tests.
        lenient().when(jwtTokenProvider.generateToken(anyString(), anyString(), anyString())).thenReturn("access-token");
        lenient().when(jwtTokenProvider.generateRefreshToken(anyString(), anyString())).thenReturn("refresh-token");
        lenient().when(jwtTokenProvider.getExpirationTime()).thenReturn(900_000L);
    }

    // ────────────────────────────────────────────────────────────────────
    //  register
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: creates an EMPLOYEE even when client supplies a different role")
    void register_alwaysCreatesEmployee() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("New@Example.com");
        req.setFirstName("New");
        req.setLastName("User");
        req.setPassword("Secret@123");
        req.setRole("ADMIN"); // should be ignored

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        AuthResponse resp = authService.register(req);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("new@example.com"); // normalised
        assertThat(savedUser.getValue().getRole()).isEqualTo(User.UserRole.EMPLOYEE);
        assertThat(savedUser.getValue().getPassword()).isEqualTo("HASH");

        assertThat(resp.getToken()).isEqualTo("access-token");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("register: throws when email already exists")
    void register_duplicateEmailRejected() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup@example.com");
        req.setFirstName("X");
        req.setLastName("Y");
        req.setPassword("Secret@123");

        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
            .isInstanceOf(UserAlreadyExistsException.class)
            .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────
    //  login
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: returns tokens for valid credentials on an ACTIVE account")
    void login_happyPath() {
        AuthRequest req = new AuthRequest();
        req.setEmail("Foo@Example.com");
        req.setPassword("Secret@123");

        User u = activeUser(7L, "foo@example.com", User.UserRole.EMPLOYEE);
        when(userRepository.findByEmail("foo@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("Secret@123", "HASH")).thenReturn(true);

        AuthResponse resp = authService.login(req);

        assertThat(resp.getEmail()).isEqualTo("foo@example.com");
        assertThat(resp.getRole()).isEqualTo("EMPLOYEE");
        assertThat(resp.getToken()).isNotBlank();
    }

    @Test
    @DisplayName("login: throws on wrong password")
    void login_wrongPassword() {
        AuthRequest req = new AuthRequest();
        req.setEmail("foo@example.com");
        req.setPassword("Wrong");

        User u = activeUser(7L, "foo@example.com", User.UserRole.EMPLOYEE);
        when(userRepository.findByEmail("foo@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("Wrong", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("login: SUSPENDED account is rejected with 403 even with correct password")
    void login_suspendedAccountRejected() {
        AuthRequest req = new AuthRequest();
        req.setEmail("blocked@example.com");
        req.setPassword("Secret@123");

        User u = activeUser(7L, "blocked@example.com", User.UserRole.EMPLOYEE);
        u.setStatus(User.UserStatus.SUSPENDED);
        u.setActive(false);
        when(userRepository.findByEmail("blocked@example.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    @DisplayName("login: unknown email returns the generic 'invalid' message (no info leak)")
    void login_unknownEmail() {
        AuthRequest req = new AuthRequest();
        req.setEmail("nobody@example.com");
        req.setPassword("Secret@123");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("Invalid email or password");
    }

    // ────────────────────────────────────────────────────────────────────
    //  refreshToken
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refreshToken: issues new pair when refresh token is valid")
    void refreshToken_happyPath() {
        TokenRefreshRequest req = new TokenRefreshRequest();
        req.setRefreshToken("good-refresh");

        when(jwtTokenProvider.validateToken("good-refresh")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("good-refresh")).thenReturn("foo@example.com");
        when(userRepository.findByEmail("foo@example.com"))
            .thenReturn(Optional.of(activeUser(7L, "foo@example.com", User.UserRole.EMPLOYEE)));

        AuthResponse resp = authService.refreshToken(req);

        assertThat(resp.getToken()).isEqualTo("access-token");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("refreshToken: invalid refresh token throws")
    void refreshToken_invalidToken() {
        TokenRefreshRequest req = new TokenRefreshRequest();
        req.setRefreshToken("bad");
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(req))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("Invalid or expired");
    }

    // ────────────────────────────────────────────────────────────────────
    //  adminProvisionUser
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("adminProvisionUser: rejects non-admin requester")
    void adminProvision_rejectsNonAdmin() {
        AdminProvisionRequest req = provisionReq("a@b.com", "ADMIN");

        assertThatThrownBy(() -> authService.adminProvisionUser(req, "EMPLOYEE"))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("administrators");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("adminProvisionUser: creates a TECHNICIAN with the supplied role")
    void adminProvision_createsTechnician() {
        AdminProvisionRequest req = provisionReq("tech@b.com", "TECHNICIAN");
        when(userRepository.existsByEmail("tech@b.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });

        AdminProvisionResponse resp = authService.adminProvisionUser(req, "ADMIN");

        assertThat(resp.getRole()).isEqualTo("TECHNICIAN");
        assertThat(resp.getUserId()).isEqualTo(99L);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(User.UserRole.TECHNICIAN);
        assertThat(saved.getValue().getStatus()).isEqualTo(User.UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("adminProvisionUser: refuses duplicate email")
    void adminProvision_duplicateEmail() {
        AdminProvisionRequest req = provisionReq("dup@b.com", "EMPLOYEE");
        when(userRepository.existsByEmail("dup@b.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.adminProvisionUser(req, "ADMIN"))
            .isInstanceOf(UserAlreadyExistsException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  adminResetPassword
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("adminResetPassword: rejects non-admin requester")
    void adminResetPassword_rejectsNonAdmin() {
        assertThatThrownBy(() -> authService.adminResetPassword(1L, "X", "EMPLOYEE"))
            .isInstanceOf(AuthException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("adminResetPassword: encodes and persists the new hash")
    void adminResetPassword_happy() {
        User u = activeUser(5L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("NewPass1@")).thenReturn("NEW-HASH");

        authService.adminResetPassword(5L, "NewPass1@", "ADMIN");

        assertThat(u.getPassword()).isEqualTo("NEW-HASH");
        verify(userRepository).save(u);
    }

    // ────────────────────────────────────────────────────────────────────
    //  applyAccountSync / applyRoleSync / applyEmailSync / applyNameSync
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("applyAccountSync: updates active flag and status")
    void applyAccountSync_updatesFlags() {
        User u = activeUser(5L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));

        InternalAccountSyncRequest body = new InternalAccountSyncRequest();
        body.setUserId(5L);
        body.setActive(false);
        body.setStatus(User.UserStatus.SUSPENDED);
        authService.applyAccountSync(body);

        assertThat(u.getActive()).isFalse();
        assertThat(u.getStatus()).isEqualTo(User.UserStatus.SUSPENDED);
        verify(userRepository).save(u);
    }

    @Test
    @DisplayName("applyRoleSync: updates the role")
    void applyRoleSync_updatesRole() {
        User u = activeUser(5L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));

        authService.applyRoleSync(5L, "TECHNICIAN");

        assertThat(u.getRole()).isEqualTo(User.UserRole.TECHNICIAN);
        verify(userRepository).save(u);
    }

    @Test
    @DisplayName("applyRoleSync: rejects invalid role string")
    void applyRoleSync_invalidRole() {
        User u = activeUser(5L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.applyRoleSync(5L, "NOT_A_ROLE"))
            .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("applyEmailSync: rejects when new email is already in use by someone else")
    void applyEmailSync_rejectsDuplicate() {
        User target = activeUser(5L, "old@x.com", User.UserRole.EMPLOYEE);
        User other = activeUser(6L, "new@x.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmail("new@x.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> authService.applyEmailSync(5L, "new@x.com"))
            .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("applyNameSync: updates names")
    void applyNameSync_updatesNames() {
        User u = activeUser(5L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));

        authService.applyNameSync(5L, "New", "Name");

        assertThat(u.getFirstName()).isEqualTo("New");
        assertThat(u.getLastName()).isEqualTo("Name");
        verify(userRepository).save(u);
    }

    // ────────────────────────────────────────────────────────────────────
    //  helpers
    // ────────────────────────────────────────────────────────────────────

    private static User activeUser(Long id, String email, User.UserRole role) {
        return User.builder()
            .id(id)
            .email(email)
            .firstName("First")
            .lastName("Last")
            .password("HASH")
            .role(role)
            .status(User.UserStatus.ACTIVE)
            .active(true)
            .build();
    }

    private static AdminProvisionRequest provisionReq(String email, String role) {
        AdminProvisionRequest r = new AdminProvisionRequest();
        r.setEmail(email);
        r.setFirstName("First");
        r.setLastName("Last");
        r.setPassword("Pass@1234");
        r.setRole(role);
        return r;
    }
}
