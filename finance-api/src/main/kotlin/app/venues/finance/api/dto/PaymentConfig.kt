package app.venues.finance.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Type-safe configuration classes for merchant payment settings.
 * These are used directly in the MerchantProfile entity with JPA AttributeConverters.
 * Automatically serialized to JSON and encrypted before storage.
 */

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
    @JsonIgnore
    fun hasAnyProvider(): Boolean =
        idram != null || telcel != null || arca != null || converse != null || stripe != null

    /**
     * Get configured provider names.
     */
    @JsonIgnore
    fun getConfiguredProviders(): List<String> = buildList {
        if (idram != null) add("idram")
        if (telcel != null) add("telcel")
        if (arca != null) add("arca")
        if (converse != null) add("converse")
        if (stripe != null) add("stripe")
    }
}

/**
 * Configuration for Idram payment gateway.
 *
 * @property recAccount The receiver account number (ID).
 * @property secretKey The secret key for signing requests (optional for client-side, required for server-side).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IdramConfig(
    val recAccount: String,
    val secretKey: String? = null
) {
    init {
        require(recAccount.isNotBlank()) { "Idram recAccount cannot be blank" }
        if (secretKey != null) {
            require(secretKey.isNotBlank()) { "Idram secretKey cannot be blank if provided" }
        }
    }

    override fun toString(): String {
        return "IdramConfig(recAccount='$recAccount', secretKey='***')"
    }
}

/**
 * Configuration for Telcel payment gateway.
 *
 * @property storeKey The store identifier/key.
 * @property postponeBillIssuer The issuer ID for postponed bills (optional).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelcelConfig(
    val storeKey: String,
    val postponeBillIssuer: String? = null
) {
    init {
        require(storeKey.isNotBlank()) { "Telcel storeKey cannot be blank" }
    }

    override fun toString(): String {
        return "TelcelConfig(storeKey='***', postponeBillIssuer=$postponeBillIssuer)"
    }
}

/**
 * Configuration for Arca (Armenian Card) payment gateway.
 *
 * @property username The API username.
 * @property password The API password.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ArcaConfig(
    val username: String,
    val password: String
) {
    init {
        require(username.isNotBlank()) { "Arca username cannot be blank" }
        require(password.isNotBlank()) { "Arca password cannot be blank" }
    }

    override fun toString(): String {
        return "ArcaConfig(username='$username', password='***')"
    }
}

/**
 * Configuration for Converse Bank payment gateway.
 *
 * @property merchantId The merchant ID.
 * @property secretKey The secret key for authentication.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConverseConfig(
    val merchantId: String,
    val secretKey: String
) {
    init {
        require(merchantId.isNotBlank()) { "Converse merchantId cannot be blank" }
        require(secretKey.isNotBlank()) { "Converse secretKey cannot be blank" }
    }

    override fun toString(): String {
        return "ConverseConfig(merchantId='$merchantId', secretKey='***')"
    }
}

/**
 * Configuration for Stripe payment gateway.
 *
 * @property secretKey The secret API key (starts with sk_).
 * @property publishableKey The publishable API key (starts with pk_).
 * @property webhookSecret The signing secret for webhooks (starts with whsec_).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StripeConfig(
    val secretKey: String,
    val publishableKey: String,
    val webhookSecret: String? = null
) {
    init {
        require(secretKey.isNotBlank()) { "Stripe secretKey cannot be blank" }
        require(publishableKey.isNotBlank()) { "Stripe publishableKey cannot be blank" }
    }

    override fun toString(): String {
        return "StripeConfig(publishableKey='$publishableKey', secretKey='***', webhookSecret='***')"
    }
}
