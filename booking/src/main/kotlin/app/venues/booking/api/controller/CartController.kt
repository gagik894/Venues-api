package app.venues.booking.api.controller

import app.venues.booking.api.dto.*
import app.venues.booking.service.CartQueryService
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
 * Delegates write operations (Commands) to `CartService`.
 * Delegates read operations (Queries) to `CartQueryService`.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Shopping cart management")
class CartController(
    private val cartService: CartService,
    private val cartQueryService: CartQueryService // NEW: Inject query service
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
     * Add Tables tickets to cart.
     */
    @PostMapping("/table")
    @Operation(
        summary = "Add Table to cart",
        description = "Add Table tickets to cart"
    )
    fun addTableToCart(
        @Valid @RequestBody request: AddTableToCartRequest,
        @RequestParam(required = false) token: UUID?
    ): ApiResponse<AddToCartResponse> {
        logger.debug { "Adding Table to cart: $request" }

        val result = cartService.addTableToCart(request, token)

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

        // DELEGATE TO THE NEW QUERY SERVICE
        val cart = cartQueryService.getCartSummary(token)

        return ApiResponse.success(
            data = cart,
            message = "Cart retrieved successfully"
        )
    }

    /**
     * Remove seat from cart.
     */
    @DeleteMapping("/{token}/seats/{seatIdentifier}")
    @Operation(
        summary = "Remove seat from cart",
        description = "Remove a specific seat from cart"
    )
    fun removeSeatFromCart(
        @PathVariable token: UUID,
        @PathVariable seatIdentifier: String
    ): ApiResponse<Unit> {
        logger.debug { "Removing seat from cart: token=$token, seatIdentifier=$seatIdentifier" }

        cartService.removeSeatFromCart(token, seatIdentifier)

        return ApiResponse.success(
            data = Unit,
            message = "Seat removed from cart"
        )
    }

    /**
     * Update GA ticket quantity in cart.
     */
    // FIX: Changed to a consistent RESTful URL
    @PutMapping("/{token}/ga/{levelIdentifier}")
    @Operation(
        summary = "Update GA quantity",
        description = "Update quantity of GA tickets for a level. Setting quantity to 0 removes the item."
    )
    fun updateGAQuantity(
        @PathVariable token: UUID,
        @PathVariable levelIdentifier: String, // ID is now in the path
        @Valid @RequestBody request: UpdateGAQuantityRequest // DTO only contains quantity
    ): ApiResponse<Unit> {
        logger.debug { "Updating GA quantity: token=$token, level=$levelIdentifier, qty=${request.quantity}" }

        cartService.updateGAQuantity(token, levelIdentifier, request.quantity)

        return ApiResponse.success(
            data = Unit,
            message = "GA quantity updated successfully"
        )
    }

    /**
     * Remove GA item from cart.
     */
    @DeleteMapping("/{token}/ga/{levelIdentifier}")
    @Operation(
        summary = "Remove GA item from cart",
        description = "Remove all GA tickets for a specific level from cart."
    )
    fun removeGAFromCart(
        @PathVariable token: UUID,
        @PathVariable levelIdentifier: String
    ): ApiResponse<Unit> {
        logger.debug { "Removing GA item: token=$token, level=$levelIdentifier" }

        cartService.removeGAFromCart(token, levelIdentifier)

        return ApiResponse.success(
            data = Unit,
            message = "GA item removed from cart"
        )
    }

    /**
     * Remove table from cart.
     */
    @DeleteMapping("/{token}/tables/{tableIdentifier}")
    @Operation(
        summary = "Remove table from cart",
        description = "Remove a specific table from cart."
    )
    fun removeTableFromCart(
        @PathVariable token: UUID,
        @PathVariable tableIdentifier: String
    ): ApiResponse<Unit> {
        logger.debug { "Removing table: token=$token, table=$tableIdentifier" }

        cartService.removeTableFromCart(token, tableIdentifier)

        return ApiResponse.success(
            data = Unit,
            message = "Table removed from cart"
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