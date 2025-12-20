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
     *
     * > [!WARNING]
     * > This contains sensitive secrets (API keys, passwords).
     * > - Do not expose this field to the frontend or public API responses.
     * > - This should only be used internally by the Payment Module to initialize transactions.
     * > - Ensure any logging of this object uses the overridden [toString] methods which mask secrets.
     */
    val config: PaymentConfig?
) {
    val merchantId: UUID get() = id
}
