package app.venues.shared.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

/**
 * AOP configuration for aspect-based cross-cutting concerns.
 *
 * Enables AspectJ-based proxying for annotations like @Auditable.
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class AopConfig
