package app.venues.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

/**
 * Main entry point for the Venues API application.
 *
 * This is a modular monolith application with the following structure:
 * - app: Runner module (this file)
 * - shared: Cross-cutting concerns (security, web config, exception handling)
 * - common: Pure Kotlin shared utilities and models
 * - user: User feature module
 * - venue: Venue feature module
 *
 * Component Scanning:
 * Scans all packages under app.venues to discover:
 * - @Configuration classes from shared module
 * - @RestController classes from feature modules
 * - @Service, @Repository, @Component beans from all modules
 */
@SpringBootApplication
@ComponentScan(basePackages = ["app.venues"])
class AppApplication

fun main(args: Array<String>) {
    runApplication<AppApplication>(*args)
}