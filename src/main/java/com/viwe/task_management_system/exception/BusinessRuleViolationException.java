package com.viwe.task_management_system.exception;

/**
 * Thrown when a request is structurally valid but violates an application
 * business rule.
 *
 * <p>The most common use case is an invalid task status transition, for example
 * attempting to move a {@code DONE} task directly to {@code IN_PROGRESS}.
 *
 * <p>Maps to HTTP 400 Bad Request because the error is the client's
 * responsibility to resolve — the data is well-formed but the operation
 * is not permitted given the current state.
 *
 * <p>Usage example:
 * <pre>{@code
 * if (!isValidTransition(current, requested)) {
 *     throw new BusinessRuleViolationException(
 *         "Cannot transition task from " + current + " to " + requested);
 * }
 * }</pre>
 */
public class BusinessRuleViolationException extends RuntimeException {

    /**
     * @param message a description of which business rule was violated
     */
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
