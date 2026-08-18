package com.viwe.task_management_system.dto.request;

import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for POST /api/tasks.
 *
 * <p>Only {@code title} is required. All other fields are optional and fall
 * back to sensible defaults in the service layer:
 * <ul>
 *   <li>{@code status} defaults to {@code TODO} if {@code null}</li>
 *   <li>{@code priority} defaults to {@code MEDIUM} if {@code null}</li>
 *   <li>{@code description} and {@code dueDate} remain {@code null}</li>
 * </ul>
 *
 * @param title       short descriptive title (required, 1–100 characters)
 * @param description optional longer description (max 1000 characters)
 * @param status      optional initial status; defaults to TODO
 * @param priority    optional priority level; defaults to MEDIUM
 * @param dueDate     optional due date; if provided must be today or in the future
 */
public record CreateTaskRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 1, max = 100, message = "Title must be between 1 and 100 characters")
        String title,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        @FutureOrPresent(message = "Due date must be today or in the future")
        LocalDate dueDate

) {}
