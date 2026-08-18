package com.viwe.task_management_system.enums;

/**
 * Represents the priority level assigned to a task.
 *
 * <p>Stored as a string in the database (EnumType.STRING).
 * Ordered from lowest to highest urgency: LOW → MEDIUM → HIGH → URGENT.
 *
 * <p>MEDIUM is the default on task creation.
 */
public enum TaskPriority {

    /** Low urgency — can be addressed when time permits. */
    LOW,

    /** Normal urgency — standard work item. Default on creation. */
    MEDIUM,

    /** High urgency — should be addressed soon. */
    HIGH,

    /** Critical urgency — requires immediate attention. */
    URGENT
}
