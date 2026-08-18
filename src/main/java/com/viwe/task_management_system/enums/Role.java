package com.viwe.task_management_system.enums;

/**
 * Represents the role assigned to a user account.
 *
 * <p>Stored as a string in the database (EnumType.STRING) so that
 * adding future roles does not shift ordinal values.
 *
 * <p>All newly registered users receive the USER role by default.
 * ADMIN is defined now so the security layer can reference it later
 * without a schema change, but no admin-specific endpoints exist yet.
 */
public enum Role {

    /** Standard registered user — default role on registration. */
    USER,

    /** Administrator — reserved for future privileged operations. */
    ADMIN
}
