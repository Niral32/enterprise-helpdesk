package com.helpdesk.auth.dto;

import com.helpdesk.auth.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User registration request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @Email(message = "Email should be valid")
    @NotBlank(message = "Email cannot be blank")
    private String email;

    @NotBlank(message = "First name cannot be blank")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name cannot be blank")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    /** Ignored for security — public registration always creates EMPLOYEE (end user). */
    private String role;

    private String department;
    private String phone;

    /**
     * Convert RegisterRequest to User entity (role field is only used before AuthService forces EMPLOYEE).
     */
    public User toUser() {
        User.UserRole resolved = User.UserRole.EMPLOYEE;
        if (role != null && !role.isBlank()) {
            try {
                resolved = User.UserRole.valueOf(role.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                resolved = User.UserRole.EMPLOYEE;
            }
        }
        return User.builder()
            .email(this.email)
            .firstName(this.firstName)
            .lastName(this.lastName)
            .department(this.department)
            .phone(this.phone)
            .role(resolved)
            .status(User.UserStatus.ACTIVE)
            .active(true)
            .build();
    }
}
