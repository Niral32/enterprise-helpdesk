package com.helpdesk.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * User entity for authentication service
 * Stores user credentials and authentication information
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private UserStatus status;

    @Column
    private String department;

    @Column
    private String phone;

    /**
     * Optional URL (relative to the gateway) of the user's profile image.
     * Stored here so AuthResponse can include it without a roundtrip to
     * user-service on every login. Bytes live in user-service storage.
     */
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean active = true;

    /**
     * User roles in the system
     */
    public enum UserRole {
        ADMIN,           // Full system access
        TECHNICIAN,      // Can manage tickets and assets
        EMPLOYEE         // Can create tickets and request assets
    }

    /**
     * User account status
     */
    public enum UserStatus {
        ACTIVE,          // Account is active
        INACTIVE,        // Account is inactive
        SUSPENDED,       // Account is suspended
        PENDING          // Awaiting activation
    }
}
