package com.viwe.task_management_system.repository;

import com.viwe.task_management_system.TestcontainersConfiguration;
import com.viwe.task_management_system.entity.Task;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TaskRepository}.
 *
 * <p>Validates ownership-scoped derived queries and the new
 * {@code findAllByUserIdWithFilters} JPQL query that supports
 * combined filtering by status, priority, title substring, and due-date range.
 *
 * <p>Uses {@code @DataJpaTest} with the MySQL Testcontainers instance so that
 * {@code LOWER()} and {@code LIKE} behaviour matches production exactly.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User otherUser;

    private Task todoTask;
    private Task inProgressTask;
    private Task urgentTask;
    private Task doneTask;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.save(User.builder()
                .firstName("Alice").lastName("Smith")
                .email("alice@example.com").password("hash").role(Role.USER)
                .build());

        otherUser = userRepository.save(User.builder()
                .firstName("Bob").lastName("Jones")
                .email("bob@example.com").password("hash").role(Role.USER)
                .build());

        todoTask = taskRepository.save(Task.builder()
                .title("Write unit tests")
                .description("Cover repository layer")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .dueDate(LocalDate.now().plusDays(5))
                .user(owner)
                .build());

        inProgressTask = taskRepository.save(Task.builder()
                .title("Implement service")
                .status(TaskStatus.IN_PROGRESS)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.now().plusDays(3))
                .user(owner)
                .build());

        urgentTask = taskRepository.save(Task.builder()
                .title("Fix production bug")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.URGENT)
                .dueDate(LocalDate.now().plusDays(1))
                .user(owner)
                .build());

        doneTask = taskRepository.save(Task.builder()
                .title("Deploy to staging")
                .status(TaskStatus.DONE)
                .priority(TaskPriority.HIGH)
                // no dueDate — intentionally absent
                .user(owner)
                .build());

        // Task belonging to a different user — must never appear in owner's results
        taskRepository.save(Task.builder()
                .title("Bob's private task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.LOW)
                .user(otherUser)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findAllByUserId (legacy derived query — retained for backward compat)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAllByUserId")
    class FindAllByUserId {

        @Test
        @DisplayName("returns only tasks belonging to the specified user")
        void returnsOnlyOwnerTasks() {
            Page<Task> result = taskRepository.findAllByUserId(
                    owner.getId(), PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(4);
            assertThat(result.getContent())
                    .extracting(Task::getTitle)
                    .containsExactlyInAnyOrder(
                            "Write unit tests", "Implement service",
                            "Fix production bug", "Deploy to staging");
        }

        @Test
        @DisplayName("for other user returns only that user's tasks")
        void otherUser_returnsOnlyTheirTasks() {
            Page<Task> result = taskRepository.findAllByUserId(
                    otherUser.getId(), PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Bob's private task");
        }

        @Test
        @DisplayName("second page is empty when fewer tasks exist than page size")
        void secondPage_isEmpty() {
            Page<Task> result = taskRepository.findAllByUserId(
                    owner.getId(), PageRequest.of(1, 10));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(4);
        }

        @Test
        @DisplayName("supports sorting by title ascending")
        void sortedByTitleAscending() {
            Page<Task> result = taskRepository.findAllByUserId(
                    owner.getId(), PageRequest.of(0, 10, Sort.by("title").ascending()));

            assertThat(result.getContent())
                    .extracting(Task::getTitle)
                    .isSortedAccordingTo(String::compareToIgnoreCase);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findAllByUserIdAndStatus (legacy)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAllByUserIdAndStatus")
    class FindAllByUserIdAndStatus {

        @Test
        @DisplayName("returns only tasks with the given status")
        void filtersTodoTasks() {
            Page<Task> result = taskRepository.findAllByUserIdAndStatus(
                    owner.getId(), TaskStatus.TODO, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                    .extracting(Task::getStatus)
                    .containsOnly(TaskStatus.TODO);
        }

        @Test
        @DisplayName("returns empty page when no tasks match the status")
        void noMatchingStatus_returnsEmpty() {
            Page<Task> result = taskRepository.findAllByUserIdAndStatus(
                    owner.getId(), TaskStatus.CANCELLED, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findAllByUserIdAndPriority (legacy)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAllByUserIdAndPriority")
    class FindAllByUserIdAndPriority {

        @Test
        @DisplayName("returns only tasks with the given priority")
        void filtersUrgentTasks() {
            Page<Task> result = taskRepository.findAllByUserIdAndPriority(
                    owner.getId(), TaskPriority.URGENT, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Fix production bug");
        }

        @Test
        @DisplayName("does not return tasks belonging to other users")
        void doesNotLeakOtherUsersTasks() {
            Page<Task> result = taskRepository.findAllByUserIdAndPriority(
                    owner.getId(), TaskPriority.LOW, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findAllByUserIdWithFilters (new unified @Query)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAllByUserIdWithFilters")
    class FindAllByUserIdWithFilters {

        // ── No filter (baseline) ──────────────────────────────────────────

        @Test
        @DisplayName("all-null filters returns all owner tasks")
        void noFilters_returnsAllOwnerTasks() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(4);
        }

        @Test
        @DisplayName("all-null filters never returns another user's tasks")
        void noFilters_doesNotLeakOtherUserTasks() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, null, null,
                    PageRequest.of(0, 100));

            assertThat(result.getContent())
                    .extracting(Task::getTitle)
                    .doesNotContain("Bob's private task");
        }

        // ── Status filter ─────────────────────────────────────────────────

        @Test
        @DisplayName("status filter returns only tasks with that status")
        void statusFilter_returnsTodoOnly() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), TaskStatus.TODO, null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                    .extracting(Task::getStatus)
                    .containsOnly(TaskStatus.TODO);
        }

        @Test
        @DisplayName("status filter returns empty page when no tasks match")
        void statusFilter_noMatch_returnsEmpty() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), TaskStatus.CANCELLED, null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        // ── Priority filter ───────────────────────────────────────────────

        @Test
        @DisplayName("priority filter returns only tasks with that priority")
        void priorityFilter_returnsHighPriorityOnly() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, TaskPriority.HIGH, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2); // inProgressTask + doneTask
            assertThat(result.getContent())
                    .extracting(Task::getPriority)
                    .containsOnly(TaskPriority.HIGH);
        }

        // ── Title search ──────────────────────────────────────────────────

        @Test
        @DisplayName("title search returns tasks whose title contains the substring (case-insensitive)")
        void titleSearch_partialMatch_caseInsensitive() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, "test", null, null,
                    PageRequest.of(0, 10));

            // "Write unit tests" contains "test" (case-insensitive)
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Write unit tests");
        }

        @Test
        @DisplayName("title search is case-insensitive — uppercase query matches lowercase title")
        void titleSearch_uppercaseQuery_matchesLowercaseTitle() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, "TESTS", null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Write unit tests");
        }

        @Test
        @DisplayName("title search that matches no task returns empty page")
        void titleSearch_noMatch_returnsEmpty() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, "zzznomatch", null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("title search does not return matching tasks from other users")
        void titleSearch_doesNotLeakOtherUserTasks() {
            // "Bob's private task" contains "task", as do some owner tasks
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, "private", null, null,
                    PageRequest.of(0, 10));

            // "private" only appears in Bob's task — should return nothing for owner
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        // ── Due-date range filters ────────────────────────────────────────

        @Test
        @DisplayName("dueOnOrBefore returns tasks with due_date <= bound (inclusive)")
        void dueOnOrBefore_returnsTasksDueByBound() {
            // urgentTask due in 1 day, inProgressTask in 3, todoTask in 5
            // doneTask has no dueDate — should be excluded
            LocalDate bound = LocalDate.now().plusDays(3);
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, bound, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                    .extracting(Task::getTitle)
                    .containsExactlyInAnyOrder("Fix production bug", "Implement service");
        }

        @Test
        @DisplayName("dueOnOrAfter returns tasks with due_date >= bound (inclusive)")
        void dueOnOrAfter_returnsTasksDueFromBound() {
            LocalDate bound = LocalDate.now().plusDays(3);
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, null, bound,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(3); // +3, +5 days, and doneTask has no due date but doesn't match
            assertThat(result.getContent())
                    .extracting(Task::getTitle)
                    .containsExactlyInAnyOrder("Write unit tests", "Implement service", "Fix production bug");
        }

        @Test
        @DisplayName("dueOnOrBefore and dueOnOrAfter combined produces a date-range window")
        void dueRange_combinedBounds_returnsTasksInsideWindow() {
            LocalDate from = LocalDate.now().plusDays(2);
            LocalDate to   = LocalDate.now().plusDays(4);

            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, to, from,
                    PageRequest.of(0, 10));

            // inProgressTask (due +3 days) is the only one inside [+2, +4]
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Implement service");
        }

        @Test
        @DisplayName("tasks without a dueDate are excluded when a date-range filter is active")
        void dueRange_excludesTasksWithNullDueDate() {
            LocalDate from = LocalDate.now();
            LocalDate to   = LocalDate.now().plusDays(10);

            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, to, from,
                    PageRequest.of(0, 10));

            // doneTask has no dueDate — must not appear even though the window is wide
            assertThat(result.getContent())
                    .extracting(Task::getTitle)
                    .doesNotContain("Deploy to staging");
        }

        // ── Combined filters ──────────────────────────────────────────────

        @Test
        @DisplayName("status + priority combined returns only tasks matching both")
        void combined_statusAndPriority_andedTogether() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), TaskStatus.TODO, TaskPriority.URGENT, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Fix production bug");
        }

        @Test
        @DisplayName("status + title combined returns only tasks matching both")
        void combined_statusAndTitle() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), TaskStatus.TODO, null, "unit", null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Write unit tests");
        }

        @Test
        @DisplayName("status + priority + title all combined narrows results correctly")
        void combined_allThreeFilters() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), TaskStatus.TODO, TaskPriority.MEDIUM, "unit", null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Write unit tests");
        }

        @Test
        @DisplayName("combined filter that matches nothing returns empty page")
        void combined_noMatch_returnsEmpty() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), TaskStatus.DONE, TaskPriority.URGENT, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        // ── Pagination and sorting ────────────────────────────────────────

        @Test
        @DisplayName("pageable size is respected — page 0 size 2 returns exactly 2 items")
        void pagination_pageSizeIsRespected() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, null, null,
                    PageRequest.of(0, 2));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(4);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("second page of size 2 returns remaining 2 items")
        void pagination_secondPage_returnsRemainingItems() {
            Page<Task> page0 = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, null, null,
                    PageRequest.of(0, 2));
            Page<Task> page1 = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, null, null,
                    PageRequest.of(1, 2));

            assertThat(page1.getContent()).hasSize(2);
            // Pages must not overlap
            assertThat(page0.getContent())
                    .extracting(Task::getId)
                    .doesNotContainAnyElementsOf(
                            page1.getContent().stream().map(Task::getId).toList());
        }

        @Test
        @DisplayName("sort by dueDate ascending orders tasks correctly")
        void sorting_byDueDateAscending() {
            // Only tasks with a dueDate (all except doneTask which has no due date)
            // Apply a date filter to ensure we only get tasks with a known dueDate
            LocalDate from = LocalDate.now();
            LocalDate to   = LocalDate.now().plusDays(30);

            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, to, from,
                    PageRequest.of(0, 10, Sort.by("dueDate").ascending()));

            assertThat(result.getContent())
                    .extracting(Task::getDueDate)
                    .isSorted();
        }

        @Test
        @DisplayName("sort by title ascending orders tasks alphabetically")
        void sorting_byTitleAscending() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, null, null,
                    PageRequest.of(0, 10, Sort.by("title").ascending()));

            assertThat(result.getContent())
                    .extracting(Task::getTitle)
                    .isSortedAccordingTo(String::compareToIgnoreCase);
        }

        // ── Ownership isolation (cross-user) ──────────────────────────────

        @Test
        @DisplayName("owner filter never surfaces Bob's tasks regardless of filters")
        void ownershipIsolation_neverReturnsOtherUsersTasks() {
            // Use no filters so everything would be returned if scoping were broken
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    owner.getId(), null, null, null, null, null,
                    PageRequest.of(0, 100));

            assertThat(result.getContent())
                    .allSatisfy(t -> assertThat(t.getUser().getId()).isEqualTo(owner.getId()));
        }

        @Test
        @DisplayName("filtering on a different userId returns that user's tasks only")
        void ownershipIsolation_differentUserId_returnsOnlyTheirTasks() {
            Page<Task> result = taskRepository.findAllByUserIdWithFilters(
                    otherUser.getId(), null, null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getUser().getId())
                    .isEqualTo(otherUser.getId());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findByIdAndUserId
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByIdAndUserId")
    class FindByIdAndUserId {

        @Test
        @DisplayName("returns the task when ID and userId both match")
        void whenOwned_returnsTask() {
            Optional<Task> result = taskRepository.findByIdAndUserId(
                    todoTask.getId(), owner.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getTitle()).isEqualTo("Write unit tests");
        }

        @Test
        @DisplayName("returns empty when task exists but belongs to another user")
        void whenNotOwned_returnsEmpty() {
            Optional<Task> result = taskRepository.findByIdAndUserId(
                    todoTask.getId(), otherUser.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when task ID does not exist")
        void whenTaskDoesNotExist_returnsEmpty() {
            Optional<Task> result = taskRepository.findByIdAndUserId(99999L, owner.getId());

            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // existsByIdAndUserId
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("existsByIdAndUserId")
    class ExistsByIdAndUserId {

        @Test
        @DisplayName("returns true when task exists and is owned by user")
        void whenOwned_returnsTrue() {
            assertThat(taskRepository.existsByIdAndUserId(
                    inProgressTask.getId(), owner.getId())).isTrue();
        }

        @Test
        @DisplayName("returns false when task exists but belongs to another user")
        void whenNotOwned_returnsFalse() {
            assertThat(taskRepository.existsByIdAndUserId(
                    inProgressTask.getId(), otherUser.getId())).isFalse();
        }

        @Test
        @DisplayName("returns false when task ID does not exist")
        void whenTaskDoesNotExist_returnsFalse() {
            assertThat(taskRepository.existsByIdAndUserId(99999L, owner.getId())).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JPA auditing
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JPA auditing")
    class Auditing {

        @Test
        @DisplayName("createdAt and updatedAt are populated automatically on save")
        void populatesAuditTimestamps() {
            assertThat(todoTask.getCreatedAt()).isNotNull();
            assertThat(todoTask.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("dueDate is persisted and retrieved correctly")
        void persistsDueDate() {
            Optional<Task> result = taskRepository.findByIdAndUserId(
                    inProgressTask.getId(), owner.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getDueDate()).isEqualTo(LocalDate.now().plusDays(3));
        }
    }
}
