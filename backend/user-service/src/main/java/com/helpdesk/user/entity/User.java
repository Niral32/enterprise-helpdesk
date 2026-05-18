package com.helpdesk.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * User Entity - User management
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private UserRole role;

    @Column
    private String department;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private UserStatus status;

    /** Relative URL of the uploaded avatar (served by the gateway). */
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum UserRole {
        ADMIN, TECHNICIAN, EMPLOYEE
    }

    /**
     * Account lifecycle states. Aligned with auth-service so users synced
     * from auth-service can carry their full status across the boundary.
     *
     *   ACTIVE     — normal, can log in.
     *   INACTIVE   — soft-deactivated by an admin, cannot log in.
     *   SUSPENDED  — same effect as INACTIVE but signals a punitive block.
     *   PENDING    — awaiting verification or admin approval, cannot log in.
     */
    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED, PENDING
    }
}
