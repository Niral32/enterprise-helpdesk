package com.helpdesk.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code PATCH /api/users/{id}/role}.
 * Tiny single-field DTO instead of stuffing into UserDTO — clearer API + smaller payload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRoleRequest {
    /** New role: USER, TECHNICIAN, or ADMIN. (Internally USER maps to EMPLOYEE.) */
    private String role;
}
