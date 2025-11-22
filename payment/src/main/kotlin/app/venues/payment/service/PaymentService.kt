package app.venues.payment.service

import app.venues.finance.api.PaymentRoutingApi
import app.venues.payment.api.PaymentApi
import app.venues.payment.api.dto.InitiatePaymentRequest
import app.venues.payment.api.dto.InitiatePaymentResponse
import app.venues.payment.domain.Payment
import app.venues.payment.repository.PaymentRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service implementation for Payment operations.
 *
 * Handles the business logic for creating and managing payments.
 * Integrates with Finance module for merchant routing.
 */
@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentRoutingApi: PaymentRoutingApi
) : PaymentApi {

    private val logger = KotlinLogging.logger {}

    /**
     * Retrieves the status of a payment.
     */
    @Transactional(readOnly = true)
    override fun getPaymentStatus(paymentId: UUID): String {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { RuntimeException("Payment not found: $paymentId") }
        return payment.status
    }

    /**
     * Initiates a payment by resolving the merchant and creating a record.
     */
    override fun initiatePayment(request: InitiatePaymentRequest): InitiatePaymentResponse {
        logger.info { "Initiating payment for booking ${request.bookingId} at venue ${request.venueId}" }
        //TODO: Implement actual payment gateway integration

        // 1. Resolve Merchant via Finance API
        val merchant = paymentRoutingApi.resolveMerchant(request.venueId)
        logger.debug { "Resolved merchant: ${merchant.id} (${merchant})" }

        // 2. Create Payment Record
        val payment = Payment(
            bookingId = request.bookingId,
            amount = request.amount,
            currency = request.currency,
            status = "PENDING",
            merchantId = merchant.id
        )

        // 3. (Placeholder) Interact with Payment Gateway
        // In a real implementation, we would use a Strategy pattern based on merchant.provider
        // to call Stripe, PayPal, etc.
        val gatewayReference = "simulated_ref_${UUID.randomUUID()}"
        val paymentUrl = "https://checkout.example.com/pay/${gatewayReference}"

        payment.externalReference = gatewayReference

        val savedPayment = paymentRepository.save(payment)
        logger.info { "Payment created: ${savedPayment.id}, status: ${savedPayment.status}" }

        return InitiatePaymentResponse(
            paymentId = savedPayment.id,
            status = savedPayment.status,
            paymentUrl = paymentUrl,
            gatewayReference = gatewayReference
        )
    }
}
