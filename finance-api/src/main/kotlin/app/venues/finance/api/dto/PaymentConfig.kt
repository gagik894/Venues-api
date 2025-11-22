package app.venues.finance.api.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Type-safe configuration classes for merchant payment settings.
 * These are used directly in the MerchantProfile entity with JPA AttributeConverters.
 * Automatically serialized to JSON and encrypted before storage.
 */

/**
 * Complete payment configuration for a merchant.
 * Supports multiple payment gateways simultaneously.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentConfig(
    val idram: IdramConfig? = null,
    val telcel: TelcelConfig? = null,
    val arca: ArcaConfig? = null,
    val converse: ConverseConfig? = null,
    val stripe: StripeConfig? = null
) {
    /**
     * Check if any payment gateway is configured.
     */
    fun hasAnyProvider(): Boolean =
        idram != null || telcel != null || arca != null || converse != null || stripe != null

    /**
     * Get configured provider names.
     */
    fun getConfiguredProviders(): List<String> = buildList {
        if (idram != null) add("idram")
        if (telcel != null) add("telcel")
        if (arca != null) add("arca")
        if (converse != null) add("converse")
        if (stripe != null) add("stripe")
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class IdramConfig(
    val recAccount: String,
    val secretKey: String? = null // Optional for public display, required for signing
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelcelConfig(
    val storeKey: String,
    val postponeBillIssuer: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ArcaConfig(
    val username: String,
    val password: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConverseConfig(
    val merchantId: String,
    val secretKey: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StripeConfig(
    val secretKey: String,
    val publishableKey: String,
    val webhookSecret: String? = null
)
