package com.taskmanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita la auditoría JPA (@CreatedDate/@LastModifiedDate).
 *
 * Vive en una {@code @Configuration} separada y no sobre {@link com.taskmanager.TaskManagerApplication}
 * para que los test slices de Spring ({@code @WebMvcTest}) no intenten crear el
 * {@code jpaAuditingHandler} sin el metamodelo JPA (que esos slices excluyen).
 * En la aplicación completa se descubre por component scan.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
