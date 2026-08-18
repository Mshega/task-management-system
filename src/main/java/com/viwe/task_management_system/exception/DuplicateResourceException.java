package com.viwe.task_management_system.exception;

/**
 * Thrown when a create operation would violate a uniqueness constraint,
 * such as attempting to register with an email address that is already in use.
 *
 * <p>Maps to HTTP 409 Conflict.
 *
 * <p>Usage example:
 * <pre>{@code
 * if (userRepository.existsByEmail(request.email())) {
 *     throw new DuplicateResourceException("User", "email", request.email());
 * }
 * }</pre>
 */
public class DuplicateResourceException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message in the form
     * "{@code <resourceName> already exists with <field>: <value>}".
     *
     * @param resourceName the type of resource (e.g. "User")
     * @param field        the field that must be unique (e.g. "email")
     * @param value        the duplicate value (e.g. "alice@example.com")
     */
    public DuplicateResourceException(String resourceName, String field, Object value) {
        super(resourceName + " already exists with " + field + ": " + value);
    }

    /**
     * Creates an exception with a fully custom message.
     *
     * @param message a description of the conflict
     */
    public DuplicateResourceException(String message) {
        super(message);
    }
}
