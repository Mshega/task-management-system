package com.viwe.task_management_system.service;

import com.viwe.task_management_system.dto.response.UserResponse;
import com.viwe.task_management_system.entity.User;

/**
 * Business operations for user management.
 *
 * <p>Authentication (register/login) will be handled by a dedicated
 * AuthService in a later step. This service covers profile operations
 * available to an already-authenticated user.
 */
public interface UserService {

    /**
     * Returns the profile of the authenticated user.
     *
     * @param userId the ID of the authenticated user
     * @return the user's profile as a response DTO
     * @throws com.viwe.task_management_system.exception.ResourceNotFoundException
     *         if no user exists with the given ID
     */
    UserResponse getUserProfile(Long userId);

    /**
     * Loads a {@link User} entity by email address.
     *
     * <p>Used internally by the Spring Security {@code UserDetailsService}
     * implementation to load the principal during authentication.
     *
     * @param email the email to search for
     * @return the matching {@link User} entity
     * @throws com.viwe.task_management_system.exception.ResourceNotFoundException
     *         if no user with the given email exists
     */
    User loadUserByEmail(String email);
}
