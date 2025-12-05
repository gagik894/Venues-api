package app.venues.app.context

import app.venues.shared.web.context.DomainChangedEvent
import app.venues.shared.web.context.DomainResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens for domain change events and invalidates the domain resolution cache.
 */
@Component
class DomainResolverInvalidationListener(
    private val domainResolver: DomainResolver
) {
    private val logger = KotlinLogging.logger {}

    @EventListener
    fun onDomainChanged(event: DomainChangedEvent) {
        if (event.oldDomain != null) {
            domainResolver.invalidate(event.oldDomain!!)
            logger.debug { "Invalidated old domain cache: ${event.oldDomain}" }
        }
        if (event.newDomain != null) {
            domainResolver.invalidate(event.newDomain!!)
            logger.debug { "Invalidated new domain cache: ${event.newDomain}" }
        }
    }
}
