package com.helpdesk.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Minimal user row from user-service JSON (internal lookup). Ignores extra fields from {@code UserDTO}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DirectoryUserSnapshot {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String status;
    private String department;
    private String phone;
}
