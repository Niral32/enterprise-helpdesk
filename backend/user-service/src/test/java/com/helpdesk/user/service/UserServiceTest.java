package com.helpdesk.user.service;

import com.helpdesk.user.dto.UserDTO;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.exception.UserNotFoundException;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService unit tests. Focus areas:
 *   - Admin-only operations correctly reject non-admin callers
 *   - The "last active admin" invariant is enforced on block/deactivate/role
 *   - updateUser is patch-style (null fields skipped)
 *   - changeEmail validates uniqueness and rolls back on auth sync failure
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RestTemplate restTemplate;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, restTemplate);
        ReflectionTestUtils.setField(userService, "authServiceBaseUrl", "http://auth-service:8001");
        // Empty secret → auth-sync calls are skipped, which is what we want
        // for most unit tests. The few that exercise the sync path set it
        // explicitly inside the test.
        ReflectionTestUtils.setField(userService, "internalApiSecret", "");

        // Default lenient encoder stub so we don't have to repeat it.
        lenient().when(passwordEncoder.encode(any())).thenReturn("HASH");
    }

    // ────────────────────────────────────────────────────────────────────
    //  updateUser — patch semantics
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateUser: null fields in the DTO are left untouched")
    void updateUser_nullFieldsIgnored() {
        User existing = user(1L, "x@y.com", User.UserRole.EMPLOYEE);
        existing.setFirstName("Original");
        existing.setDepartment("Sales");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO patch = new UserDTO();
        patch.setPhone("555-1234"); // only field provided

        UserDTO out = userService.updateUser(1L, patch);

        assertThat(out.getFirstName()).isEqualTo("Original"); // untouched
        assertThat(out.getDepartment()).isEqualTo("Sales");    // untouched
        assertThat(out.getPhone()).isEqualTo("555-1234");      // updated
    }

    @Test
    @DisplayName("updateUser: rejects promoting a non-admin to ADMIN when one already exists")
    void updateUser_blocksSecondAdmin() {
        User existing = user(1L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByRole(User.UserRole.ADMIN)).thenReturn(true);

        UserDTO patch = new UserDTO();
        patch.setRole("ADMIN");

        assertThatThrownBy(() -> userService.updateUser(1L, patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("administrator");
    }

    @Test
    @DisplayName("updateUser: refuses to demote an ADMIN to anything else")
    void updateUser_blocksAdminDemotion() {
        User existing = user(1L, "admin@x.com", User.UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        UserDTO patch = new UserDTO();
        patch.setRole("EMPLOYEE");

        assertThatThrownBy(() -> userService.updateUser(1L, patch))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  changeRole — last-admin guard
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("changeRole: requires admin requester")
    void changeRole_rejectsNonAdmin() {
        assertThatThrownBy(() -> userService.changeRole(1L, "TECHNICIAN", "EMPLOYEE"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("changeRole: cannot demote the only active admin")
    void changeRole_blocksLastAdminDemotion() {
        User onlyAdmin = user(1L, "admin@x.com", User.UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(onlyAdmin));
        when(userRepository.countByRoleAndStatus(User.UserRole.ADMIN, User.UserStatus.ACTIVE))
            .thenReturn(1L);

        assertThatThrownBy(() -> userService.changeRole(1L, "EMPLOYEE", "ADMIN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("only active administrator");
    }

    @Test
    @DisplayName("changeRole: allows demotion when another active admin exists")
    void changeRole_demotionOkWhenOtherAdminExists() {
        User firstAdmin = user(1L, "a@x.com", User.UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(firstAdmin));
        when(userRepository.countByRoleAndStatus(User.UserRole.ADMIN, User.UserStatus.ACTIVE))
            .thenReturn(2L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO out = userService.changeRole(1L, "TECHNICIAN", "ADMIN");

        assertThat(out.getRole()).isEqualTo("TECHNICIAN");
    }

    @Test
    @DisplayName("changeRole: cannot promote to ADMIN when one already exists")
    void changeRole_blocksSecondAdmin() {
        User emp = user(1L, "e@x.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(userRepository.existsByRole(User.UserRole.ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> userService.changeRole(1L, "ADMIN", "ADMIN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only one administrator");
    }

    // ────────────────────────────────────────────────────────────────────
    //  blockUser / unblockUser / deactivateUser
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("blockUser: sets status to SUSPENDED")
    void blockUser_setsSuspended() {
        User u = user(2L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO out = userService.blockUser(2L, "ADMIN");

        assertThat(out.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("blockUser: cannot block the only active admin")
    void blockUser_blocksLastAdmin() {
        User onlyAdmin = user(1L, "admin@x.com", User.UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(onlyAdmin));
        when(userRepository.countByRoleAndStatus(User.UserRole.ADMIN, User.UserStatus.ACTIVE))
            .thenReturn(1L);

        assertThatThrownBy(() -> userService.blockUser(1L, "ADMIN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("only active administrator");
    }

    @Test
    @DisplayName("unblockUser: restores ACTIVE status")
    void unblockUser_setsActive() {
        User u = user(2L, "x@y.com", User.UserRole.EMPLOYEE);
        u.setStatus(User.UserStatus.SUSPENDED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO out = userService.unblockUser(2L, "ADMIN");

        assertThat(out.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("deactivateUser: cannot deactivate the only active admin")
    void deactivateUser_blocksLastAdmin() {
        User onlyAdmin = user(1L, "admin@x.com", User.UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(onlyAdmin));
        when(userRepository.countByRoleAndStatus(User.UserRole.ADMIN, User.UserStatus.ACTIVE))
            .thenReturn(1L);

        assertThatThrownBy(() -> userService.deactivateUser(1L, "ADMIN"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  changeEmail
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("changeEmail: no-op when the new email equals the old one")
    void changeEmail_noopWhenSame() {
        User u = user(1L, "same@x.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        UserDTO out = userService.changeEmail(1L, "Same@X.com", "ADMIN");

        assertThat(out.getEmail()).isEqualTo("same@x.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeEmail: rejects an email that another user already owns")
    void changeEmail_rejectsDuplicate() {
        User target = user(1L, "old@x.com", User.UserRole.EMPLOYEE);
        User other  = user(2L, "new@x.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmail("new@x.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> userService.changeEmail(1L, "new@x.com", "ADMIN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("changeEmail: requires admin requester")
    void changeEmail_rejectsNonAdmin() {
        assertThatThrownBy(() -> userService.changeEmail(1L, "new@x.com", "EMPLOYEE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  setProfileImage
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setProfileImage: persists the URL on the user")
    void setProfileImage_persists() {
        User u = user(7L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO out = userService.setProfileImage(7L, "/api/users/avatars/x.jpg");

        assertThat(out.getProfileImageUrl()).isEqualTo("/api/users/avatars/x.jpg");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getProfileImageUrl()).isEqualTo("/api/users/avatars/x.jpg");
    }

    @Test
    @DisplayName("setProfileImage: clears the URL when passed null")
    void setProfileImage_clearsWithNull() {
        User u = user(7L, "x@y.com", User.UserRole.EMPLOYEE);
        u.setProfileImageUrl("/api/users/avatars/x.jpg");
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO out = userService.setProfileImage(7L, null);

        assertThat(out.getProfileImageUrl()).isNull();
    }

    // ────────────────────────────────────────────────────────────────────
    //  Reads
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById: throws when user is missing")
    void getUserById_missing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getUserById(99L))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("getUserByIdForCaller: EMPLOYEE cannot read someone else's profile")
    void getUserByIdForCaller_employeeBlocked() {
        assertThatThrownBy(() -> userService.getUserByIdForCaller(2L, 1L, "EMPLOYEE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("only view your own");
    }

    @Test
    @DisplayName("getUserByIdForCaller: ADMIN may read any profile")
    void getUserByIdForCaller_adminAllowed() {
        User u = user(2L, "x@y.com", User.UserRole.EMPLOYEE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(u));

        UserDTO out = userService.getUserByIdForCaller(2L, 1L, "ADMIN");

        assertThat(out.getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getAllUsers: strips password hashes from every row")
    void getAllUsers_stripsPasswords() {
        User a = user(1L, "a@x.com", User.UserRole.EMPLOYEE);
        User b = user(2L, "b@x.com", User.UserRole.TECHNICIAN);
        when(userRepository.findAll()).thenReturn(List.of(a, b));

        List<UserDTO> out = userService.getAllUsers();

        assertThat(out).hasSize(2);
        assertThat(out).allSatisfy(u -> assertThat(u.getPassword()).isNull());
    }

    // ────────────────────────────────────────────────────────────────────
    //  helpers
    // ────────────────────────────────────────────────────────────────────

    private static User user(Long id, String email, User.UserRole role) {
        return User.builder()
            .id(id)
            .email(email)
            .firstName("First")
            .lastName("Last")
            .password("HASH")
            .role(role)
            .status(User.UserStatus.ACTIVE)
            .build();
    }
}
