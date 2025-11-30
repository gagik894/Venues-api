package app.venues.shared.email

/**
 * Configuration for SMTP email sending.
 * Used for both global and venue-specific email configurations.
 */
data class EmailConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val auth: Boolean = true,
    val startTls: Boolean = true
)
