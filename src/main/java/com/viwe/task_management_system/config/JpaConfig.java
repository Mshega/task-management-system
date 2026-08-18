package com.viwe.task_management_system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration for the Task Management System.
 *
 * <p>Enables JPA auditing so that {@code @CreatedDate} and
 * {@code @LastModifiedDate} annotations on entities are populated
 * automatically by Spring Data, without any manual timestamp management
 * in service or repository code.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
