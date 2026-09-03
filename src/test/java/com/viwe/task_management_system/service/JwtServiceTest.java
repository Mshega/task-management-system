package com.viwe.task_management_system.service;

import com.viwe.task_management_system.config.JwtProperties;
import com.viwe.task_management_system.entity.User;
import com.viwe.task_management_system.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for {@link JwtService}.
 *
 * <p>No Spring context is loaded. {@link JwtProperties} is wired manually
 * so the tests run without any environment variables and without a database.
 *
 * <p>The HMAC-SHA256 secret used here is a 32-byte key encoded as Base64 —
 * the minimum length enforced by {@code JwtService}'s own constructor guard.
 *
 * <h2>Coverage matrix</h2>
 * <ul>
 *   <li>Valid token — generation, parsing, and validation succeed end-to-end.</li>
 *   <li>Invalid / tampered token — altering even one character of the payload
 *       must cause validation to return {@code false}.</li>
 *   <li>Expired token — a token whose {@code exp} is in the past must be
 *       rejected by {@code isTokenValid}.</li>
 *   <li>Missing / blank token — {@code extractEmail} on an empty string must
 *       throw rather than silently return {@code null}.</li>
 *   <li>Wrong subject — a valid token issued for User A must not validate
 *       against User B's {@link UserDetails}.</li>
 *   <li>Secret too short — the constructor must throw {@link IllegalStateException}
 *       when the decoded secret is under 32 bytes.</li>
 *   <li>Missing secret — the constructor must throw when the secret property
 *       is blank or null.</li>
 * </ul>
 *
 * <p>Endpoint-level scenarios (protected endpoint without authentication,
 * public endpoint without authentication) are covered in
 * {@link com.viwe.task_management_system.controller.JwtSecurityIntegrationTest},
 * which exercises the full Spring Security filter chain via {@code @WebMvcTest}.
 */
class JwtServiceTest {

    /**
     * A valid 32-byte key, Base64-encoded.
     * 32 × 8 = 256 bits, which is the minimum HS256 key size.
     */
    private static final String VALID_SECRET =
            Base64.getEncoder().encodeToString(
                    "01234567890123456789012345678901".getBytes());

    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    private JwtService jwtService;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        JwtProperties props = buildProperties(VALID_SECRET, EXPIRATION_MS);
        jwtService = new JwtService(props);

        userA = User.builder()
                .id(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        userB = User.builder()
                .id(2L)
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();
    }

    // ── Valid token ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid token")
    class ValidToken {

        @Test
        @DisplayName("generateToken produces a non-blank compact JWT string")
        void generateToken_producesNonBlankJwt() {
            String token = jwtService.generateToken(userA);

            assertThat(token)
                    .isNotBlank()
                    .contains(".")   // JWS compact serialisation has three dot-separated parts
                    .matches("^[\\w-]+\\.[\\w-]+\\.[\\w-]+$");
        }

        @Test
        @DisplayName("extractEmail returns the subject used during token creation")
        void extractEmail_returnsCorrectSubject() {
            String token = jwtService.generateToken(userA);

            assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("isTokenValid returns true for a freshly issued token")
        void isTokenValid_freshToken_returnsTrue() {
            String token = jwtService.generateToken(userA);

            assertThat(jwtService.isTokenValid(token, userA)).isTrue();
        }

        @Test
        @DisplayName("isTokenValid returns true regardless of non-security fields on UserDetails")
        void isTokenValid_validatesSubjectNotEntityFields() {
            // Rebuild the same user with a different id — subject is email, not id
            User sameEmail = User.builder()
                    .id(99L)
                    .email("alice@example.com")
                    .password("different-hash")
                    .role(Role.USER)
                    .build();

            String token = jwtService.generateToken(userA);

            assertThat(jwtService.isTokenValid(token, sameEmail)).isTrue();
        }

        @Test
        @DisplayName("two tokens for the same user are independently valid")
        void generateToken_twoTokensForSameUser_bothValid() {
            String token1 = jwtService.generateToken(userA);
            String token2 = jwtService.generateToken(userA);

            assertThat(jwtService.isTokenValid(token1, userA)).isTrue();
            assertThat(jwtService.isTokenValid(token2, userA)).isTrue();
        }
    }

    // ── Invalid / tampered token ──────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid / tampered token")
    class InvalidToken {

        @Test
        @DisplayName("isTokenValid returns false when the signature segment is altered")
        void isTokenValid_alteredSignature_returnsFalse() {
            String token = jwtService.generateToken(userA);

            // Replace the last character of the signature to break it
            String tampered = token.substring(0, token.length() - 1) + "X";

            assertThat(jwtService.isTokenValid(tampered, userA)).isFalse();
        }

        @Test
        @DisplayName("isTokenValid returns false for a completely garbage string")
        void isTokenValid_garbageString_returnsFalse() {
            assertThat(jwtService.isTokenValid("not.a.jwt", userA)).isFalse();
        }

        @Test
        @DisplayName("isTokenValid returns false for an empty string")
        void isTokenValid_emptyString_returnsFalse() {
            assertThat(jwtService.isTokenValid("", userA)).isFalse();
        }

