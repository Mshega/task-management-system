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
 * to the {@code APP_JWT_SECRET} environment variable. It is never stored
 * in source control.
 *
 * <p>Security guarantees:
 * <ul>
 *   <li>Tokens are signed with HS256 (HMAC-SHA256).</li>
 *   <li>The secret is never logged or returned in responses.</li>
 *   <li>Expired, tampered, and unparseable tokens are rejected
 *       (returning {@code false}) — the filter maps that to HTTP 401.</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] secret = decodeSecret();
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least " + MIN_SECRET_BYTES
                            + " bytes after Base64 decoding");
        }
    }

    /**
     * Generates a signed JWT for the given user using the configured lifetime.
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
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getExpirationMs());
        return generateToken(userDetails, now, expiry);
    }

    /**
     * Generates a signed JWT with an explicit expiry. Used by tests to produce
     * expired tokens without waiting for the configured lifetime.
     *
     * @param userDetails the authenticated user
     * @param expiry      token expiration instant
     * @return a compact, URL-safe signed JWT string
     */
    public String generateToken(UserDetails userDetails, Instant expiry) {
        return generateToken(userDetails, Instant.now(), expiry);
    }

    /**
     * Extracts the subject (email) from a JWT without validating the signature.
     * Callers that authenticate a request must also call
     * {@link #isTokenValid(String, UserDetails)}.
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

            if (claims.getExpirationTime() == null
                    || claims.getExpirationTime().before(new Date())) {
                return false;
            }

            return claims.getSubject().equals(userDetails.getUsername());

        } catch (ParseException | JOSEException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    private String generateToken(UserDetails userDetails, Instant issuedAt, Instant expiry) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userDetails.getUsername())
                    .issueTime(Date.from(issuedAt))
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
     * Decodes the Base64-encoded secret from properties into raw bytes.
     * The secret is never held as a field — decoded fresh each call to
     * avoid keeping sensitive material in heap memory longer than necessary.
     */
    private byte[] decodeSecret() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set the APP_JWT_SECRET environment variable.");
        }
        return Base64.getDecoder().decode(secret);
    }
}
