package app.venues.payment.provider.impl

import app.venues.finance.api.dto.PaymentConfig
import app.venues.payment.domain.Payment
import app.venues.payment.provider.PaymentLinkResult
import app.venues.payment.provider.PaymentProvider
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.*

/**
 * Payment Provider implementation for Telcell.
 *
 * Logic based on official Telcell documentation:
 * - Generates MD5 checksum of parameters.
 * - Returns form data for POST request to Telcell.
 */
@Component
class TelcellProvider : PaymentProvider {

    override val providerId: String = "telcel"

    override fun isConfigured(config: PaymentConfig): Boolean = config.telcel != null

    override fun generatePaymentLink(payment: Payment, config: PaymentConfig): PaymentLinkResult {
        val telcelConfig = config.telcel ?: throw IllegalStateException("Telcell config missing")

        val storeKey = telcelConfig.storeKey
        val postponeBillIssuer = telcelConfig.postponeBillIssuer ?: ""

        // Telcell requires specific formatting
        val amount = payment.amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
        val currency = "51" // AMD code for Telcell
        val descriptionText = "Payment for order ${payment.bookingId}"
        val issuerId = payment.bookingId.toString()
        val validDays = "1"

        // Base64 encoding
        val cash = Base64.getEncoder().encodeToString("Event ticket payment".toByteArray())
        val description = Base64.getEncoder().encodeToString(descriptionText.toByteArray())
        val issuerIdBase64 = Base64.getEncoder().encodeToString(issuerId.toByteArray())

        // Checksum generation order:
        // storeKey + postponeBillIssuer + cash + currency + sum + description + validDays + issuerIdBase64
        val checksumString = StringBuilder()
            .append(storeKey)
            .append(postponeBillIssuer)
            .append(cash)
            .append(currency)
            .append(amount)
            .append(description)
            .append(validDays)
            .append(issuerIdBase64)
            .toString()

        val checksum = md5(checksumString)

        val formData = mapOf(
            "description" to description,
            "sum" to amount,
            "currency" to currency,
            "issuer_id" to issuerIdBase64,
            "valid_days" to validDays,
            "cash" to cash,
            "checksum" to checksum,
            "postpone_bill:issuer" to postponeBillIssuer
        )

        return PaymentLinkResult(
            paymentUrl = "https://telcellmoney.am/invoices",
            formData = formData,
            method = "POST"
        )
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
