package app.venues.booking.api.dto

import java.util.*

/**
 * Minimal response for cart mutation operations.
 *
 * This response is designed to be:
 * - Small (~100 bytes) for efficient idempotency caching
 * - Sufficient for frontend to know the operation succeeded
 * - Include cart token for subsequent operations
 *
 * Frontend should call GET /cart/summary after receiving this
 * to get the full, fresh cart state.
 */
data class CartMutationResponse(
    /** Cart token for subsequent operations */
    val cartToken: UUID,

    /** Whether the operation was successful */
    val success: Boolean = true,

    /** ID of the affected item (seat code, GA level code, table code) */
    val affectedItemId: String,

    /** Type of item affected */
    val affectedItemType: CartItemType,

    /** Cart version for optimistic locking (optional) */
    val cartVersion: Long? = null
)

/**
 * Types of items that can be in a cart.
 */
enum class CartItemType {
    SEAT,
    GA,
    TABLE
}
