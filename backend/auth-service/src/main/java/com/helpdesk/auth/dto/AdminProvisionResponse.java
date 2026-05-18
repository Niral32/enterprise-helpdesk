package com.helpdesk.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminProvisionResponse {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
}
