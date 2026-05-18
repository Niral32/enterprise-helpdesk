package com.helpdesk.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin enables login for a profile that already exists in user-service (same numeric id in both DBs).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnableDirectoryLoginRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank(message = "newPassword cannot be blank")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Password must contain uppercase, lowercase, digit, and special character"
    )
    private String newPassword;
}
