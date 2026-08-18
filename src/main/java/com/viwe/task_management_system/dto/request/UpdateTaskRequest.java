package com.viwe.task_management_system.dto.request;

import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for PUT /api/tasks/{id}.
 *
 * <p>All fields are optional. The service applies only the fields that are
 * non-null, leaving unchanged fields at their current values. This is a
 * partial-update (PATCH-style) contract delivered via a PUT endpoint.
 *
 * <p>To explicitly clear the description or due date, the client may send
 * an empty string for description or omit dueDate. The service interprets
 * {@code null} as "do not change" and handles explicit clearing separately
 * if that use case is needed later.
 *
 * @param title       new title (1–100 characters); {@code null} means no change
 * @param description new description (max 1000 characters); {@code null} means no change
 * @param status      new status; {@code null} means no change
 * @param priority    new priority; {@code null} means no change
 * @param dueDate     new due date (today or future); {@code null} means no change
 */
public record UpdateTaskRequest(

        @Size(min = 1, max = 100, message = "Title must be between 1 and 100 characters")
        String title,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        @FutureOrPresent(message = "Due date must be today or in the future")
        LocalDate dueDate

) {}
