package app.venues.venue.api.dto

data class SmtpConfigDto(
    val email: String,
    val password: String,
    val host: String,
    val port: Int,
    val tls: Boolean
)
