package app.venues.finance.api

import app.venues.finance.api.dto.MerchantProfileDto
import java.util.*

/**
 * Public API for Payment Routing.
 *
 * This is the Input Port in Hexagonal Architecture for the Finance module.
 * It defines the contract for resolving the correct financial destination (Merchant)
 * for any given transaction context (Venue, Event, etc.).
 *
 * Usage:
 * Call [resolveMerchant] before initiating any payment to determine which credentials to use.
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

    /**
     * Retrieve a specific merchant profile by ID.
     *
     * @param merchantId The ID of the merchant.
     * @return The MerchantProfile DTO.
     */
    fun getMerchant(merchantId: UUID): MerchantProfileDto
}
