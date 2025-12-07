package app.venues.app.revalidation

import app.venues.app.config.FrontendRevalidationProperties
import app.venues.shared.web.revalidation.RevalidationNotifier
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * HTTP client for triggering frontend ISR revalidation.
 */
@Component
@ConditionalOnProperty(prefix = "frontend.revalidation", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class HttpRevalidationNotifier(
    private val properties: FrontendRevalidationProperties,
    @Qualifier("revalidationWebClient") private val webClient: WebClient
) : RevalidationNotifier {

    private val logger = KotlinLogging.logger {}

    override fun revalidate(domain: String, paths: List<String>, reason: String?) {
        if (!properties.isConfigured()) {
            logger.debug { "Skipping revalidation: secret not configured" }
            return
        }

        val host = canonicalizeDomain(domain)
        if (host == null) {
            logger.warn { "Skipping revalidation: invalid domain '$domain'" }
            return
        }

        val sanitizedPaths = paths
            .map { it.trim() }
            .filter { it.startsWith("/") && it.length > 1 }
            .distinct()

        if (sanitizedPaths.isEmpty()) {
            logger.debug { "Skipping revalidation for domain=$host because no paths provided" }
            return
        }

        val url = "${properties.scheme}://$host/api/revalidate"
        val payload = mapOf(
            "secret" to properties.secret!!.trim(),
            "paths" to sanitizedPaths
        )

        try {
            webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block(properties.timeout)

            logger.info {
                "Requested frontend revalidation: domain=$host, paths=${sanitizedPaths.size}, reason=${reason ?: "n/a"}"
            }
        } catch (ex: WebClientResponseException) {
            logger.warn {
                "Frontend revalidation failed: domain=$host status=${ex.statusCode.value()} reason=${reason ?: "n/a"}"
            }
        } catch (ex: Exception) {
            logger.warn(ex) { "Frontend revalidation error for domain=$host reason=${reason ?: "n/a"}" }
        }
    }

    private fun canonicalizeDomain(raw: String): String? {
        if (raw.isBlank()) return null

        val normalized = raw.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")

        val hostOnly = normalized.substringBefore("/").substringBefore(":").trimEnd('.')
        if (hostOnly.isBlank()) return null

        return if (HOST_REGEX.matches(hostOnly)) hostOnly else null
    }

    companion object {
        private val HOST_REGEX = Regex("^[a-z0-9.-]{1,253}$")
    }
}
