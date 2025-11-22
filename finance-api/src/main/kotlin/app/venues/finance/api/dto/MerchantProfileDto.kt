package app.venues.finance.api.dto

import java.util.*

/**
 * Data Transfer Object for Merchant Profile.
 * Exposes only safe, necessary information to consumers.
 */
data class MerchantProfileDto(
    val id: UUID,
    val name: String,
    val legalName: String?,
    val taxId: String?,
    val organizationId: UUID,

    /**
     * The payment configuration containing credentials.
     * WARNING: This contains sensitive secrets. Do not expose to frontend.
     * Only for internal use by Payment Module.
     */
    val config: PaymentConfig?
) {
    val merchantId: UUID get() = id
}
