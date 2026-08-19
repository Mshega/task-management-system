package com.viwe.task_management_system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized JWT configuration bound from {@code app.jwt.*} properties.
 *
 * <p>Values are supplied via environment variables:
 * <ul>
 *   <li>{@code APP_JWT_SECRET}  — Base64-encoded HMAC-SHA256 secret (min 256 bits)</li>
 *   <li>{@code APP_JWT_EXPIRATION_MS} — Token lifetime in milliseconds</li>
 * </ul>
 *
 * <p>The secret is never logged, printed, or returned in any response.
 * The default secret in {@code application.properties} is a development
 * placeholder that must be overridden in staging and production via the
 * environment variable.
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * Base64-encoded HMAC-SHA256 signing secret.
     * Must be at least 256 bits (32 bytes) after decoding.
     * Supply via {@code APP_JWT_SECRET} environment variable in production.
     */
    private String secret;

    /**
     * Token expiration time in milliseconds.
     * Default: 86400000 (24 hours).
     * Supply via {@code APP_JWT_EXPIRATION_MS} environment variable if needed.
     */
    private long expirationMs;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
