package com.viwe.task_management_system.entity;

import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a task owned by a user.
 *
 * <p>Tasks are always associated with exactly one {@link User} (the owner).
 * Ownership is enforced at the service layer: a user may only read or
 * modify their own tasks.
 *
 * <p>The relationship to {@link User} is unidirectional (Task → User) with
 * LAZY loading. A bidirectional mapping is intentionally avoided to prevent
 * accidental serialisation of entire task collections when fetching a user,
 * and to keep the entity graph simple.
 *
 * <p>Timestamps are managed by JPA auditing — never set manually in code.
 */
@Entity
@Table(
    name = "tasks",
    indexes = {
        // Most common query pattern: all tasks belonging to a specific user
        @Index(name = "idx_tasks_user_id", columnList = "user_id"),
        // Support filtering by status per user
        @Index(name = "idx_tasks_user_id_status", columnList = "user_id, status"),
        // Support filtering by priority per user
        @Index(name = "idx_tasks_user_id_priority", columnList = "user_id, priority")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Short descriptive title for the task. Required, non-blank.
     * Max 100 characters enforced at both the database and validation layer.
     */
    @Column(nullable = false, length = 100)
    private String title;

    /**
     * Optional longer description providing detail about the task.
     * Stored as TEXT in the database to accommodate larger content
     * without a fixed VARCHAR limit.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Current lifecycle status of the task.
     * Defaults to TODO on creation.
     * Stored as a string so adding future statuses is non-breaking.
     * Valid transitions are enforced in the service layer.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    /**
     * Priority level of the task.
     * Defaults to MEDIUM on creation.
     * Stored as a string for the same reason as status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    /**
     * Optional due date. Date only — no time component is needed.
     * When provided, must not be in the past (validated at the service/DTO layer).
     */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * The user who owns this task.
     *
     * <p>Loaded lazily: the user record is not fetched from the database
     * unless explicitly accessed. This avoids unnecessary joins when
     * loading task lists.
     *
     * <p>The FK column {@code user_id} is non-nullable — a task cannot
     * exist without an owner.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
