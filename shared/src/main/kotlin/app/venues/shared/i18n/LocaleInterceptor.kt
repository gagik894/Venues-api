package app.venues.shared.i18n

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.*

/**
 * Interceptor that extracts the `Accept-Language` header and stores it in `LocaleContextHolder`.
 *
 * This follows the Single Responsibility Principle - it only handles locale extraction.
 * The locale is then available throughout the request lifecycle via `LocaleContextHolder`.
 *
 * Supported languages: en (English), hy (Armenian), ru (Russian)
 * Default: en (English)
 */
@Component
class LocaleInterceptor : HandlerInterceptor {
    private val logger = KotlinLogging.logger {}

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "hy", "ru")
        private const val DEFAULT_LANGUAGE = "en"
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val acceptLanguage = request.getHeader("Accept-Language")
        val locale = parseLocale(acceptLanguage)

        LocaleContextHolder.setLocale(locale)
        logger.debug { "Request locale set to: ${locale.language}" }

        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        LocaleContextHolder.clear()
        logger.debug { "Locale context cleared" }
    }

    /**
     * Parses the Accept-Language header and returns the best matching supported locale.
     *
     * Examples:
     * - "hy" -> Armenian
     * - "hy-AM" -> Armenian
     * - "ru,en;q=0.9" -> Russian
     * - "fr,en;q=0.9" -> English (fallback)
     * - null -> English (default)
     */
    private fun parseLocale(acceptLanguage: String?): Locale {
        if (acceptLanguage.isNullOrBlank()) {
            return Locale(DEFAULT_LANGUAGE)
        }

        // Parse Accept-Language header (e.g., "hy-AM,hy;q=0.9,en;q=0.8")
        val languages = acceptLanguage
            .split(",")
            .map { it.trim().split(";").first().trim() }
            .mapNotNull { languageTag ->
                // Extract language code (e.g., "hy" from "hy-AM")
                val code = languageTag.split("-", "_").first().lowercase()
                if (code in SUPPORTED_LANGUAGES) code else null
            }

        val languageCode = languages.firstOrNull() ?: DEFAULT_LANGUAGE
        return Locale(languageCode)
    }
}
