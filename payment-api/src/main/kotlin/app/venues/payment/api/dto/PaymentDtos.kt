package app.venues.payment.api.dto

import java.math.BigDecimal
import java.util.*

/**
 * Request DTO for initiating a new payment.
 *
 * @property bookingId The ID of the booking to pay for.
 * @property venueId The ID of the venue (required for merchant routing).
 * @property amount The amount to charge.
 * @property currency The currency code.
 */
data class InitiatePaymentRequest(
    val bookingId: UUID,
    val venueId: UUID,
    val amount: BigDecimal,
    val currency: String
)

/**
 * Response DTO for a payment initiation.
 *
 * @property paymentId The internal ID of the created payment record.
 * @property status The initial status of the payment.
 * @property paymentUrl The URL to redirect the user to for payment (if applicable).
 * @property gatewayReference The reference ID from the payment gateway.
 */
data class InitiatePaymentResponse(
    val paymentId: UUID,
    val status: String,
    val paymentUrl: String?,
    val gatewayReference: String?
)
