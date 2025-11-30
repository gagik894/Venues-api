package app.venues.shared.email

/**
 * Service for sending emails.
 * Supports both global system emails and venue-specific white-labeled emails.
 */
interface EmailService {

    /**
     * Send an email using the global system configuration.
     * Use for system notifications, password resets, etc.
     */
    fun sendGlobalEmail(to: String, subject: String, content: String, isHtml: Boolean = false)

    /**
     * Send an email using a specific venue's configuration.
     * Use for ticket delivery, venue-specific notifications, etc.
     */
    fun sendVenueEmail(config: EmailConfig, to: String, subject: String, content: String, isHtml: Boolean = false)
}
