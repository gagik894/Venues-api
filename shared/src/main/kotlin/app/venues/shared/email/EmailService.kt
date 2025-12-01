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

    /**
     * Send an email with attachments using the global system configuration.
     */
    fun sendGlobalEmailWithAttachments(
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean = false,
        attachments: List<EmailAttachment> = emptyList()
    )

    /**
     * Send an email with attachments using a specific venue's configuration.
     */
    fun sendVenueEmailWithAttachments(
        config: EmailConfig,
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean = false,
        attachments: List<EmailAttachment> = emptyList()
    )
}

/**
 * Email attachment data.
 */
data class EmailAttachment(
    val filename: String,
    val contentType: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmailAttachment
        return filename == other.filename && contentType == other.contentType && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
