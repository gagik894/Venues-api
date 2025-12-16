package app.venues.booking.api.controller

import app.venues.booking.api.dto.*
import app.venues.booking.service.CartQueryService
import app.venues.booking.service.CartService
import app.venues.booking.service.StaffCartService
import app.venues.common.model.ApiResponse
import app.venues.venue.api.service.VenueSecurityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Controller for staff cart operations.
 *
 * Provides cart management endpoints for venue staff to build carts incrementally
 * for in-person direct sales. Staff carts start with 20-minute expiration (vs 7
 * minutes for customer carts) and get 10-minute extensions (vs 5 minutes for customers).
 *
 * All operations require:
 * - Staff or Super Admin role
 * - Venue management permission for the specified venue
 *
 * Cart operations delegate to existing CartService, with `isStaffCart=true` flag.
 * Checkout converts cart to confirmed booking via Direct Sales flow.
 */
@RestController
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/v1/staff/venues/{venueId}/cart")
@Tag(
    name = "Staff Cart",
    description = "Cart management for venue staff (direct sales)"
)
class StaffCartController(
    private val cartService: CartService,
    private val cartQueryService: CartQueryService,
    private val staffCartService: StaffCartService,
    private val venueSecurityService: VenueSecurityService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Add seat to staff cart.
     * Creates cart with 20-minute expiration if first item.
     * Extends by 10 minutes on each action (max 30 minutes from creation).
     */
    @PostMapping("/seats")
    @Operation(
        summary = "Add seat to staff cart",
        description = "Add a seat to staff cart. Creates cart with 20-minute expiration if first item. " +
                "Extends expiration by 10 minutes on each add (max 30 minutes from creation)."
    )
    fun addSeatToCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: AddSeatToCartRequest,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId adding seat to cart for venue $venueId" }

        val effectiveToken = token ?: cookieToken
        val result = cartService.addSeatToCart(request, effectiveToken, isStaffCart = true)

        setCartCookie(response, result.token)

        return ApiResponse.success(
            data = result,
            message = "Seat added to staff cart"
        )
    }

    /**
     * Add GA tickets to staff cart.
     */
    @PostMapping("/ga")
    @Operation(
        summary = "Add GA tickets to staff cart",
        description = "Add general admission tickets to staff cart"
    )
    fun addGAToCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: AddGAToCartRequest,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId adding GA to cart for venue $venueId" }

        val effectiveToken = token ?: cookieToken
        val result = cartService.addGAToCart(request, effectiveToken, isStaffCart = true)

        setCartCookie(response, result.token)

        return ApiResponse.success(
            data = result,
            message = "GA tickets added to staff cart"
        )
    }

    /**
     * Add table to staff cart.
     */
    @PostMapping("/table")
    @Operation(
        summary = "Add table to staff cart",
        description = "Add a complete table booking to staff cart"
    )
    fun addTableToCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: AddTableToCartRequest,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId adding table to cart for venue $venueId" }

        val effectiveToken = token ?: cookieToken
        val result = cartService.addTableToCart(request, effectiveToken, isStaffCart = true)

        setCartCookie(response, result.token)

        return ApiResponse.success(
            data = result,
            message = "Table added to staff cart"
        )
    }

    /**
     * Get staff cart summary.
     */
    @GetMapping("/summary")
    @Operation(
        summary = "Get staff cart summary",
        description = "Retrieve complete cart with all items and pricing"
    )
    fun getCartSummary(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId fetching cart summary for venue $venueId" }

        val effectiveToken = token ?: cookieToken
        ?: throw app.venues.common.exception.VenuesException.ValidationFailure("Cart token is required")

        val cart = cartQueryService.getCartSummary(effectiveToken)

        setCartCookie(response, effectiveToken)

        return ApiResponse.success(
            data = cart,
            message = "Cart retrieved successfully"
        )
    }

    /**
     * Remove seat from staff cart.
     */
    @DeleteMapping("/seats/{seatIdentifier}")
    @Operation(
        summary = "Remove seat from staff cart",
        description = "Remove a specific seat from cart and release inventory"
    )
    fun removeSeatFromCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @PathVariable seatIdentifier: String
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId removing seat from cart" }

        val effectiveToken = token ?: cookieToken
        ?: throw app.venues.common.exception.VenuesException.ValidationFailure("Cart token is required")

        val result = cartService.removeSeatFromCart(effectiveToken, seatIdentifier)

        return ApiResponse.success(
            data = result,
            message = "Seat removed from cart"
        )
    }

    /**
     * Update GA quantity in staff cart.
     */
    @PutMapping("/ga/{levelIdentifier}")
    @Operation(
        summary = "Update GA quantity in staff cart",
        description = "Update quantity of GA tickets for a level. Setting to 0 removes the item."
    )
    fun updateGAQuantity(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @PathVariable levelIdentifier: String,
        @Valid @RequestBody request: UpdateGAQuantityRequest
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId updating GA quantity in cart" }

        val effectiveToken = token ?: cookieToken
        ?: throw app.venues.common.exception.VenuesException.ValidationFailure("Cart token is required")

        val result = cartService.updateGAQuantity(effectiveToken, levelIdentifier, request)

        return ApiResponse.success(
            data = result,
            message = "GA quantity updated"
        )
    }

    /**
     * Remove GA item from staff cart.
     */
    @DeleteMapping("/ga/{levelIdentifier}")
    @Operation(
        summary = "Remove GA item from staff cart",
        description = "Remove all GA tickets for a specific level from cart"
    )
    fun removeGAFromCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @PathVariable levelIdentifier: String
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId removing GA from cart" }

        val effectiveToken = token ?: cookieToken
        ?: throw app.venues.common.exception.VenuesException.ValidationFailure("Cart token is required")

        val result = cartService.removeGAFromCart(effectiveToken, levelIdentifier)

        return ApiResponse.success(
            data = result,
            message = "GA item removed from cart"
        )
    }

    /**
     * Remove table from staff cart.
     */
    @DeleteMapping("/tables/{tableIdentifier}")
    @Operation(
        summary = "Remove table from staff cart",
        description = "Remove a specific table from cart and release inventory"
    )
    fun removeTableFromCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @PathVariable tableIdentifier: String
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId removing table from cart" }

        val effectiveToken = token ?: cookieToken
        ?: throw app.venues.common.exception.VenuesException.ValidationFailure("Cart token is required")

        val result = cartService.removeTableFromCart(effectiveToken, tableIdentifier)

        return ApiResponse.success(
            data = result,
            message = "Table removed from cart"
        )
    }

    /**
     * Clear entire staff cart.
     */
    @DeleteMapping("/clear")
    @Operation(
        summary = "Clear staff cart",
        description = "Remove all items from cart and release all inventory"
    )
    fun clearCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.debug { "Staff $staffId clearing cart" }

        val effectiveToken = token ?: cookieToken
        ?: throw app.venues.common.exception.VenuesException.ValidationFailure("Cart token is required")

        val result = cartService.clearCart(effectiveToken)

        // Clear cookie
        val cookie = jakarta.servlet.http.Cookie("cart_token", "")
        cookie.path = "/"
        cookie.maxAge = 0
        response.addCookie(cookie)

        return ApiResponse.success(
            data = result,
            message = "Cart cleared"
        )
    }

    /**
     * Checkout staff cart and create confirmed booking.
     * Converts cart to direct sale with immediate confirmation.
     */
    @PostMapping("/checkout")
    @Operation(
        summary = "Checkout staff cart",
        description = "Convert cart to a confirmed booking (direct sale). " +
                "Payment is assumed to have been collected by staff."
    )
    fun checkoutCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @Valid @RequestBody request: StaffCartCheckoutRequest,
        response: HttpServletResponse
    ): ApiResponse<StaffCartCheckoutResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.info { "Staff $staffId checking out cart for venue $venueId" }

        val effectiveToken = token ?: cookieToken
        ?: throw app.venues.common.exception.VenuesException.ValidationFailure("Cart token is required")

        val checkout = staffCartService.checkoutStaffCart(effectiveToken, request, venueId, staffId)

        // Clear cart cookie after successful checkout
        val cookie = jakarta.servlet.http.Cookie("cart_token", "")
        cookie.path = "/"
        cookie.maxAge = 0
        response.addCookie(cookie)

        return ApiResponse.success(
            data = checkout,
            message = "Booking created successfully"
        )
    }

    /**
     * Close items from staff cart (seats, tables, GA capacity reduction).
     *
     * Closes all items in the cart:
     * - Seats: Set to CLOSED status
     * - Tables: Set to CLOSED status
     * - GA: Reduce capacity by quantity (decrement available capacity)
     *
     * Releases inventory reservations and deletes the cart.
     */
    @PostMapping("/close")
    @Operation(
        summary = "Close items from staff cart",
        description = "Close all items in cart (seats, tables, GA capacity reduction). " +
                "Items are closed (not sold), inventory is released, and cart is deleted."
    )
    fun closeCart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<Map<String, Any>> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)
        logger.info { "Staff $staffId closing items from cart for venue $venueId" }

        val effectiveToken = token ?: cookieToken
        ?: throw app.venues.common.exception.VenuesException.ValidationFailure("Cart token is required")

        staffCartService.closeCartItems(effectiveToken, venueId, staffId)

        // Clear cart cookie after successful close
        val cookie = jakarta.servlet.http.Cookie("cart_token", "")
        cookie.path = "/"
        cookie.maxAge = 0
        response.addCookie(cookie)

        return ApiResponse.success(
            data = mapOf("message" to "Items closed successfully"),
            message = "Items closed successfully"
        )
    }

    // ==================== Private Helper Methods ====================

    private fun setCartCookie(response: HttpServletResponse, token: UUID) {
        val cookie = jakarta.servlet.http.Cookie("cart_token", token.toString())
        cookie.isHttpOnly = true
        cookie.path = "/"
        cookie.maxAge = 60 * 20 // 20 minutes for staff carts
        cookie.secure = true
        cookie.setAttribute("SameSite", "Strict")
        response.addCookie(cookie)
    }
}
