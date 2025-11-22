package app.venues.payment.provider

import app.venues.finance.api.dto.PaymentConfig
import app.venues.payment.domain.Payment
import app.venues.payment.provider.dto.PaymentCallbackResult
import app.venues.payment.provider.dto.PaymentLinkResult

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

    /**
     * Extracts the internal payment ID (booking ID) from the callback parameters.
     * This is needed to load the payment record before processing the callback.
     *
     * @param params The parameters received in the callback.
     * @return The payment ID string, or null if not found.
     */
    fun extractPaymentId(params: Map<String, String>): String?

    /**
     * Processes a callback or redirect from the payment provider.
     *
     * @param params The parameters received in the callback (query params or body).
     * @param config The merchant's payment configuration.
     * @return The result of the callback processing.
     */
    fun handleCallback(params: Map<String, String>, config: PaymentConfig): PaymentCallbackResult
}
