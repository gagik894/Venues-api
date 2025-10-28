package app.venues.booking.api.controller

import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.AddToCartResponse
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.service.CartService
import app.venues.common.model.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Controller for cart operations.
 *
 * Provides endpoints for managing shopping cart (seats + GA tickets).
 * No authentication required - cart is token-based.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Shopping cart management")
class CartController(
    private val cartService: CartService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Add seat to cart.
     */
    @PostMapping("/seats")
    @Operation(
        summary = "Add seat to cart",
        description = "Add a specific seat to cart. Returns token to use for subsequent operations."
    )
    fun addSeatToCart(
        @Valid @RequestBody request: AddSeatToCartRequest,
        @RequestParam(required = false) token: UUID?
    ): ApiResponse<AddToCartResponse> {
        logger.debug { "Adding seat to cart: $request" }

        val result = cartService.addSeatToCart(request, token)

        return ApiResponse.success(
            data = result,
            message = result.message
        )
    }

    /**
     * Add GA tickets to cart.
     */
    @PostMapping("/ga")
    @Operation(
        summary = "Add GA tickets to cart",
        description = "Add general admission tickets to cart"
    )
    fun addGAToCart(
        @Valid @RequestBody request: AddGAToCartRequest,
        @RequestParam(required = false) token: UUID?
    ): ApiResponse<AddToCartResponse> {
        logger.debug { "Adding GA to cart: $request" }

        val result = cartService.addGAToCart(request, token)

        return ApiResponse.success(
            data = result,
            message = result.message
        )
    }

    /**
     * Get cart summary.
     */
    @GetMapping("/{token}")
    @Operation(
        summary = "Get cart summary",
        description = "Retrieve complete cart with all items and pricing"
    )
    fun getCartSummary(@PathVariable token: UUID): ApiResponse<CartSummaryResponse> {
        logger.debug { "Fetching cart: $token" }

        val cart = cartService.getCartSummary(token)

        return ApiResponse.success(
            data = cart,
            message = "Cart retrieved successfully"
        )
    }

    /**
     * Remove seat from cart.
     */
    @DeleteMapping("/{token}/seats/{seatId}")
    @Operation(
        summary = "Remove seat from cart",
        description = "Remove a specific seat from cart"
    )
    fun removeSeatFromCart(
        @PathVariable token: UUID,
        @PathVariable seatId: Long
    ): ApiResponse<Unit> {
        logger.debug { "Removing seat from cart: token=$token, seatId=$seatId" }

        cartService.removeSeatFromCart(token, seatId)

        return ApiResponse.success(
            data = Unit,
            message = "Seat removed from cart"
        )
    }

    /**
     * Clear cart.
     */
    @DeleteMapping("/{token}")
    @Operation(
        summary = "Clear cart",
        description = "Remove all items from cart"
    )
    fun clearCart(@PathVariable token: UUID): ApiResponse<Unit> {
        logger.debug { "Clearing cart: $token" }

        cartService.clearCart(token)

        return ApiResponse.success(
            data = Unit,
            message = "Cart cleared"
        )
    }
}

