package com.viwe.task_management_system.service.impl;

import com.viwe.task_management_system.dto.response.UserResponse;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.exception.ResourceNotFoundException;
import com.viwe.task_management_system.repository.UserRepository;
import com.viwe.task_management_system.service.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link UserService} and Spring Security's
 * {@link UserDetailsService}.
 *
 * <p>Implementing {@link UserDetailsService} here means the JWT authentication
 * filter can load users by email directly from this service.
 *
 * <p>The {@code User} entity already implements {@code UserDetails}, so
 * {@link #loadUserByUsername(String)} simply delegates to
 * {@link UserRepository#findByEmail(String)}.
 */
@Service
@Transactional
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ── UserService ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return UserResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User loadUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + email));
    }

    // ── UserDetailsService ───────────────────────────────────────────────────

    /**
     * Loads a user by their email address for Spring Security authentication.
     *
     * <p>The username in this application is the email address. Spring Security
     * calls this method during the authentication process. The returned
     * {@link UserDetails} is the {@link User} entity itself, which already
     * implements {@link UserDetails}.
     *
     * @param email the email address used as the login identifier
     * @return the matching {@link User} entity
     * @throws UsernameNotFoundException if no user with the given email exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));
    }
}
