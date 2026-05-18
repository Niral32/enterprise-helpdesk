package com.helpdesk.auth.dto;

import com.helpdesk.auth.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login response DTO - returned after successful authentication
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String refreshToken;
    private String type = "Bearer";
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String profileImageUrl;
    private Long expiresIn;

    public static AuthResponse fromUser(User user, String token, String refreshToken, Long expiresIn) {
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setRefreshToken(refreshToken);
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setRole(user.getRole().toString());
        response.setProfileImageUrl(user.getProfileImageUrl());
        response.setExpiresIn(expiresIn);
        return response;
    }
}
