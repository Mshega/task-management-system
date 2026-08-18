package com.viwe.task_management_system.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralised exception handler for the Task Management System REST API.
 *
 * <p>{@code @RestControllerAdvice} applies this handler globally to every
 * controller in the application. Controllers do not need any try/catch blocks
 * for these exception types — they simply throw and this class handles the
 * conversion to a consistent {@link ErrorResponse} JSON body.
 *
 * <p>Principles applied:
 * <ul>
 *   <li>No stack traces in responses — safe for production.</li>
 *   <li>No database details exposed — Hibernate/MySQL internals stay server-side.</li>
 *   <li>Unexpected errors are logged at ERROR level with the full stack trace
 *       server-side, but only a generic message is returned to the client.</li>
 *   <li>All responses share the same {@link ErrorResponse} shape.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 400 Bad Request ──────────────────────────────────────────────────────

    /**
     * Handles Bean Validation failures from {@code @Valid} on controller method
     * parameters. Collects every field-level violation into the response so the
     * client can correct all errors in a single round trip.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        BindingResult bindingResult = ex.getBindingResult();

        List<ErrorResponse.FieldError> fieldErrors = bindingResult.getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage()))
                .toList();

        ErrorResponse body = ErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles malformed request bodies — e.g. invalid JSON syntax, wrong value
     * types, or unknown enum values. Spring throws this before validation even runs.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            HttpMessageNotReadableException ex) {

        log.debug("Malformed request body: {}", ex.getMessage());

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.MALFORMED_REQUEST,
                "Request body is malformed or contains invalid values");

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles application-level business rule violations, such as an invalid
     * task status transition. The client is responsible for correcting the request.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolation(
            BusinessRuleViolationException ex) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.BUSINESS_RULE_VIOLATION,
                ex.getMessage());

        return ResponseEntity.badRequest().body(body);
    }

    // ── 401 Unauthorized ────────────────────────────────────────────────────

    /**
     * Handles authentication failures — e.g. missing or invalid JWT token.
     * Spring Security throws {@link AuthenticationException} when the request
     * is unauthenticated. This handler converts it to a structured JSON response
     * instead of the default HTML error page.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                ErrorCode.UNAUTHORIZED,
                "Authentication is required to access this resource");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // ── 403 Forbidden ────────────────────────────────────────────────────────

    /**
     * Handles authorisation failures — e.g. an authenticated user attempting
     * to access a resource they do not have permission for.
     * Spring Security throws {@link AccessDeniedException} in this case.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // ── 404 Not Found ────────────────────────────────────────────────────────

    /**
     * Handles missing resources. Also used when a resource exists but belongs
     * to a different user — returning 404 in that case avoids leaking the
     * existence of resources owned by others.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                ErrorCode.RESOURCE_NOT_FOUND,
                ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── 409 Conflict ────────────────────────────────────────────────────────

    /**
     * Handles uniqueness constraint violations, such as attempting to register
     * with an email address that is already in use.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                ErrorCode.DUPLICATE_RESOURCE,
                ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ── 500 Internal Server Error ────────────────────────────────────────────

    /**
     * Catch-all for any unexpected exception not handled by a more specific
     * handler above.
     *
     * <p>The full stack trace is logged at ERROR level server-side for
     * debugging. Only a generic message is returned to the client — no
     * internal details, exception types, or stack traces are exposed.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
