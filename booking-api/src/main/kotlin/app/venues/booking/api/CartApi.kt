package app.venues.booking.api

import app.venues.booking.api.dto.*
import java.util.*

/**
 * Public API Port for Cart Command (Write) operations.
 */
interface CartApi {

    /**
     * Adds a seat to cart.
     * @param request DTO containing all request data
     * @param token Optional existing cart token
     * @param isStaffCart Whether this is a staff cart (affects expiration timing)
     * @return Response DTO with token and message
     */
    fun addSeatToCart(
        request: AddSeatToCartRequest,
        token: UUID? = null,
        isStaffCart: Boolean = false,
        platformId: UUID? = null
    ): CartSummaryResponse

    /**
     * Adds GA tickets to cart.
     * @param request DTO containing all request data
     * @param token Optional existing cart token
     * @param isStaffCart Whether this is a staff cart (affects expiration timing)
     * @return Response DTO with token and message
     */
    fun addGAToCart(
        request: AddGAToCartRequest,
        token: UUID? = null,
        isStaffCart: Boolean = false,
        platformId: UUID? = null
    ): CartSummaryResponse

    /**
     * Adds a table to cart.
     * @param request DTO containing all request data
     * @param token Optional existing cart token
     * @param isStaffCart Whether this is a staff cart (affects expiration timing)
     * @return Response DTO with token and message
     */
    fun addTableToCart(
        request: AddTableToCartRequest,
        token: UUID? = null,
        isStaffCart: Boolean = false,
        platformId: UUID? = null
    ): CartSummaryResponse

    /**
     * Removes a specific seat from cart.
     */
    fun removeSeatFromCart(token: UUID, seatIdentifier: String, platformId: UUID? = null): CartSummaryResponse

    /**
     * Updates quantity of GA tickets in cart.
     * @param request DTO containing the new quantity
     */
    fun updateGAQuantity(
        token: UUID,
        levelIdentifier: String,
        request: UpdateGAQuantityRequest,
        platformId: UUID? = null
    ): CartSummaryResponse

    /**
     * Removes all GA tickets for a level from cart.
     */
    fun removeGAFromCart(token: UUID, levelIdentifier: String, platformId: UUID? = null): CartSummaryResponse

    /**
     * Remove a table from cart.
     */
    fun removeTableFromCart(token: UUID, tableIdentifier: String, platformId: UUID? = null): CartSummaryResponse

    /**
     * Clears entire cart.
     */
    fun clearCart(token: UUID, platformId: UUID? = null): CartSummaryResponse

    /**
     * Apply a promo code to the cart.
     * @param token Cart token
     * @param code Promo code string
     * @return Response with new pricing details
     */
    fun applyPromoCode(token: UUID, code: String, platformId: UUID? = null): PromoCodeAppliedResponse

    /**
     * Platform-oriented batch hold (seats/GA/tables) with optional TTL override.
     * Returns the cart summary for the hold.
     */
    fun holdBatch(request: PlatformHoldBatchRequest): CartSummaryResponse
}
