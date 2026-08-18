package com.viwe.task_management_system.enums;

/**
 * Represents the lifecycle status of a task.
 *
 * <p>Stored as a string in the database (EnumType.STRING).
 *
 * <p>Valid transitions (enforced in the service layer):
 * <pre>
 *   TODO         → IN_PROGRESS, CANCELLED
 *   IN_PROGRESS  → DONE, TODO (unstart), CANCELLED
 *   DONE         → TODO (reopen)
 *   CANCELLED    → TODO (reopen)
 * </pre>
 *
 * <p>Neither DONE nor CANCELLED is terminal — tasks can be reopened.
 */
public enum TaskStatus {

    /** Task has been created but work has not started. Default on creation. */
    TODO,

    /** Work on the task is actively in progress. */
    IN_PROGRESS,

    /** Task has been completed successfully. */
    DONE,

    /** Task was abandoned before completion. */
    CANCELLED
}
