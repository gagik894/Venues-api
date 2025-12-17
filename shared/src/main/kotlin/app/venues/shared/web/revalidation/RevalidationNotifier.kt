package app.venues.shared.web.revalidation

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

/**
 * Port for triggering frontend cache revalidation (On-Demand ISR).
 *
 * Implementations must call the frontend revalidate endpoint with the provided domain and paths.
 */
interface RevalidationNotifier {
    /**
     * Requests revalidation for the given tenant domain and paths.
     *
     * @param domain Public venue domain (host only, without protocol).
     * @param paths List of absolute paths starting with '/'.
     * @param reason Optional reason for logging/metrics.
     */
    fun revalidate(domain: String, paths: List<String>, reason: String? = null)
}

/**
 * Fallback no-op implementation used when no HTTP client is configured.
 */
@Component
@ConditionalOnMissingBean(RevalidationNotifier::class)
class NoOpRevalidationNotifier : RevalidationNotifier {
    private val logger = KotlinLogging.logger {}

    override fun revalidate(domain: String, paths: List<String>, reason: String?) {
        logger.debug {
            "Skipping frontend revalidation (no-op). domain=$domain, paths=${paths.size}, reason=${reason ?: "n/a"}"
        }
    }
}
