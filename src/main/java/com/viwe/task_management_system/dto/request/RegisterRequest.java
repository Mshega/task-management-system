package com.viwe.task_management_system.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/auth/register.
 *
 * <p>All fields are required. The password is accepted as plain text here
 * and will be BCrypt-hashed by the service before persisting.
 * It is never returned in any response DTO.
 *
 * @param firstName the user's first name (1–50 characters)
 * @param lastName  the user's last name (1–50 characters)
 * @param email     a valid, unique email address used as the login identifier
 * @param password  the plain-text password (min 8 characters)
 */
public record RegisterRequest(

        @NotBlank(message = "First name is required")
        @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password

) {}
