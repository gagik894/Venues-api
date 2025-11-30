package app.venues.shared.email

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test/email")
class TestEmailController(
    private val emailService: EmailService
) {

    @PostMapping("/global")
    fun sendGlobal(@RequestBody request: TestEmailRequest) {
        emailService.sendGlobalEmail(request.to, request.subject, request.content)
    }

    @PostMapping("/venue")
    fun sendVenue(@RequestBody request: TestVenueEmailRequest) {
        val config = EmailConfig(
            host = request.host,
            port = request.port,
            username = request.username,
            password = request.password
        )
        emailService.sendVenueEmail(config, request.to, request.subject, request.content)
    }
}

data class TestEmailRequest(
    val to: String,
    val subject: String,
    val content: String
)

data class TestVenueEmailRequest(
    val to: String,
    val subject: String,
    val content: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)
