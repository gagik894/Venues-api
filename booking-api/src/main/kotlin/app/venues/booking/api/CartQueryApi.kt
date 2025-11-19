package app.venues.booking.api

import app.venues.booking.api.dto.CartSummaryResponse
import java.util.*

/**
 * Public API Port for Cart Query (Read) operations.
 *
 * This interface defines the contract for all cross-module interactions
 * that read and aggregate cart data.
 *
 * External modules (like Platform) MUST depend on this interface,
 * not on the concrete `CartQueryService` implementation.
 */
interface CartQueryApi {

    /**
     * Retrieves cart summary.
     * @see app.venues.booking.service.CartQueryService.getCartSummary
     */
    fun getCartSummary(token: UUID): CartSummaryResponse
}
