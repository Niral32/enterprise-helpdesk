package com.helpdesk.auth.repository;

import com.helpdesk.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User repository for database operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);

    /**
     * Case-insensitive lookup — helps legacy rows created before emails were normalized to lowercase.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Find active users by email
     */
    Optional<User> findByEmailAndActive(String email, Boolean active);

    boolean existsByRole(User.UserRole role);
}
