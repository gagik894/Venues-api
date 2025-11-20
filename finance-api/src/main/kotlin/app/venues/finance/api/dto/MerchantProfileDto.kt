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

    // We do NOT expose the full encrypted config here.
    // Consumers usually just need to know "who" the merchant is,
    // or pass this DTO to a payment processor which might need internal access.
    // For now, we keep it simple.
    val hasPaymentConfig: Boolean
)
