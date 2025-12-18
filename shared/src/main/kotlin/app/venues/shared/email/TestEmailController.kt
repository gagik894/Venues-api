package app.venues.shared.email

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.shared.money.toMoney
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("api/test/email")
class TestEmailController(
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService
) {

    @PostMapping("/send-global")
    @Auditable(action = "TEST_EMAIL_GLOBAL", subjectType = "test_email", includeVenueId = false)
    fun sendGlobal(@RequestParam to: String) {
        emailService.sendGlobalEmail(to, "Test Global Email", "This is a test email from global config.")
    }

    @PostMapping("/send-venue")
    @Auditable(action = "TEST_EMAIL_VENUE", subjectType = "test_email", includeVenueId = false)
    fun sendVenue(@RequestParam to: String, @AuditMetadata("config") @RequestBody config: EmailConfig) {
        emailService.sendVenueEmail(config, to, "Test Venue Email", "This is a test email from venue config.")
    }

    @PostMapping("/preview/staff-verification")
    @Auditable(action = "TEST_EMAIL_PREVIEW_STAFF_VERIFICATION", subjectType = "test_email", includeVenueId = false)
    fun previewStaffVerification(@RequestParam name: String, @RequestParam url: String): String {
        return emailTemplateService.generateStaffVerificationEmail(name, url)
    }

    @PostMapping("/preview/user-verification")
    @Auditable(action = "TEST_EMAIL_PREVIEW_USER_VERIFICATION", subjectType = "test_email", includeVenueId = false)
    fun previewUserVerification(@RequestParam name: String, @RequestParam url: String): String {
        return emailTemplateService.generateUserVerificationEmail(name, url)
    }

    @PostMapping("/preview/booking-confirmation")
    @Auditable(action = "TEST_EMAIL_PREVIEW_BOOKING", subjectType = "test_email", includeVenueId = false)
    fun previewBookingConfirmation(): String {
        return emailTemplateService.generateBookingConfirmationEmail(
            name = "John Doe",
            bookingReference = "REF123456",
            eventTitle = "Grand Concert",
            eventDate = "2025-12-25",
            eventTime = "19:00",
            venueName = "Opera House",
            items = listOf(
                EmailBookingItem("VIP Ticket", 2, BigDecimal("50.00").toMoney("USD")),
                EmailBookingItem("Regular Ticket", 1, BigDecimal("30.00").toMoney("USD"))
            ),
            totalPrice = BigDecimal("130.00").toMoney("USD")
        )
    }

    @PostMapping("/send-template/booking-confirmation")
    @Auditable(action = "TEST_EMAIL_SEND_BOOKING", subjectType = "test_email", includeVenueId = false)
    fun sendBookingConfirmationTemplate(@RequestParam to: String) {
        val content = emailTemplateService.generateBookingConfirmationEmail(
            name = "John Doe",
            bookingReference = "REF123456",
            eventTitle = "Grand Concert",
            eventDate = "2025-12-25",
            eventTime = "19:00",
            venueName = "Opera House",
            items = listOf(
                EmailBookingItem("VIP Ticket", 2, BigDecimal("50.00").toMoney("USD")),
                EmailBookingItem("Regular Ticket", 1, BigDecimal("30.00").toMoney("USD"))
            ),
            totalPrice = BigDecimal("130.00").toMoney("USD")
        )
        emailService.sendGlobalEmail(to, "Booking Confirmation Test", content, isHtml = true)
    }
}
