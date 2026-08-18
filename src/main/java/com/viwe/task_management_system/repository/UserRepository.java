package com.viwe.task_management_system.repository;

import com.viwe.task_management_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence operations for {@link User} entities.
 *
 * <p>All standard CRUD operations are inherited from {@link JpaRepository}.
 * Only methods required by the current business logic are declared here.
 *
 * <p>Business rules (e.g. duplicate email validation) are enforced in the
 * service layer, not here. These methods provide the data access primitives
 * that the service layer uses to implement those rules.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their email address.
     *
     * <p>Used by:
     * <ul>
     *   <li>The {@code UserDetailsService} implementation to load a user
     *       during Spring Security authentication.</li>
     *   <li>The auth service to retrieve a user during login.</li>
     * </ul>
     *
     * @param email the email address to search for
     * @return an {@link Optional} containing the user if found, or empty
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether a user with the given email address already exists.
     *
     * <p>Used at registration to detect duplicate accounts without loading
     * the full {@link User} entity. More efficient than
     * {@link #findByEmail(String)} when only existence needs to be verified.
     *
     * @param email the email address to check
     * @return {@code true} if a user with this email exists, {@code false} otherwise
     */
    boolean existsByEmail(String email);
}
