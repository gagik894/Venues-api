package app.venues.payment.provider.dto

import java.math.BigDecimal

/**
 * Represents the result of processing a payment provider callback.
 */
sealed class PaymentCallbackResult {

    /**
     * The payment was successfully verified.
     * @property paymentId The internal payment/booking ID extracted from the callback.
     * @property externalId The provider's transaction ID.
     * @property amount The amount confirmed by the provider.
     */
    data class Success(
        val paymentId: String,
        val externalId: String?,
        val amount: BigDecimal?
    ) : PaymentCallbackResult()

    /**
     * The payment failed or was rejected.
     * @property paymentId The internal payment/booking ID extracted from the callback.
     * @property reason The reason for failure.
     */
    data class Failure(
        val paymentId: String,
        val reason: String
    ) : PaymentCallbackResult()

    /**
     * The provider requires an immediate response (e.g., Idram Precheck).
     * The service should return this payload to the caller.
     * @property payload The response body to send back to the provider.
     * @property statusCode The HTTP status code to return.
     */
    data class ResponseRequired(
        val payload: Any,
        val statusCode: Int = 200
    ) : PaymentCallbackResult()

    /**
     * The callback is invalid or irrelevant.
     */
    data class Invalid(val reason: String) : PaymentCallbackResult()
}
