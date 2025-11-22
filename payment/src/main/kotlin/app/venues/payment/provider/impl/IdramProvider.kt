package app.venues.payment.provider.impl

import app.venues.finance.api.dto.PaymentConfig
import app.venues.payment.domain.Payment
import app.venues.payment.provider.PaymentLinkResult
import app.venues.payment.provider.PaymentProvider
import org.springframework.stereotype.Component

/**
 * Payment Provider implementation for Idram.
 *
 * Generates a POST form for Idram checkout.
 */
@Component
class IdramProvider : PaymentProvider {

    override val providerId: String = "idram"

    override fun isConfigured(config: PaymentConfig): Boolean = config.idram != null

    override fun generatePaymentLink(payment: Payment, config: PaymentConfig): PaymentLinkResult {
        val idramConfig = config.idram ?: throw IllegalStateException("Idram config missing")

        val amount = payment.amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

        val formData = mapOf(
            "EDP_LANGUAGE" to "EN",
            "EDP_REC_ACCOUNT" to idramConfig.recAccount,
            "EDP_DESCRIPTION" to "Payment for booking ${payment.bookingId}",
            "EDP_AMOUNT" to amount,
            "EDP_BILL_NO" to payment.bookingId.toString()
        )

        return PaymentLinkResult(
            paymentUrl = "https://banking.idram.am/Payment/GetPayment",
            formData = formData,
            method = "POST"
        )
    }
}
