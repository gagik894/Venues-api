package app.venues.finance.api

import app.venues.finance.api.dto.MerchantProfileDto
import java.util.*

/**
 * Public API for Payment Routing.
 *
 * This is the Port in Hexagonal Architecture.
 * Defines the stable interface for resolving payment destinations.
 */
interface PaymentRoutingApi {

    /**
     * Resolve the MerchantProfile to be used for a specific transaction context.
     *
     * @param venueId The ID of the venue.
     * @param eventId Optional ID of the event (for future specific overrides).
     * @return The resolved MerchantProfile DTO.
     */
    fun resolveMerchant(venueId: UUID, eventId: UUID? = null): MerchantProfileDto
}
