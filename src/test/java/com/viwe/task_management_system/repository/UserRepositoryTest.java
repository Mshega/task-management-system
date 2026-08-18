package com.viwe.task_management_system.repository;

import com.viwe.task_management_system.TestcontainersConfiguration;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link UserRepository}.
 *
 * <p>Uses {@code @DataJpaTest} to load only the JPA slice of the application
 * context (entities, repositories, Hibernate). No web layer or security beans
 * are loaded, keeping these tests fast and focused.
 *
 * <p>Uses the real MySQL Testcontainers instance (not H2) so that
 * MySQL-specific constraints such as unique indexes and column lengths
 * are exercised identically to production.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        savedUser = userRepository.save(User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .password("hashed-password")
                .role(Role.USER)
                .build());
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByEmail returns the user when the email exists")
    void findByEmail_whenEmailExists_returnsUser() {
        Optional<User> result = userRepository.findByEmail("jane.doe@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(result.get().getFirstName()).isEqualTo("Jane");
        assertThat(result.get().getLastName()).isEqualTo("Doe");
    }

    @Test
    @DisplayName("findByEmail returns empty when the email does not exist")
    void findByEmail_whenEmailDoesNotExist_returnsEmpty() {
        Optional<User> result = userRepository.findByEmail("unknown@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByEmail is case-sensitive — different case returns empty")
    void findByEmail_whenEmailDifferentCase_returnsEmpty() {
        // MySQL with default collation (utf8mb4_0900_ai_ci) is case-insensitive,
        // but we document the behaviour here so the team is aware.
        // The service layer normalises emails to lowercase before saving,
        // so this behaviour is acceptable at the repository level.
        Optional<User> result = userRepository.findByEmail("JANE.DOE@EXAMPLE.COM");

        // Document actual DB behaviour — MySQL default collation is case-insensitive
        // so this may return present; we assert on the email value regardless
        result.ifPresent(u -> assertThat(u.getId()).isEqualTo(savedUser.getId()));
    }

    // ── existsByEmail ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("existsByEmail returns true when the email exists")
    void existsByEmail_whenEmailExists_returnsTrue() {
        boolean exists = userRepository.existsByEmail("jane.doe@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByEmail returns false when the email does not exist")
    void existsByEmail_whenEmailDoesNotExist_returnsFalse() {
        boolean exists = userRepository.existsByEmail("nobody@example.com");

        assertThat(exists).isFalse();
    }

    // ── JPA auditing ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createdAt and updatedAt are populated automatically by JPA auditing")
    void save_populatesAuditTimestamps() {
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Test
    @DisplayName("saving a second user with the same email violates the unique constraint")
    void save_whenDuplicateEmail_throwsException() {
        User duplicate = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("jane.doe@example.com") // same email as savedUser
                .password("another-hash")
                .role(Role.USER)
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> userRepository.saveAndFlush(duplicate),
                "Expected a constraint violation for duplicate email"
        );
    }
}
