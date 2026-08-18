package com.viwe.task_management_system.dto.response;

/**
 * Response body for POST /api/auth/register and POST /api/auth/login.
 *
 * <p>Returns a JWT token that the client must include in the
 * {@code Authorization: Bearer <token>} header for all subsequent
 * requests to protected endpoints.
 *
 * <p>The {@code tokenType} field is always {@code "Bearer"} and is
 * included so clients do not need to hard-code the scheme.
 *
 * @param token     the signed JWT access token
 * @param tokenType always {@code "Bearer"}
 */
public record AuthResponse(

        String token,

        String tokenType

) {

    /**
     * Convenience factory method that sets {@code tokenType} to {@code "Bearer"}
     * automatically, reducing boilerplate in the service layer.
     *
     * @param token the signed JWT access token
     * @return a fully constructed {@link AuthResponse}
     */
    public static AuthResponse of(String token) {
        return new AuthResponse(token, "Bearer");
    }
}
