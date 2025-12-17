package app.venues.payment.provider

import app.venues.finance.api.dto.PaymentConfig
import org.springframework.stereotype.Component

/**
 * Factory for resolving the appropriate PaymentProvider.
 */
@Component
class PaymentProviderFactory(
    private val providers: List<PaymentProvider>
) {
    private val providerMap = providers.associateBy { it.providerId }

    /**
     * Get a provider by ID.
     */
    fun getProvider(providerId: String): PaymentProvider {
        return providerMap[providerId]
            ?: throw IllegalArgumentException("Payment provider not found: $providerId")
    }

    /**
     * Get the first configured provider from the config (default strategy).
     * In a real app, the user would select the provider from a list.
     */
    fun getDefaultProvider(config: PaymentConfig): PaymentProvider {
        return providers.firstOrNull { it.isConfigured(config) }
            ?: throw IllegalStateException("No payment providers configured for this merchant")
    }
}
