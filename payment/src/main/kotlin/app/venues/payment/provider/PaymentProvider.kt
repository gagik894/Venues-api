package app.venues.payment.provider

import app.venues.finance.api.dto.PaymentConfig
import app.venues.payment.domain.Payment

/**
 * Strategy interface for Payment Providers.
 *
 * Each implementation handles the specific logic for a payment gateway
 * (e.g., Telcell, Idram, Stripe).
 */
interface PaymentProvider {

    /**
     * The unique identifier of the provider (e.g., "telcel", "idram").
     */
    val providerId: String

    /**
     * Checks if this provider is configured in the given config.
     */
    fun isConfigured(config: PaymentConfig): Boolean

    /**
     * Generates the payment link or form data for the given payment.
     *
     * @param payment The payment record.
     * @param config The merchant's payment configuration.
     * @return A result containing the URL and/or form data.
     */
    fun generatePaymentLink(payment: Payment, config: PaymentConfig): PaymentLinkResult
}

data class PaymentLinkResult(
    val paymentUrl: String?,
    val formData: Map<String, String>? = null,
    val method: String = "GET", // GET (redirect) or POST (form submit)
    val gatewayReference: String? = null
)