        @Test
        @DisplayName("isTokenValid returns false when validated against the wrong user")
        void isTokenValid_wrongUser_returnsFalse() {
            // Token issued for User A must not validate against User B
            String tokenForA = jwtService.generateToken(userA);

            assertThat(jwtService.isTokenValid(tokenForA, userB)).isFalse();
        }

        @Test
        @DisplayName("isTokenValid returns false for a token signed with a different secret")
        void isTokenValid_differentSecret_returnsFalse() {
            // Produce a token with a different key
            String otherSecret = Base64.getEncoder().encodeToString(
                    "98765432109876543210987654321098".getBytes());
            JwtProperties otherProps = buildProperties(otherSecret, EXPIRATION_MS);
            JwtService otherService = new JwtService(otherProps);

            String tokenFromOtherKey = otherService.generateToken(userA);

            // Original service must reject it — wrong HMAC signature
            assertThat(jwtService.isTokenValid(tokenFromOtherKey, userA)).isFalse();
        }
    }

    // ── Expired token ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Expired token")
    class ExpiredToken {

        @Test
        @DisplayName("isTokenValid returns false for a token whose expiry is in the past")
        void isTokenValid_expiredToken_returnsFalse() {
            // Use the explicit-expiry overload designed exactly for this test scenario
            Instant pastExpiry = Instant.now().minusSeconds(1);
            String expiredToken = jwtService.generateToken(userA, pastExpiry);

            assertThat(jwtService.isTokenValid(expiredToken, userA)).isFalse();
        }

        @Test
        @DisplayName("isTokenValid returns false for a token that expired 1 hour ago")
        void isTokenValid_longExpiredToken_returnsFalse() {
            Instant oneHourAgo = Instant.now().minusSeconds(3_600);
            String expiredToken = jwtService.generateToken(userA, oneHourAgo);

            assertThat(jwtService.isTokenValid(expiredToken, userA)).isFalse();
        }

        @Test
        @DisplayName("extractEmail still parses the subject from an expired token")
        void extractEmail_expiredToken_stillParsesSubject() {
            // extractEmail is intentionally signature-agnostic; expiry is not checked here.
            // The filter always calls isTokenValid separately for the full check.
            Instant pastExpiry = Instant.now().minusSeconds(1);
            String expiredToken = jwtService.generateToken(userA, pastExpiry);

            assertThat(jwtService.extractEmail(expiredToken)).isEqualTo("alice@example.com");
        }
    }

    // ── Missing / blank token ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing / blank token input")
    class MissingToken {

        @Test
        @DisplayName("extractEmail throws RuntimeException for an empty string")
        void extractEmail_emptyString_throwsRuntimeException() {
            assertThatThrownBy(() -> jwtService.extractEmail(""))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("extractEmail throws RuntimeException for a completely malformed string")
        void extractEmail_malformedString_throwsRuntimeException() {
            assertThatThrownBy(() -> jwtService.extractEmail("this-is-not-a-jwt"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("isTokenValid returns false without throwing for an empty string")
        void isTokenValid_emptyString_returnsFalseNotThrow() {
            // The filter relies on isTokenValid returning false — never throwing —
            // so callers do not need a try/catch around it.
            assertThat(jwtService.isTokenValid("", userA)).isFalse();
        }

        @Test
        @DisplayName("isTokenValid returns false without throwing for a null-like garbage input")
        void isTokenValid_malformedInput_returnsFalseNotThrow() {
            assertThat(jwtService.isTokenValid("header.payload", userA)).isFalse();
        }
    }

    // ── Secret validation at construction ─────────────────────────────────────

    @Nested
    @DisplayName("Secret configuration validation")
    class SecretValidation {

        @Test
        @DisplayName("constructor throws IllegalStateException when secret decodes to fewer than 32 bytes")
        void constructor_shortSecret_throwsIllegalState() {
            // 31 bytes after decoding — one byte under the minimum
            String shortSecret = Base64.getEncoder().encodeToString(
                    "0123456789012345678901234567890".getBytes()); // 31 chars

            JwtProperties props = buildProperties(shortSecret, EXPIRATION_MS);

            assertThatThrownBy(() -> new JwtService(props))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("32");
        }

        @Test
        @DisplayName("constructor throws IllegalStateException when secret is blank")
        void constructor_blankSecret_throwsIllegalState() {
            JwtProperties props = buildProperties("   ", EXPIRATION_MS);

            assertThatThrownBy(() -> new JwtService(props))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("constructor throws IllegalStateException when secret is null")
        void constructor_nullSecret_throwsIllegalState() {
            JwtProperties props = buildProperties(null, EXPIRATION_MS);

            assertThatThrownBy(() -> new JwtService(props))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("constructor succeeds when secret decodes to exactly 32 bytes")
        void constructor_exactly32ByteSecret_succeeds() {
            String exact32 = Base64.getEncoder().encodeToString(
                    "01234567890123456789012345678901".getBytes()); // 32 chars

            JwtProperties props = buildProperties(exact32, EXPIRATION_MS);

            // Must not throw
            JwtService service = new JwtService(props);
            assertThat(service.generateToken(userA)).isNotBlank();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Builds a {@link JwtProperties} instance without Spring context or
     * environment variables by setting fields directly via the public setters.
     */
    private static JwtProperties buildProperties(String secret, long expirationMs) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpirationMs(expirationMs);
        return props;
    }
}
