package app.venues.shared.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Application configuration for scheduled tasks.
 *
 * Enables Spring's scheduled task execution capability.
 */
@Configuration
@EnableScheduling
class SchedulingConfig