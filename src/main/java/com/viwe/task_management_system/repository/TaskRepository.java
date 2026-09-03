package com.viwe.task_management_system.repository;

import com.viwe.task_management_system.entity.Task;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Persistence operations for {@link Task} entities.
 *
 * <h2>Query strategy</h2>
 * <p>All list queries are scoped to a specific user ({@code userId}) to
 * enforce data isolation at the database level.
 *
 * <p>The primary list query is {@link #findAllByUserIdWithFilters}, a single
 * JPQL {@code @Query} that handles every combination of optional filters
 * (status, priority, title substring, due-date bounds) in one database round
 * trip. Each filter parameter is optional — passing {@code null} disables
 * that dimension entirely. This replaces the previous approach of having
 * separate derived-query methods for each filter combination, which could not
 * be combined and did not support title search or date-range filtering.
 *
 * <p>The older derived-query methods ({@code findAllByUserId},
 * {@code findAllByUserIdAndStatus}, {@code findAllByUserIdAndPriority}) are
 * retained for backward compatibility with existing tests and any callers that
 * do not yet use the unified filter path.
 *
 * <h2>Pagination and sorting</h2>
 * <p>All list methods accept a {@link Pageable} parameter. Sorting,
 * page number, and page size are all controlled by the caller. The service
 * layer is responsible for capping the page size before the {@code Pageable}
 * reaches the repository.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ── Unified filtered query ────────────────────────────────────────────────

    /**
     * Returns a page of tasks belonging to the specified user, optionally
     * filtered by status, priority, a case-insensitive title substring, and
     * a due-date range.
     *
     * <p>Each parameter is optional. Passing {@code null} for any parameter
     * removes that filter from the WHERE clause entirely (the JPQL conditional
     * {@code (:param IS NULL OR ...)} evaluates to {@code true} and the
     * predicate is skipped). Passing values for multiple parameters produces
     * AND-combined predicates.
     *
     * <p>The title search uses {@code LOWER(t.title) LIKE LOWER(CONCAT('%', :title, '%'))}
     * which is case-insensitive and substring-matches anywhere in the title.
     *
     * <p>Due-date filters are inclusive:
     * {@code dueOnOrBefore} matches {@code due_date <= :dueOnOrBefore} and
     * {@code dueOnOrAfter} matches {@code due_date >= :dueOnOrAfter}.
     * Tasks without a due date are excluded from results when either
     * date-range filter is active.
     *
     * <p><strong>Ownership:</strong> the {@code userId} predicate is always
     * present and cannot be bypassed. The service extracts this value
     * exclusively from the Spring Security principal.
     *
     * @param userId       the ID of the authenticated owner — never from client input
     * @param status       exact-match status filter, or {@code null} to skip
     * @param priority     exact-match priority filter, or {@code null} to skip
     * @param title        case-insensitive title substring, or {@code null} to skip
     * @param dueOnOrBefore upper due-date bound (inclusive), or {@code null} to skip
     * @param dueOnOrAfter  lower due-date bound (inclusive), or {@code null} to skip
     * @param pageable     pagination and sorting parameters
     * @return a page of matching tasks owned by the user
     */
    @Query("""
            SELECT t FROM Task t
            WHERE t.user.id = :userId
              AND (:status       IS NULL OR t.status   = :status)
              AND (:priority     IS NULL OR t.priority = :priority)
              AND (:title        IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :title, '%')))
              AND (:dueOnOrBefore IS NULL OR t.dueDate <= :dueOnOrBefore)
              AND (:dueOnOrAfter  IS NULL OR t.dueDate >= :dueOnOrAfter)
            """)
    Page<Task> findAllByUserIdWithFilters(
            @Param("userId")        Long userId,
            @Param("status")        TaskStatus status,
            @Param("priority")      TaskPriority priority,
            @Param("title")         String title,
            @Param("dueOnOrBefore") LocalDate dueOnOrBefore,
            @Param("dueOnOrAfter")  LocalDate dueOnOrAfter,
            Pageable pageable);

    // ── Legacy derived-query methods (kept for existing callers) ─────────────

    /**
     * Returns a page of all tasks belonging to the specified user.
     *
     * @param userId   the ID of the owning user
     * @param pageable pagination and sorting parameters
     * @return a page of tasks owned by the user
     */
    Page<Task> findAllByUserId(Long userId, Pageable pageable);

    /**
     * Returns a page of tasks belonging to the specified user filtered by status.
     *
     * @param userId   the ID of the owning user
     * @param status   the task status to filter by
     * @param pageable pagination and sorting parameters
     * @return a page of matching tasks
     */
    Page<Task> findAllByUserIdAndStatus(Long userId, TaskStatus status, Pageable pageable);

    /**
     * Returns a page of tasks belonging to the specified user filtered by priority.
     *
     * @param userId   the ID of the owning user
     * @param priority the priority level to filter by
     * @param pageable pagination and sorting parameters
     * @return a page of matching tasks
     */
    Page<Task> findAllByUserIdAndPriority(Long userId, TaskPriority priority, Pageable pageable);

    // ── Single-task queries ───────────────────────────────────────────────────

    /**
     * Finds a single task by its ID, but only if it belongs to the specified user.
     *
     * @param id     the task ID
     * @param userId the ID of the owning user
     * @return an {@link Optional} containing the task if found and owned, or empty
     */
    Optional<Task> findByIdAndUserId(Long id, Long userId);

    /**
     * Checks whether a task with the given ID exists and belongs to the specified user.
     *
     * @param id     the task ID
     * @param userId the ID of the owning user
     * @return {@code true} if the task exists and is owned by this user
     */
    boolean existsByIdAndUserId(Long id, Long userId);
}
