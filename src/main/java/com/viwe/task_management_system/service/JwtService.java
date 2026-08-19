package com.viwe.task_management_system.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.viwe.task_management_system.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Creates and validates HMAC-SHA256 signed JSON Web Tokens.
 *
 * <p>Uses Nimbus JOSE+JWT — the same library Spring Security uses
 * internally for its OAuth2 resource server support.
 *
 * <p>The signing secret is read from {@link JwtProperties} which binds
 * to environment variables, never from hard-coded values in source code.
 *
 * <p>Security guarantees:
 * <ul>
 *   <li>Tokens are signed with HS256 (HMAC-SHA256).</li>
 *   <li>The secret is never logged or returned in responses.</li>
 *   <li>Expired, tampered, and unparseable tokens are rejected silently
 *       (returning {@code false}) — the caller decides the HTTP response.</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Generates a signed JWT for the given user.
     *
     * <p>Claims included:
     * <ul>
     *   <li>{@code sub} — the user's email address (unique identifier)</li>
     *   <li>{@code iat} — issued-at timestamp</li>
     *   <li>{@code exp} — expiration timestamp</li>
     * </ul>
     *
     * @param userDetails the authenticated user
     * @return a compact, URL-safe signed JWT string
     * @throws RuntimeException if token creation fails (e.g. invalid secret)
     */
    public String generateToken(UserDetails userDetails) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusMillis(jwtProperties.getExpirationMs());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userDetails.getUsername())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .build();

            JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
            SignedJWT signedJWT = new SignedJWT(header, claims);

            JWSSigner signer = new MACSigner(decodeSecret());
            signedJWT.sign(signer);

            return signedJWT.serialize();

        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    /**
     * Extracts the subject (email) from a valid JWT.
     *
     * @param token the compact JWT string
     * @return the email address stored in the {@code sub} claim
     * @throws RuntimeException if the token cannot be parsed
     */
    public String extractEmail(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            throw new RuntimeException("Failed to parse JWT token", e);
        }
    }

    /**
     * Validates a JWT token against the given user details.
     *
     * <p>Checks:
     * <ol>
     *   <li>The token can be parsed.</li>
     *   <li>The signature is valid.</li>
     *   <li>The token has not expired.</li>
     *   <li>The subject matches the user's username.</li>
     * </ol>
     *
     * @param token       the compact JWT string
     * @param userDetails the user to validate against
     * @return {@code true} if the token is valid, {@code false} otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            JWSVerifier verifier = new MACVerifier(decodeSecret());
            if (!signedJWT.verify(verifier)) {
                return false;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (claims.getExpirationTime().before(new Date())) {
                return false;
            }

            return claims.getSubject().equals(userDetails.getUsername());

        } catch (ParseException | JOSEException e) {
            // Log at debug — invalid tokens are a normal occurrence
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Decodes the Base64-encoded secret from properties into raw bytes.
     * The secret is never held as a field — decoded fresh each call to
     * avoid keeping sensitive material in heap memory longer than necessary.
     */
    private byte[] decodeSecret() {
        return Base64.getDecoder().decode(jwtProperties.getSecret());
    }
}
