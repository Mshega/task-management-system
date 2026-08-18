package com.viwe.task_management_system.dto.response;

import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;

import java.time.LocalDateTime;

/**
 * Response body for user profile endpoints (e.g. GET /api/users/me).
 *
 * <p>Exposes only the fields that are safe to return to the client.
 * The {@code password} field is deliberately excluded.
 *
 * <p>Provides a static factory method {@link #from(User)} to map from the
 * entity without coupling controllers or services to the entity type.
 *
 * @param id        the unique user identifier
 * @param firstName the user's first name
 * @param lastName  the user's last name
 * @param email     the user's email address (also used as the login username)
 * @param role      the user's assigned role
 * @param createdAt when the account was created
 */
public record UserResponse(

        Long id,
        String firstName,
        String lastName,
        String email,
        Role role,
        LocalDateTime createdAt

) {

    /**
     * Maps a {@link User} entity to a {@link UserResponse}.
     *
     * <p>This is the only place the mapping logic lives, keeping controllers
     * and services free from entity-to-DTO conversion boilerplate.
     *
     * @param user the entity to map from
     * @return the corresponding response DTO
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
