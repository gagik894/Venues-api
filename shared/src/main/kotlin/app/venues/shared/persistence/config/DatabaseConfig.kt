package app.venues.shared.persistence.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * JPA and database configuration for the Venues API.
 *
 * This configuration enables:
 * - JPA Auditing: Automatic tracking of entity creation/modification timestamps and users
 * - JPA Repositories: Spring Data JPA repository support across all modules
 * - Transaction Management: Declarative transaction management with @Transactional
 *
 * JPA Auditing Configuration:
 * With @EnableJpaAuditing, entities can use:
 * - @CreatedDate: Automatically sets creation timestamp
 * - @LastModifiedDate: Automatically updates modification timestamp
 * - @CreatedBy: Tracks who created the entity (requires AuditorAware bean)
 * - @LastModifiedBy: Tracks who last modified the entity (requires AuditorAware bean)
 *
 * Repository Scanning:
 * Scans for @Repository interfaces in app.venues package and all subpackages,
 * including domain modules (user, venue, etc.).
 *
 * Transaction Management:
 * Enables @Transactional annotation for declarative transaction boundaries.
 * Transactions are automatically committed on success or rolled back on exceptions.
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(
    basePackages = ["app.venues"]
)
@EnableTransactionManagement
class DatabaseConfig {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Database configuration initialized" }
        logger.info { "JPA Auditing enabled for automatic timestamp tracking" }
        logger.info { "JPA Repositories enabled with base package: app.venues" }
        logger.info { "Transaction Management enabled" }
    }

    /**
     * TODO: Implement AuditorAware bean for tracking created/modified users
     *
     * Example implementation:
     * @Bean
     * fun auditorAware(): AuditorAware<String> {
     *     return AuditorAware {
     *         Optional.of(
     *             SecurityContextHolder.getContext()
     *                 .authentication
     *                 ?.name
     *                 ?: "system"
     *         )
     *     }
     * }
     */
}

