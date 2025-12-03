package app.venues.shared.i18n

import org.springframework.context.i18n.LocaleContextHolder

/**
 * Centralized utility for locale resolution.
 *
 * Provides consistent access to the current request's language preference,
 * derived from the Accept-Language HTTP header via Spring's LocaleContextHolder.
 *
 * This utility ensures:
 * - Single source of truth for locale resolution across all modules
 * - Elimination of duplicate lang query parameters in controllers
 * - Consistent language handling throughout the application
 *
 * Usage:
 * ```kotlin
 * val language = LocaleHelper.currentLanguage() // e.g., "en", "hy", "ru"
 * ```
 *
 * @see LocalizationConfig for Accept-Language header configuration
 */
object LocaleHelper {

    /**
     * Returns the current request's language code from Accept-Language header.
     *
     * The language is resolved by Spring's AcceptHeaderLocaleResolver configured in
     * LocalizationConfig. If no Accept-Language header is present, defaults to "en".
     *
     * @return ISO 639-1 language code (e.g., "en", "hy", "ru")
     */
    fun currentLanguage(): String = LocaleContextHolder.getLocale().language

    /**
     * Returns the current language or null if it matches the default language.
     *
     * Useful when you want to skip translation lookup for the default language.
     *
     * @param defaultLanguage The default language code (defaults to "en")
     * @return Language code or null if current language equals default
     */
    fun currentLanguageOrNull(defaultLanguage: String = "en"): String? {
        val current = currentLanguage()
        return if (current.equals(defaultLanguage, ignoreCase = true)) null else current
    }
}
