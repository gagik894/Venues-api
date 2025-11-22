package app.venues.payment.provider.impl

import app.venues.finance.api.dto.PaymentConfig
import app.venues.payment.domain.Payment
import app.venues.payment.provider.PaymentProvider
import app.venues.payment.provider.dto.PaymentCallbackResult
import app.venues.payment.provider.dto.PaymentLinkResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.math.BigDecimal

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
        val amountCents = payment.amount.multiply(BigDecimal(100)).toBigInteger().toString()

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

    override fun extractPaymentId(params: Map<String, String>): String? {
        return params["orderNumber"]
    }

    override fun handleCallback(params: Map<String, String>, config: PaymentConfig): PaymentCallbackResult {
        val arcaConfig = config.arca ?: return PaymentCallbackResult.Invalid("Arca config missing")
        val orderNumber = params["orderNumber"] ?: return PaymentCallbackResult.Invalid("Missing orderNumber")

        try {
            val queryParams = LinkedMultiValueMap<String, String>()
            queryParams.add("userName", arcaConfig.username)
            queryParams.add("password", arcaConfig.password)
            queryParams.add("orderNumber", orderNumber)

            val response = restClient.get()
                .uri(
                    "https://ipay.arca.am/payment/rest/getOrderStatusExtended.do?userName={u}&password={p}&orderNumber={o}",
                    arcaConfig.username, arcaConfig.password, orderNumber
                )
                .retrieve()
                .body(ArcaStatusResponse::class.java)

            if (response == null || response.orderStatus == null) {
                return PaymentCallbackResult.Invalid("Invalid response from Arca")
            }

            return when (response.orderStatus) {
                2 -> PaymentCallbackResult.Success(
                    paymentId = orderNumber,
                    externalId = null, // Arca doesn't return a separate transaction ID here usually
                    amount = response.amount?.let { BigDecimal(it).divide(BigDecimal(100)) }
                )

                6, 7, 8 -> PaymentCallbackResult.Failure(
                    paymentId = orderNumber,
                    reason = "Arca status: ${response.orderStatus} (${response.errorMessage})"
                )

                else -> PaymentCallbackResult.Invalid("Arca status: ${response.orderStatus} (Pending or Unknown)")
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to verify Arca payment" }
            return PaymentCallbackResult.Invalid("Verification failed: ${e.message}")
        }
    }

    data class ArcaResponse(
        val orderId: String?,
        val formUrl: String?,
        val errorCode: String?,
        val errorMessage: String?
    )

    data class ArcaStatusResponse(
        val orderStatus: Int?,
        val errorCode: String?,
        val errorMessage: String?,
        val amount: Long?,
        val currency: String?
    )
}
