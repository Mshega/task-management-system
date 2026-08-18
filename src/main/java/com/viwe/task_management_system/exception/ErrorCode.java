package com.viwe.task_management_system.exception;

/**
 * Stable string identifiers for every error scenario the API can produce.
 *
 * <p>These codes are included in every {@link ErrorResponse} so that clients
 * can distinguish error types programmatically without parsing free-text
 * messages. Adding a new value is non-breaking for existing clients.
 */
public enum ErrorCode {

    // ── 400 Bad Request ──────────────────────────────────────────────────────
    /** One or more request fields failed Bean Validation constraints. */
    VALIDATION_FAILED,

    /** The request body could not be parsed (malformed JSON, wrong types). */
    MALFORMED_REQUEST,

    /** A requested status transition is not permitted by the business rules. */
    INVALID_STATUS_TRANSITION,

    /** A general business rule was violated. */
    BUSINESS_RULE_VIOLATION,

    // ── 401 Unauthorized ────────────────────────────────────────────────────
    /** No valid authentication credentials were provided. */
    UNAUTHORIZED,

    // ── 403 Forbidden ────────────────────────────────────────────────────────
    /** The authenticated user does not have permission to perform this action. */
    FORBIDDEN,

    // ── 404 Not Found ────────────────────────────────────────────────────────
    /** The requested resource does not exist or is not visible to this user. */
    RESOURCE_NOT_FOUND,

    // ── 409 Conflict ────────────────────────────────────────────────────────
    /** A resource with the given identifier already exists. */
    DUPLICATE_RESOURCE,

    // ── 500 Internal Server Error ────────────────────────────────────────────
    /** An unexpected error occurred on the server. */
    INTERNAL_SERVER_ERROR
}
