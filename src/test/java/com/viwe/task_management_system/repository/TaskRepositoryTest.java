package com.viwe.task_management_system.repository;

import com.viwe.task_management_system.TestcontainersConfiguration;
import com.viwe.task_management_system.entity.Task;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;
import com.viwe.task_management_system.enums.TaskPriority;
import com.viwe.task_management_system.enums.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * <p>Validates that all ownership-scoped query methods return correct results
 * and that tasks belonging to other users are never exposed.
 *
 * <p>Uses {@code @DataJpaTest} with the MySQL Testcontainers instance so that
 * composite index behaviour and column constraints match production.
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

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.save(User.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("hash")
                .role(Role.USER)
                .build());

        otherUser = userRepository.save(User.builder()
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@example.com")
                .password("hash")
                .role(Role.USER)
                .build());

        todoTask = taskRepository.save(Task.builder()
                .title("Write tests")
                .description("Cover repository layer")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
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

    // ── findAllByUserId ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findAllByUserId returns only tasks belonging to the specified user")
    void findAllByUserId_returnsOnlyOwnerTasks() {
        Page<Task> result = taskRepository.findAllByUserId(
                owner.getId(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Write tests", "Implement service", "Fix production bug");
    }

    @Test
    @DisplayName("findAllByUserId for other user returns only that user's tasks")
    void findAllByUserId_otherUser_returnsOnlyTheirTasks() {
        Page<Task> result = taskRepository.findAllByUserId(
                otherUser.getId(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Bob's private task");
    }

    @Test
    @DisplayName("findAllByUserId supports pagination — second page is empty when fewer tasks exist")
    void findAllByUserId_paginationSecondPage_isEmpty() {
        Page<Task> result = taskRepository.findAllByUserId(
                owner.getId(), PageRequest.of(1, 10)); // page 1 with page size 10 → empty

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("findAllByUserId supports sorting by title ascending")
    void findAllByUserId_sortedByTitleAscending_returnsInOrder() {
        Page<Task> result = taskRepository.findAllByUserId(
                owner.getId(), PageRequest.of(0, 10, Sort.by("title").ascending()));

        assertThat(result.getContent())
                .extracting(Task::getTitle)
                .isSortedAccordingTo(String::compareToIgnoreCase);
    }

    // ── findAllByUserIdAndStatus ──────────────────────────────────────────────

    @Test
    @DisplayName("findAllByUserIdAndStatus returns only tasks with the given status")
    void findAllByUserIdAndStatus_filtersTodoTasks() {
        Page<Task> result = taskRepository.findAllByUserIdAndStatus(
                owner.getId(), TaskStatus.TODO, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Task::getStatus)
                .containsOnly(TaskStatus.TODO);
    }

    @Test
    @DisplayName("findAllByUserIdAndStatus returns empty page when no tasks match the status")
    void findAllByUserIdAndStatus_noMatchingStatus_returnsEmpty() {
        Page<Task> result = taskRepository.findAllByUserIdAndStatus(
                owner.getId(), TaskStatus.DONE, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getContent()).isEmpty();
    }

    // ── findAllByUserIdAndPriority ────────────────────────────────────────────

    @Test
    @DisplayName("findAllByUserIdAndPriority returns only tasks with the given priority")
    void findAllByUserIdAndPriority_filtersUrgentTasks() {
        Page<Task> result = taskRepository.findAllByUserIdAndPriority(
                owner.getId(), TaskPriority.URGENT, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Fix production bug");
    }

    @Test
    @DisplayName("findAllByUserIdAndPriority does not return tasks belonging to other users")
    void findAllByUserIdAndPriority_doesNotLeakOtherUsersTasks() {
        Page<Task> result = taskRepository.findAllByUserIdAndPriority(
                owner.getId(), TaskPriority.LOW, PageRequest.of(0, 10));

        // Bob has a LOW priority task but it must not appear here
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ── findByIdAndUserId ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findByIdAndUserId returns the task when ID and userId both match")
    void findByIdAndUserId_whenOwned_returnsTask() {
        Optional<Task> result = taskRepository.findByIdAndUserId(
                todoTask.getId(), owner.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Write tests");
    }

    @Test
    @DisplayName("findByIdAndUserId returns empty when the task exists but belongs to another user")
    void findByIdAndUserId_whenNotOwned_returnsEmpty() {
        Optional<Task> result = taskRepository.findByIdAndUserId(
                todoTask.getId(), otherUser.getId()); // owner's task queried with otherUser's ID

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId returns empty when the task ID does not exist")
    void findByIdAndUserId_whenTaskDoesNotExist_returnsEmpty() {
        Optional<Task> result = taskRepository.findByIdAndUserId(
                99999L, owner.getId());

        assertThat(result).isEmpty();
    }

    // ── existsByIdAndUserId ───────────────────────────────────────────────────

    @Test
    @DisplayName("existsByIdAndUserId returns true when task exists and is owned by user")
    void existsByIdAndUserId_whenOwned_returnsTrue() {
        boolean exists = taskRepository.existsByIdAndUserId(
                inProgressTask.getId(), owner.getId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByIdAndUserId returns false when task exists but belongs to another user")
    void existsByIdAndUserId_whenNotOwned_returnsFalse() {
        boolean exists = taskRepository.existsByIdAndUserId(
                inProgressTask.getId(), otherUser.getId());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByIdAndUserId returns false when the task ID does not exist")
    void existsByIdAndUserId_whenTaskDoesNotExist_returnsFalse() {
        boolean exists = taskRepository.existsByIdAndUserId(99999L, owner.getId());

        assertThat(exists).isFalse();
    }

    // ── JPA auditing ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createdAt and updatedAt are populated automatically on save")
    void save_populatesAuditTimestamps() {
        assertThat(todoTask.getCreatedAt()).isNotNull();
        assertThat(todoTask.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("dueDate is persisted and retrieved correctly")
    void save_withDueDate_persistsCorrectly() {
        LocalDate expectedDate = LocalDate.now().plusDays(3);

        Optional<Task> result = taskRepository.findByIdAndUserId(
                inProgressTask.getId(), owner.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getDueDate()).isEqualTo(expectedDate);
    }
}
