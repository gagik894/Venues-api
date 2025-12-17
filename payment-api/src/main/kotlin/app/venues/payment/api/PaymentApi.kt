package app.venues.payment.api

import app.venues.payment.api.dto.InitiatePaymentRequest
import app.venues.payment.api.dto.InitiatePaymentResponse
import java.util.*

/**
 * Public API for Payment operations.
 *
 * This interface defines the contract for interacting with the Payment module.
 * It follows the Port pattern in Hexagonal Architecture.
 */
interface PaymentApi {

    /**
     * Retrieves the current status of a payment.
     *
     * @param paymentId The unique identifier of the payment.
     * @return The status string (e.g., "PENDING", "COMPLETED").
     */
    fun getPaymentStatus(paymentId: UUID): String

    /**
     * Initiates a new payment transaction.
     *
     * This method will:
     * 1. Resolve the correct merchant account based on the venue.
     * 2. Create a payment record in the database.
     * 3. Generate a payment link or intent with the payment provider.
     *
     * @param request The payment initiation details.
     * @return The response containing the payment ID and redirect URL.
     */
    fun initiatePayment(request: InitiatePaymentRequest): InitiatePaymentResponse

    /**
     * Processes a callback from a payment provider.
     *
     * @param providerId The ID of the provider (e.g., "telcel", "idram").
     * @param params The parameters received in the callback.
     * @return The result payload (if any) to be returned to the provider.
     */
    fun processCallback(providerId: String, params: Map<String, String>): Any

    /**
     * Refunds a completed payment.
     *
     * @param paymentId The unique identifier of the payment to refund.
     * @return True if the refund was initiated/completed successfully.
     */
    fun refundPayment(paymentId: UUID): Boolean
}
