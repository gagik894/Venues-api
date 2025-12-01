package app.venues.shared.email

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.MimeMessage
import jakarta.mail.util.ByteArrayDataSource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.util.*

@Service
class SmtpEmailService(
    private val globalMailSender: JavaMailSender
) : EmailService {

    private val logger = KotlinLogging.logger {}

    override fun sendGlobalEmail(to: String, subject: String, content: String, isHtml: Boolean) {
        sendGlobalEmailWithAttachments(to, subject, content, isHtml, emptyList())
    }

    override fun sendVenueEmail(config: EmailConfig, to: String, subject: String, content: String, isHtml: Boolean) {
        sendVenueEmailWithAttachments(config, to, subject, content, isHtml, emptyList())
    }

    override fun sendGlobalEmailWithAttachments(
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean,
        attachments: List<EmailAttachment>
    ) {
        logger.info { "Sending global email to $to with subject: $subject (${attachments.size} attachments)" }
        try {
            val message = createMessageWithAttachments(globalMailSender, to, subject, content, isHtml, attachments)
            globalMailSender.send(message)
            logger.info { "Global email sent successfully to $to" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send global email to $to" }
            throw e
        }
    }

    override fun sendVenueEmailWithAttachments(
        config: EmailConfig,
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean,
        attachments: List<EmailAttachment>
    ) {
        logger.info { "Sending venue email to $to with subject: $subject using host: ${config.host} (${attachments.size} attachments)" }
        try {
            val sender = createSender(config)
            val message = createMessageWithAttachments(sender, to, subject, content, isHtml, attachments)
            message.setFrom(config.username)
            sender.send(message)
            logger.info { "Venue email sent successfully to $to" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send venue email to $to" }
            throw e
        }
    }

    private fun createMessageWithAttachments(
        sender: JavaMailSender,
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean,
        attachments: List<EmailAttachment>
    ): MimeMessage {
        val message = sender.createMimeMessage()

        if (attachments.isEmpty()) {
            // Simple message without attachments
            val helper = MimeMessageHelper(message, "utf-8")
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(content, isHtml)
        } else {
            // Multipart message with attachments
            val helper = MimeMessageHelper(message, true, "utf-8")
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(content, isHtml)

            // Add attachments
            attachments.forEach { attachment ->
                val dataSource = ByteArrayDataSource(attachment.data, attachment.contentType)
                helper.addAttachment(attachment.filename, dataSource)
            }
        }
        
        return message
    }

    private fun createSender(config: EmailConfig): JavaMailSenderImpl {
        val sender = JavaMailSenderImpl()
        sender.host = config.host
        sender.port = config.port
        sender.username = config.username
        sender.password = config.password

        val props: Properties = sender.javaMailProperties
        props["mail.transport.protocol"] = "smtp"
        props["mail.smtp.auth"] = config.auth.toString()
        props["mail.smtp.starttls.enable"] = config.startTls.toString()
        // Add timeouts to prevent hanging
        props["mail.smtp.connectiontimeout"] = "5000"
        props["mail.smtp.timeout"] = "3000"
        props["mail.smtp.writetimeout"] = "5000"

        return sender
    }
}
