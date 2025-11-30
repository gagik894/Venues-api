package app.venues.shared.email

import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

/**
 * Service for generating HTML email content using Thymeleaf templates.
 */
@Service
class EmailTemplateService(
    private val templateEngine: SpringTemplateEngine
) {

    fun generateStaffVerificationEmail(name: String, verificationUrl: String): String {
        val context = Context()
        context.setVariable("title", "Verify Your Staff Account")
        context.setVariable("name", name)
        context.setVariable("verificationUrl", verificationUrl)
        return templateEngine.process("email/staff-verification", context)
    }

    fun generateUserVerificationEmail(name: String, verificationUrl: String): String {
        val context = Context()
        context.setVariable("title", "Welcome to Venues!")
        context.setVariable("name", name)
        context.setVariable("verificationUrl", verificationUrl)
        return templateEngine.process("email/user-verification", context)
    }

    fun generatePasswordResetEmail(name: String, resetUrl: String): String {
        val context = Context()
        context.setVariable("title", "Reset Your Password")
        context.setVariable("name", name)
        context.setVariable("resetUrl", resetUrl)
        return templateEngine.process("email/password-reset", context)
    }

    fun generateBookingConfirmationEmail(
        name: String,
        bookingReference: String,
        bookingUrl: String,
        eventTitle: String,
        eventDate: String,
        eventTime: String,
        venueName: String,
        items: List<EmailBookingItem>,
        totalPrice: String
    ): String {
        val context = Context()
        context.setVariable("title", "Booking Confirmation")
        context.setVariable("name", name)
        context.setVariable("bookingReference", bookingReference)
        context.setVariable("bookingUrl", bookingUrl)
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
