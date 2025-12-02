package app.venues.shared.email

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import java.util.*

/**
 * Service for generating HTML email content using Thymeleaf templates.
 *
 * Follows SRP by focusing solely on template rendering.
 * Uses explicit locale when provided, or falls back to `LocaleContextHolder`.
 */
@Service
class EmailTemplateService(
    private val templateEngine: SpringTemplateEngine,
    private val messageSource: MessageSource
) {

    /**
     * Creates a Thymeleaf context with the specified locale.
     * Falls back to LocaleContextHolder if no locale provided.
     */
    private fun createContext(locale: Locale? = null): Context {
        val resolvedLocale = locale ?: LocaleContextHolder.getLocale()
        return Context(resolvedLocale)
    }

    fun generateStaffVerificationEmail(name: String, verificationUrl: String, locale: Locale? = null): String {
        val context = createContext(locale)
        context.setVariable("name", name)
        context.setVariable("verificationUrl", verificationUrl)
        return templateEngine.process("email/staff-verification", context)
    }

    fun generateUserVerificationEmail(name: String, verificationUrl: String, locale: Locale? = null): String {
        val context = createContext(locale)
        context.setVariable("name", name)
        context.setVariable("verificationUrl", verificationUrl)
        return templateEngine.process("email/user-verification", context)
    }

    fun generatePasswordResetEmail(name: String, resetUrl: String, locale: Locale? = null): String {
        val context = createContext(locale)
        context.setVariable("name", name)
        context.setVariable("resetUrl", resetUrl)
        return templateEngine.process("email/password-reset", context)
    }

    fun generateBookingConfirmationEmail(
        name: String,
        bookingReference: String,
        eventTitle: String,
        eventDate: String,
        eventTime: String,
        venueName: String,
        items: List<EmailBookingItem>,
        totalPrice: String,
        locale: Locale? = null
    ): String {
        val context = createContext(locale)
        context.setVariable("name", name)
        context.setVariable("bookingReference", bookingReference)
        context.setVariable("eventTitle", eventTitle)
        context.setVariable("eventDate", eventDate)
        context.setVariable("eventTime", eventTime)
        context.setVariable("venueName", venueName)
        context.setVariable("items", items)
        context.setVariable("totalPrice", totalPrice)
        return templateEngine.process("email/booking-confirmation", context)
    }
}

data class EmailBookingItem(
    val description: String,
    val quantity: Int,
    val price: String
)

data class EmailTicket(
    val qrCodeBase64: String,
    val ticketType: String,
    val seatInfoLines: List<String>,  // Each line: zone levels, row, seat/table/GA name
    val ticketNumber: String
)
