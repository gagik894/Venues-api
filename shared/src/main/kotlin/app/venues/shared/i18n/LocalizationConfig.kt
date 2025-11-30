package app.venues.shared.i18n

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Configuration for internationalization (i18n) support.
 *
 * Responsibilities:
 * 1. Registers the `LocaleInterceptor` to capture Accept-Language headers
 * 2. Configures the `MessageSource` for loading translation files
 */
@Configuration
class LocalizationConfig(
    private val localeInterceptor: LocaleInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(localeInterceptor)
    }

    @Bean
    fun messageSource(): MessageSource {
        return ResourceBundleMessageSource().apply {
            setBasename("messages")
            defaultEncoding = "UTF-8"
            isFallbackToSystemLocale = false
            isUseCodeAsDefaultMessage = true // Show message key if translation is missing
        }
    }
}
