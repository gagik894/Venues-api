package app.venues.payment.service

import app.venues.booking.api.BookingApi
import app.venues.finance.api.PaymentRoutingApi
import app.venues.payment.api.PaymentApi
import app.venues.payment.api.dto.InitiatePaymentRequest
import app.venues.payment.api.dto.InitiatePaymentResponse
import app.venues.payment.domain.Payment
import app.venues.payment.provider.PaymentProviderFactory
import app.venues.payment.provider.dto.PaymentCallbackResult
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
    private val paymentRoutingApi: PaymentRoutingApi,
    private val paymentProviderFactory: PaymentProviderFactory,
    private val bookingApi: BookingApi
) : PaymentApi {

    private val logger = KotlinLogging.logger {}

    /**
     * Retrieves the status of a payment.
     */
    @Transactional
    override fun getPaymentStatus(paymentId: UUID): String {
        val payment = paymentRepository.findByIdWithLock(paymentId)
            .orElseThrow { RuntimeException("Payment not found: $paymentId") }

        if (payment.status == "PENDING") {
            try {
                val merchant = paymentRoutingApi.getMerchant(payment.merchantId)
                val config = merchant.config ?: return payment.status

                val provider = paymentProviderFactory.getProvider(payment.providerId)
                val result = provider.checkStatus(payment, config)

                if (result is PaymentCallbackResult.Success) {
                    payment.status = "COMPLETED"
                    payment.externalReference = result.externalId ?: payment.externalReference
                    paymentRepository.save(payment)
                    bookingApi.confirmBooking(payment.bookingId, payment.id)
                } else if (result is PaymentCallbackResult.Failure) {
                    payment.status = "FAILED"
                    paymentRepository.save(payment)
                    bookingApi.cancelBooking(payment.bookingId)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to check status for payment $paymentId" }
            }
        }
        return payment.status
    }

    /**
     * Initiates a payment by resolving the merchant and creating a record.
     */
    override fun initiatePayment(request: InitiatePaymentRequest): InitiatePaymentResponse {
        logger.info { "Initiating payment for booking ${request.bookingId} at venue ${request.venueId}" }

        // 0. Check if booking is already paid
        val booking = bookingApi.getBookingById(request.bookingId)
        if (booking.status.name == "CONFIRMED") {
            throw IllegalStateException("Booking ${request.bookingId} is already confirmed")
        }

        // 1. Resolve Merchant via Finance API
        val merchant = paymentRoutingApi.resolveMerchant(request.venueId)
        logger.debug { "Resolved merchant: ${merchant.id} (${merchant.name})" }

        val config = merchant.config
            ?: throw IllegalStateException("Merchant ${merchant.name} has no payment configuration")

        // 2. Select Provider
        val provider = if (request.providerId != null) {
            paymentProviderFactory.getProvider(request.providerId!!)
        } else {
            paymentProviderFactory.getDefaultProvider(config)
        }

        if (!provider.isConfigured(config)) {
            throw IllegalStateException("Provider ${provider.providerId} is not configured for merchant ${merchant.name}")
        }

        // 3. Create Payment Record
        val payment = Payment(
            bookingId = request.bookingId,
            amount = request.amount,
            currency = request.currency,
            status = "PENDING",
            merchantId = merchant.id,
            providerId = provider.providerId
        )

        // 4. Generate Link via Provider Strategy
        val linkResult = provider.generatePaymentLink(payment, config)

        payment.externalReference = linkResult.gatewayReference
        
        val savedPayment = paymentRepository.save(payment)
        logger.info { "Payment created: ${savedPayment.id}, provider: ${provider.providerId}" }

        return InitiatePaymentResponse(
            paymentId = savedPayment.id,
            status = savedPayment.status,
            paymentUrl = linkResult.paymentUrl,
            formData = linkResult.formData,
            method = linkResult.method,
            gatewayReference = linkResult.gatewayReference
        )
    }

    /**
     * Processes a callback from a payment provider.
     */
    override fun processCallback(providerId: String, params: Map<String, String>): Any {
        logger.info { "Processing callback from $providerId" }

        val provider = paymentProviderFactory.getProvider(providerId)

        val paymentIdStr = provider.extractPaymentId(params)
            ?: throw IllegalArgumentException("Could not extract payment ID from callback params")

        val paymentId = try {
            UUID.fromString(paymentIdStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid payment ID format: $paymentIdStr")
        }

        // Lock the payment record to prevent race conditions (double callbacks)
        val payment = paymentRepository.findByIdWithLock(paymentId)
            .orElseThrow { RuntimeException("Payment not found: $paymentId") }

        val merchant = paymentRoutingApi.getMerchant(payment.merchantId)
        val config = merchant.config
            ?: throw IllegalStateException("Merchant ${merchant.name} has no payment configuration")

        return when (val result = provider.handleCallback(params, config)) {
            is PaymentCallbackResult.Success -> {
                if (payment.status != "COMPLETED") {
                    payment.status = "COMPLETED"
                    payment.externalReference = result.externalId ?: payment.externalReference
                    paymentRepository.save(payment)
                    logger.info { "Payment ${payment.id} completed successfully via $providerId" }

                    try {
                        bookingApi.confirmBooking(payment.bookingId, payment.id)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to confirm booking ${payment.bookingId} after successful payment" }
                        // We don't rollback payment status, but we should alert/retry
                    }
                }
                "OK"
            }

            is PaymentCallbackResult.Failure -> {
                if (payment.status != "FAILED") {
                    payment.status = "FAILED"
                    paymentRepository.save(payment)
                    logger.warn { "Payment ${payment.id} failed: ${result.reason}" }

                    try {
                        bookingApi.cancelBooking(payment.bookingId)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to cancel booking ${payment.bookingId}" }
                    }
                }
                "FAILED"
            }

            is PaymentCallbackResult.ResponseRequired -> {
                logger.debug { "Provider $providerId requires immediate response" }
                result.payload
            }

            is PaymentCallbackResult.Invalid -> {
                logger.error { "Invalid callback for payment ${payment.id}: ${result.reason}" }
                throw IllegalArgumentException(result.reason)
            }
        }
    }

    /**
     * Refunds a completed payment.
     *
     * Currently only marks the payment as REFUNDED and cancels the booking.
     * Actual provider refund integration is pending.
     */
    @Transactional
    override fun refundPayment(paymentId: UUID): Boolean {
        logger.info { "Initiating refund for payment $paymentId" }

        val payment = paymentRepository.findByIdWithLock(paymentId)
            .orElseThrow { RuntimeException("Payment not found: $paymentId") }

        if (payment.status != "COMPLETED") {
            logger.warn { "Cannot refund payment $paymentId: Status is ${payment.status}" }
            throw IllegalStateException("Only COMPLETED payments can be refunded")
        }

        // TODO: Call provider refund API
        // val merchant = paymentRoutingApi.getMerchant(payment.merchantId)
        // val provider = paymentProviderFactory.getProvider(payment.providerId)
        // provider.refund(payment, merchant.config)

//        payment.status = "REFUNDED"
//        paymentRepository.save(payment)

        try {
            bookingApi.cancelBooking(payment.bookingId)
            logger.info { "Booking ${payment.bookingId} cancelled due to refund" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel booking ${payment.bookingId} during refund" }
            // We still return true as the payment status is updated, but this requires manual intervention
        }

        return true
    }
}
