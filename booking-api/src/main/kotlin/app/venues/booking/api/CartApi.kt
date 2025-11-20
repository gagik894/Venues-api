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
     * @return Response DTO with token and message
     */
    fun addSeatToCart(request: AddSeatToCartRequest, token: UUID? = null): AddToCartResponse

    /**
     * Adds GA tickets to cart.
     * @param request DTO containing all request data
     * @param token Optional existing cart token
     * @return Response DTO with token and message
     */
    fun addGAToCart(request: AddGAToCartRequest, token: UUID? = null): AddToCartResponse

    /**
     * Adds a table to cart.
     * @param request DTO containing all request data
     * @param token Optional existing cart token
     * @return Response DTO with token and message
     */
    fun addTableToCart(request: AddTableToCartRequest, token: UUID? = null): AddToCartResponse

    /**
     * Removes a specific seat from cart.
     */
    fun removeSeatFromCart(token: UUID, seatIdentifier: String)

    /**
     * Updates quantity of GA tickets in cart.
     * @param request DTO containing the new quantity
     */
    fun updateGAQuantity(token: UUID, levelIdentifier: String, request: UpdateGAQuantityRequest)

    /**
     * Removes all GA tickets for a level from cart.
     */
    fun removeGAFromCart(token: UUID, levelIdentifier: String)

    /**
     * Remove a table from cart.
     */
    fun removeTableFromCart(token: UUID, tableIdentifier: String)

    /**
     * Clears entire cart.
     */
    fun clearCart(token: UUID)
}
