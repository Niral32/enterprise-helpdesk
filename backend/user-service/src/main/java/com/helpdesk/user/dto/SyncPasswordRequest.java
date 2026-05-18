package com.helpdesk.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code POST /api/users/internal/sync-password}.
 * Sent by auth-service when an admin resets a user's password, so the
 * duplicated hash in user_db stays consistent with auth_db.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncPasswordRequest {
    private Long userId;
    /** Raw password — user-service encodes it. */
    private String newPassword;
}
