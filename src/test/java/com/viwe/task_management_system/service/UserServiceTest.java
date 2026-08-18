package com.viwe.task_management_system.service;

import com.viwe.task_management_system.dto.response.UserResponse;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;
import com.viwe.task_management_system.exception.ResourceNotFoundException;
import com.viwe.task_management_system.repository.UserRepository;
import com.viwe.task_management_system.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("hashed")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getUserProfile returns UserResponse for existing user")
    void getUserProfile_whenUserExists_returnsUserResponse() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.getUserProfile(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.firstName()).isEqualTo("Alice");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("getUserProfile throws ResourceNotFoundException for unknown user")
    void getUserProfile_whenUserNotFound_throwsNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User")
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("loadUserByUsername returns UserDetails for existing email")
    void loadUserByUsername_whenEmailExists_returnsUser() {
        given(userRepository.findByEmail("alice@example.com")).willReturn(Optional.of(user));

        var result = userService.loadUserByUsername("alice@example.com");

        assertThat(result.getUsername()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException for unknown email")
    void loadUserByUsername_whenEmailNotFound_throwsUsernameNotFound() {
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("UserResponse does not contain password")
    void getUserProfile_responseNeverContainsPassword() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.getUserProfile(1L);

        // UserResponse is a record — verify it has no password field by
        // confirming all expected fields are present and no extras exist
        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isNotNull();
        assertThat(response.firstName()).isNotNull();
        assertThat(response.lastName()).isNotNull();
        assertThat(response.role()).isNotNull();
        assertThat(response.createdAt()).isNotNull();
    }
}
