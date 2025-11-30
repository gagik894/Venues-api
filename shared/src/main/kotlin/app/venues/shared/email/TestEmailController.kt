package app.venues.shared.email

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("api/test/email")
class TestEmailController(
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService
) {

    @PostMapping("/send-global")
    fun sendGlobal(@RequestParam to: String) {
        emailService.sendGlobalEmail(to, "Test Global Email", "This is a test email from global config.")
    }

    @PostMapping("/send-venue")
    fun sendVenue(@RequestParam to: String, @RequestBody config: EmailConfig) {
        emailService.sendVenueEmail(config, to, "Test Venue Email", "This is a test email from venue config.")
    }

    @PostMapping("/preview/staff-verification")
    fun previewStaffVerification(@RequestParam name: String, @RequestParam url: String): String {
        return emailTemplateService.generateStaffVerificationEmail(name, url)
    }

    @PostMapping("/preview/user-verification")
    fun previewUserVerification(@RequestParam name: String, @RequestParam url: String): String {
        return emailTemplateService.generateUserVerificationEmail(name, url)
    }

    @PostMapping("/preview/booking-confirmation")
    fun previewBookingConfirmation(): String {
        return emailTemplateService.generateBookingConfirmationEmail(
            name = "John Doe",
            bookingReference = "REF123456",
            bookingUrl = "https://venues.app/bookings/123",
            eventTitle = "Grand Concert",
            eventDate = "2025-12-25",
            eventTime = "19:00",
            venueName = "Opera House",
            items = listOf(
                EmailBookingItem("VIP Ticket", 2, "50.00"),
                EmailBookingItem("Regular Ticket", 1, "30.00")
            ),
            totalPrice = "130.00"
        )
    }

    @PostMapping("/send-template/booking-confirmation")
    fun sendBookingConfirmationTemplate(@RequestParam to: String) {
        val content = emailTemplateService.generateBookingConfirmationEmail(
            name = "John Doe",
            bookingReference = "REF123456",
            bookingUrl = "https://venues.app/bookings/123",
            eventTitle = "Grand Concert",
            eventDate = "2025-12-25",
            eventTime = "19:00",
            venueName = "Opera House",
            items = listOf(
                EmailBookingItem("VIP Ticket", 2, "50.00"),
                EmailBookingItem("Regular Ticket", 1, "30.00")
            ),
            totalPrice = "130.00"
        )
        emailService.sendGlobalEmail(to, "Booking Confirmation Test", content, isHtml = true)
    }
}
