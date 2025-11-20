package app.venues.venue.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Type-safe configuration classes for venue settings.
 * These are used directly in the VenueSettings entity with JPA AttributeConverters.
 * Automatically serialized to JSON and encrypted before storage.
 */



/**
 * SMTP configuration for venue email notifications.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SmtpConfig(
    val email: String,
    val password: String,
    val host: String = "smtp.gmail.com",
    val port: Int = 587,
    val tls: Boolean = true
) {
    /**
     * Get masked version for safe logging.
     */
    fun toMasked() = SmtpConfig(email, password.maskSecret(), host, port, tls)
}

/**
 * Utility for masking sensitive data in logs.
 */
fun String.maskSecret(): String {
    return if (this.length <= 4) "****"
    else "${this.take(2)}${"*".repeat(this.length - 4)}${this.takeLast(2)}"
}

