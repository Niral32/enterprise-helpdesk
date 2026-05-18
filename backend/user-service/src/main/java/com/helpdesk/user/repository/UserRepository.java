package com.helpdesk.user.repository;

import com.helpdesk.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * User Repository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Find users by role
     */
    List<User> findByRole(User.UserRole role);

    boolean existsByRole(User.UserRole role);

    /**
     * Count users that match a role AND status — used to detect "last active
     * admin" before allowing block / deactivate / role-change to go through.
     */
    long countByRoleAndStatus(User.UserRole role, User.UserStatus status);

    /**
     * Find users by status
     */
    List<User> findByStatus(User.UserStatus status);

    /**
     * Find users by department
     */
    List<User> findByDepartment(String department);

    /**
     * Search users by name or email
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchUsers(@Param("query") String query);
}
