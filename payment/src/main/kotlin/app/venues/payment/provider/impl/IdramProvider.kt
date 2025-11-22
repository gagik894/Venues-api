package app.venues.payment.provider.impl

import app.venues.finance.api.dto.PaymentConfig
import app.venues.payment.domain.Payment
import app.venues.payment.provider.PaymentProvider
import app.venues.payment.provider.dto.PaymentCallbackResult
import app.venues.payment.provider.dto.PaymentLinkResult
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.security.MessageDigest

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

    override fun extractPaymentId(params: Map<String, String>): String? {
        return params["EDP_BILL_NO"]
    }

    override fun handleCallback(params: Map<String, String>, config: PaymentConfig): PaymentCallbackResult {
        val idramConfig = config.idram ?: return PaymentCallbackResult.Invalid("Idram config missing")

        val precheck = params["EDP_PRECHECK"]
        val billNo = params["EDP_BILL_NO"] ?: return PaymentCallbackResult.Invalid("Missing EDP_BILL_NO")
        val recAccount = params["EDP_REC_ACCOUNT"]

        // Precheck Logic
        if (precheck == "YES") {
            if (recAccount == idramConfig.recAccount) {
                return PaymentCallbackResult.ResponseRequired("OK")
            } else {
                return PaymentCallbackResult.ResponseRequired("Precheck failed", 400)
            }
        }

        // Payment Confirmation Logic
        val amount = params["EDP_AMOUNT"] ?: ""
        val payerAccount = params["EDP_PAYER_ACCOUNT"] ?: ""
        val transId = params["EDP_TRANS_ID"] ?: ""
        val transDate = params["EDP_TRANS_DATE"] ?: ""
        val checksum = params["EDP_CHECKSUM"] ?: ""

        // Secret key is required for checksum validation
        val secretKey =
            idramConfig.secretKey ?: return PaymentCallbackResult.Invalid("Idram secret key missing in config")

        // Checksum format: EDP_REC_ACCOUNT:EDP_AMOUNT:SECRET_KEY:EDP_BILL_NO:EDP_PAYER_ACCOUNT:EDP_TRANS_ID:EDP_TRANS_DATE
        val checksumString = listOf(
            recAccount,
            amount,
            secretKey,
            billNo,
            payerAccount,
            transId,
            transDate
        ).joinToString(":")

        val calculatedChecksum = md5(checksumString)

        if (!calculatedChecksum.equals(checksum, ignoreCase = true)) {
            return PaymentCallbackResult.Invalid("Invalid checksum")
        }

        return PaymentCallbackResult.Success(
            paymentId = billNo,
            externalId = transId,
            amount = BigDecimal(amount)
        )
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
