package com.helpdesk.auth.dto;

import com.helpdesk.auth.entity.User;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors lifecycle from user-service (block, unblock, deactivate) into auth_db.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InternalAccountSyncRequest {

    @NotNull
    private Long userId;

    /**
     * When false, login fails via findByEmailAndActive(..., true).
     */
    @NotNull
    private Boolean active;

    @NotNull
    private User.UserStatus status;
}
