package app.venues.shared.i18n

import java.util.*

/**
 * Thread-local holder for the current request's locale.
 *
 * This provides a clean way to access the locale throughout the application
 * without passing it through every method signature.
 *
 * The locale is typically set by `LocaleInterceptor` at the start of each request
 * and cleared at the end to prevent memory leaks.
 */
object LocaleContextHolder {
    private val localeHolder = ThreadLocal<Locale>()

    /**
     * Sets the locale for the current thread.
     */
    fun setLocale(locale: Locale) {
        localeHolder.set(locale)
    }

    /**
     * Gets the locale for the current thread.
     * Returns the default locale (English) if none is set.
     */
    fun getLocale(): Locale {
        return localeHolder.get() ?: Locale.ENGLISH
    }

    /**
     * Clears the locale for the current thread.
     * MUST be called at the end of each request to prevent memory leaks.
     */
    fun clear() {
        localeHolder.remove()
    }
}
