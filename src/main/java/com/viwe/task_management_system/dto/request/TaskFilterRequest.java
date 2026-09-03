package com.viwe.task_management_system.dto.request;

import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;

import java.time.LocalDate;

/**
 * Encapsulates all optional filter and search parameters for the task list
 * endpoint. Every field is nullable — a {@code null} value means "no
 * constraint on this dimension".
 *
 * <p>Using a dedicated DTO keeps the controller signature compact and makes
 * it straightforward to add new filter dimensions without changing the
 * service interface.
 *
 * <p>Instances are constructed by the controller from individual
 * {@code @RequestParam} bindings and passed straight through to the service.
 * The controller never looks inside the object — it is the service's job to
 * translate filters into a repository query.
 *
 * <p>Validation constraints live here rather than in the service, keeping the
 * service focused on business logic:
 * <ul>
 *   <li>{@code title} is stripped and normalised to {@code null} when blank,
 *       so the repository never receives an empty-string wildcard that would
 *       match every row.</li>
 * </ul>
 */
public record TaskFilterRequest(

        /**
         * Optional exact-match filter on task status.
         * {@code null} → no status filter.
         */
        TaskStatus status,

        /**
         * Optional exact-match filter on task priority.
         * {@code null} → no priority filter.
         */
        TaskPriority priority,

        /**
         * Optional case-insensitive substring search on the task title.
         * The repository wraps this with {@code LIKE %title%}.
         * {@code null} or blank → no title filter.
         */
        String title,

        /**
         * Optional upper bound (inclusive) for the task due date.
         * Tasks with {@code due_date <= dueOnOrBefore} are included.
         * {@code null} → no upper bound.
         */
        LocalDate dueOnOrBefore,

        /**
         * Optional lower bound (inclusive) for the task due date.
         * Tasks with {@code due_date >= dueOnOrAfter} are included.
         * {@code null} → no lower bound.
         */
        LocalDate dueOnOrAfter

) {

    /**
     * Canonical constructor that normalises the {@code title} field:
     * a blank or whitespace-only string is treated the same as {@code null}
     * so the JPQL query never executes a pointless {@code LIKE '%%'}.
     */
    public TaskFilterRequest {
        if (title != null) {
            title = title.strip();
            if (title.isEmpty()) {
                title = null;
            }
        }
    }

    /** Returns {@code true} when no filter dimension is active. */
    public boolean isEmpty() {
        return status == null
                && priority == null
                && title == null
                && dueOnOrBefore == null
                && dueOnOrAfter == null;
    }
}
