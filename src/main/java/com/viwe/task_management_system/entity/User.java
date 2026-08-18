package com.viwe.task_management_system.entity;

import com.viwe.task_management_system.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Represents an application user.
 *
 * <p>Implements {@link UserDetails} so this entity can be used directly
 * as the Spring Security principal, avoiding a separate wrapper class.
 *
 * <p>The {@code password} field is never included in API responses —
 * DTOs are used for all controller input/output, and no DTO exposes
 * the password field.
 *
 * <p>Table is named {@code users} because {@code user} is a reserved
 * word in MySQL and several other databases.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    /**
     * The user's email address. Serves as the login username.
     * Must be unique across the system.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * BCrypt-hashed password. Never exposed through any DTO or API response.
     */
    @Column(nullable = false)
    private String password;

    /**
     * The user's assigned role. Defaults to USER on registration.
     * Stored as a string so adding future roles is non-breaking.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // UserDetails contract
    // -------------------------------------------------------------------------

    /**
     * Returns a single authority derived from the user's role,
     * prefixed with "ROLE_" as required by Spring Security.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Spring Security uses this value as the username identifier.
     * Email is used here because it is unique and is the login credential.
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Returns the stored BCrypt password hash.
     * Spring Security uses this for credential verification.
     */
    @Override
    public String getPassword() {
        return password;
    }

    // The following methods return true unconditionally for now.
    // Account locking and expiry features are not part of the current scope.

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
