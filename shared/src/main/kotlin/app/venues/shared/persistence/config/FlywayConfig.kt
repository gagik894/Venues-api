/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Flyway configuration for database migrations.
 */

package app.venues.shared.persistence.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Flyway migration configuration.
 *
 * This configuration provides custom migration strategies for different environments:
 * - Development: Repairs checksum mismatches automatically
 * - Production: Strict validation, no auto-repair
 *
 * Flyway Repair:
 * The repair operation is used to fix the Flyway schema history table when:
 * - A migration file was modified after being applied
 * - Checksums don't match between database and local files
 * - Manual corrections were made to the schema history
 *
 * WARNING: In production, migrations should NEVER be modified after being applied.
 * Always create new migrations for schema changes.
 */
@Configuration
class FlywayConfig {

    private val logger = KotlinLogging.logger {}

    /**
     * Development migration strategy with automatic repair.
     *
     * This strategy:
     * 1. Repairs the schema history table (fixes checksum mismatches)
     * 2. Validates migrations
     * 3. Runs pending migrations
     *
     * Use this profile during development when you need to modify migrations.
     * NEVER use this in production.
     */
    @Bean
    @Profile("dev", "local", "default")
    fun flywayMigrationStrategyDevelopment(): FlywayMigrationStrategy {
        logger.info { "Configuring Flyway migration strategy for DEVELOPMENT environment" }
        logger.warn { "Auto-repair is ENABLED - migration checksums will be automatically repaired" }

        return FlywayMigrationStrategy { flyway ->
            try {
                // Repair the schema history table to fix checksum mismatches
                logger.info { "Running Flyway repair to fix checksum mismatches..." }
                flyway.repair()
                logger.info { "Flyway repair completed successfully" }

                // Validate migrations
                logger.info { "Validating migrations..." }
                flyway.validate()
                logger.info { "Migration validation successful" }

                // Run migrations
                logger.info { "Running pending migrations..." }
                val migrationsApplied = flyway.migrate()
                logger.info { "Applied ${migrationsApplied.migrationsExecuted} migration(s)" }

            } catch (e: Exception) {
                logger.error(e) { "Flyway migration failed: ${e.message}" }
                throw e
            }
        }
    }

    /**
     * Production migration strategy with strict validation.
     *
     * This strategy:
     * 1. Validates migrations (fails if checksums don't match)
     * 2. Runs pending migrations
     * 3. NO auto-repair
     *
     * Use this profile in production environments.
     * If validation fails, you must manually run 'flyway:repair' after investigating the issue.
     */
    @Bean
    @Profile("prod", "production")
    fun flywayMigrationStrategyProduction(): FlywayMigrationStrategy {
        logger.info { "Configuring Flyway migration strategy for PRODUCTION environment" }
        logger.info { "Auto-repair is DISABLED - strict validation enforced" }

        return FlywayMigrationStrategy { flyway ->
            try {
                // Validate migrations (will fail if checksums don't match)
                logger.info { "Validating migrations..." }
                flyway.validate()
                logger.info { "Migration validation successful" }

                // Run migrations
                logger.info { "Running pending migrations..." }
                val migrationsApplied = flyway.migrate()
                logger.info { "Applied ${migrationsApplied.migrationsExecuted} migration(s)" }

            } catch (e: Exception) {
                logger.error(e) { "Flyway migration failed: ${e.message}" }
                logger.error { "If this is a checksum mismatch, you must manually run 'flyway:repair'" }
                logger.error { "DO NOT modify migrations that have been applied to production!" }
                throw e
            }
        }
    }
}

