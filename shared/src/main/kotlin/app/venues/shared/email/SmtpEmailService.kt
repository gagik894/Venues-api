package app.venues.shared.email

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.MimeMessage
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
        logger.info { "Sending global email to $to with subject: $subject" }
        try {
            val message = createMessage(globalMailSender, to, subject, content, isHtml)
            globalMailSender.send(message)
            logger.info { "Global email sent successfully to $to" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send global email to $to" }
            throw e
        }
    }

    override fun sendVenueEmail(config: EmailConfig, to: String, subject: String, content: String, isHtml: Boolean) {
        logger.info { "Sending venue email to $to with subject: $subject using host: ${config.host}" }
        try {
            val sender = createSender(config)
            val message = createMessage(sender, to, subject, content, isHtml)
            // Set From header to match the venue's email if possible, though some SMTP servers enforce the authenticated user
            message.setFrom(config.username)
            sender.send(message)
            logger.info { "Venue email sent successfully to $to" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send venue email to $to" }
            throw e
        }
    }

    private fun createMessage(
        sender: JavaMailSender,
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean
    ): MimeMessage {
        val message = sender.createMimeMessage()
        val helper = MimeMessageHelper(message, "utf-8")
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(content, isHtml)
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
