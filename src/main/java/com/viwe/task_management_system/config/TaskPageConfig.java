package com.viwe.task_management_system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for task-list pagination limits.
 *
 * <p>Bound from the {@code app.tasks.*} property namespace. Defaults are
 * applied when properties are absent, so the application starts without any
 * environment variables.
 *
 * <h2>Configurable properties</h2>
 * <ul>
 *   <li>{@code app.tasks.max-page-size} (default {@code 100}) — the maximum
 *       number of tasks a client may request in a single page. Any
 *       {@code size} query parameter above this value is silently capped to
 *       this limit by the service layer.</li>
 *   <li>{@code app.tasks.default-page-size} (default {@code 20}) — the page
 *       size used when the client does not supply a {@code size} parameter.
 *       This value is also used as the {@code @PageableDefault} in the
 *       controller.</li>
 * </ul>
 *
 * <h2>Why cap page size in the service rather than Spring's resolver?</h2>
 * <p>Spring MVC's {@code PageableHandlerMethodArgumentResolver} has its own
 * max-page-size setting, but it is global and not easily configurable per
 * endpoint. Capping in the service layer keeps the limit close to the business
 * logic, makes it testable without a web context, and allows the limit to be
 * read from the same externalized config as other domain settings.
 */
@Component
@ConfigurationProperties(prefix = "app.tasks")
public class TaskPageConfig {

    /**
     * Maximum number of tasks per page.
     * Requests for a larger page are silently capped to this value.
     * Default: 100.
     */
    private int maxPageSize = 100;

    /**
     * Default number of tasks per page when the client omits {@code size}.
     * Also used by the controller's {@code @PageableDefault}.
     * Default: 20.
     */
    private int defaultPageSize = 20;

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }
}
