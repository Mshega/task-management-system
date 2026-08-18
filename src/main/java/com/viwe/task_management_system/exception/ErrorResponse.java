package com.viwe.task_management_system.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Uniform JSON error envelope returned by the API for every error scenario.
 *
 * <p>All error responses share this shape, so clients need only one deserialization
 * model regardless of which error occurred.
 *
 * <p>Example for a validation failure:
 * <pre>{@code
 * {
 *   "timestamp": "2026-08-17T10:15:30",
 *   "status": 400,
 *   "error": "VALIDATION_FAILED",
 *   "message": "Request validation failed",
 *   "fieldErrors": [
 *     { "field": "email", "message": "Email must be a valid email address" }
 *   ]
 * }
 * }</pre>
 *
 * <p>The {@code fieldErrors} list is only present for validation failures
 * ({@link JsonInclude.Include#NON_NULL} suppresses it for all other error types).
 *
 * <p>Stack traces and database implementation details are never included.
 *
 * @param timestamp   when the error occurred (server time)
 * @param status      the HTTP status code (e.g. 400, 404, 500)
 * @param error       a stable {@link ErrorCode} identifier (e.g. "RESOURCE_NOT_FOUND")
 * @param message     a human-readable description of what went wrong
 * @param fieldErrors per-field validation messages; {@code null} for non-validation errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        List<FieldError> fieldErrors

) {

    /**
     * Factory method for general errors (no field-level detail).
     */
    public static ErrorResponse of(int status, ErrorCode error, String message) {
        return new ErrorResponse(LocalDateTime.now(), status, error.name(), message, null);
    }

    /**
     * Factory method for validation errors that carry per-field detail.
     */
    public static ErrorResponse ofValidation(int status, ErrorCode error,
                                              String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(LocalDateTime.now(), status, error.name(), message, fieldErrors);
    }

    // -------------------------------------------------------------------------

    /**
     * A single field-level validation failure.
     *
     * @param field   the name of the request field that failed validation
     * @param message the constraint violation message for that field
     */
    public record FieldError(String field, String message) {}
}
