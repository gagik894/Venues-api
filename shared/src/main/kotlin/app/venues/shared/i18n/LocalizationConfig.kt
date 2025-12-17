package app.venues.shared.i18n

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.*

/**
 * Configuration for internationalization (i18n) support.
 *
 * Uses Spring's built-in `AcceptHeaderLocaleResolver` to automatically
 * extract and set locale from the Accept-Language header.
 * Spring handles thread-local storage and cleanup automatically.
 */
@Configuration
class LocalizationConfig {

    /**
     * Configures Spring to use Accept-Language header for locale resolution.
     * Supports: en (English), hy (Armenian), ru (Russian)
     */
    @Bean
    fun localeResolver(): LocaleResolver {
        return AcceptHeaderLocaleResolver().apply {
            supportedLocales = listOf(
                Locale.ENGLISH,
                Locale.forLanguageTag("hy"),
                Locale.forLanguageTag("ru")
            )
            setDefaultLocale(Locale.ENGLISH)
        }
    }

    /**
     * Configures MessageSource for loading translation files.
     */
    @Bean
    fun messageSource(): MessageSource {
        return ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
            setUseCodeAsDefaultMessage(true) // Show message key if translation is missing
        }
    }
}
