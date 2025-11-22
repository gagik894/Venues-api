package app.venues.payment.provider.impl

import app.venues.finance.api.dto.PaymentConfig
import app.venues.payment.domain.Payment
import app.venues.payment.provider.PaymentLinkResult
import app.venues.payment.provider.PaymentProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * Payment Provider implementation for Armenian Cards (ARCA).
 *
 * Makes a server-to-server call to register the transaction.
 */
@Component
class ArcaProvider : PaymentProvider {

    private val logger = KotlinLogging.logger {}
    private val restClient = RestClient.create()

    override val providerId: String = "arca"

    override fun isConfigured(config: PaymentConfig): Boolean = config.arca != null

    override fun generatePaymentLink(payment: Payment, config: PaymentConfig): PaymentLinkResult {
        val arcaConfig = config.arca ?: throw IllegalStateException("Arca config missing")

        val returnUrl = "https://traveler-ynga.onrender.com/payment/success/${payment.bookingId}"

        // Amount in cents/luma
        val amountCents = payment.amount.multiply(java.math.BigDecimal(100)).toBigInteger().toString()

        val params = LinkedMultiValueMap<String, String>()
        params.add("userName", arcaConfig.username)
        params.add("password", arcaConfig.password)
        params.add("amount", amountCents)
        params.add("orderNumber", payment.bookingId.toString())
        params.add("currency", "051") // AMD
        params.add("returnUrl", returnUrl)
        params.add("description", "Payment for booking ${payment.bookingId}")

        logger.debug { "Registering Arca payment for booking ${payment.bookingId}" }

        try {
            val response = restClient.post()
                .uri("https://ipay.arca.am/payment/rest/register.do")
                .body(params) // Form URL Encoded
                .retrieve()
                .body(ArcaResponse::class.java)

            if (response == null || response.errorCode != "0") {
                throw IllegalStateException("Arca registration failed: ${response?.errorMessage}")
            }

            return PaymentLinkResult(
                paymentUrl = response.formUrl,
                gatewayReference = response.orderId,
                method = "GET"
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to register Arca payment" }
            throw IllegalStateException("Payment gateway error", e)
        }
    }

    data class ArcaResponse(
        val orderId: String?,
        val formUrl: String?,
        val errorCode: String?,
        val errorMessage: String?
    )
}
