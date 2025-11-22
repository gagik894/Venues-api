package app.venues.payment.provider.impl

import app.venues.finance.api.dto.PaymentConfig
import app.venues.payment.domain.Payment
import app.venues.payment.provider.PaymentProvider
import app.venues.payment.provider.dto.PaymentCallbackResult
import app.venues.payment.provider.dto.PaymentLinkResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Payment Provider implementation for Converse Bank.
 *
 * Makes a server-to-server call to register the transaction,
 * then returns the redirect URL provided by the bank.
 */
@Component
class ConverseProvider : PaymentProvider {

    private val logger = KotlinLogging.logger {}
    private val restClient = RestClient.create()

    override val providerId: String = "converse"

    override fun isConfigured(config: PaymentConfig): Boolean = config.converse != null

    override fun generatePaymentLink(payment: Payment, config: PaymentConfig): PaymentLinkResult {
        val converseConfig = config.converse ?: throw IllegalStateException("Converse config missing")

        // TODO: Make return URL configurable via environment or request
        val returnUrl = "https://traveler-ynga.onrender.com/payment/success/${payment.bookingId}"

        val requestBody = mapOf(
            "merchant_id" to converseConfig.merchantId,
            "amount" to payment.amount,
            "returnUrl" to returnUrl,
            "lang" to "en",
            "currency" to "051", // AMD
            "orderNumber" to payment.bookingId.toString(),
            "token" to converseConfig.secretKey
        )

        logger.debug { "Registering Converse payment for booking ${payment.bookingId}" }

        try {
            val response = restClient.post()
                .uri("https://pay.conversebank.am/ecommerce.php?c=register")
                .body(requestBody)
                .retrieve()
                .body(ConverseResponse::class.java)

            if (response == null || response.success != 1) {
                throw IllegalStateException("Converse registration failed: ${response?.error}")
            }

            return PaymentLinkResult(
                paymentUrl = response.content.formUrl,
                gatewayReference = response.content.pxNumber,
                method = "GET" // Redirect user to this URL
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to register Converse payment" }
            throw IllegalStateException("Payment gateway error", e)
        }
    }

    override fun extractPaymentId(params: Map<String, String>): String? {
        // Converse redirects with orderNumber usually, but we don't have a callback API for it.
        return params["orderNumber"]
    }

    override fun handleCallback(params: Map<String, String>, config: PaymentConfig): PaymentCallbackResult {
        // Converse does not support server-to-server callbacks in this integration.
        // The user is redirected to the success page.
        return PaymentCallbackResult.Invalid("Converse does not support callbacks")
    }

    data class ConverseResponse(
        val success: Int,
        val error: String?,
        val content: ConverseContent
    )

    data class ConverseContent(
        val formUrl: String,
        val pxNumber: String
    )
}
