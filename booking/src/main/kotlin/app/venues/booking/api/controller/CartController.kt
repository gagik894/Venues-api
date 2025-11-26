package app.venues.booking.api.controller

import app.venues.booking.api.dto.*
import app.venues.booking.service.CartQueryService
import app.venues.booking.service.CartService
import app.venues.common.model.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
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
    private val cartQueryService: CartQueryService
) {
    private val logger = KotlinLogging.logger {}

    @Value("\${app.security.cookie.secure}")
    private var cookieSecure: Boolean = true

    private fun setCartCookie(response: HttpServletResponse, token: UUID) {
        val cookie = Cookie("cart_token", token.toString())
        cookie.isHttpOnly = true
        cookie.path = "/"
        cookie.maxAge = 60 * 30
        cookie.secure = cookieSecure
        cookie.setAttribute("SameSite", "Strict")
        response.addCookie(cookie)
    }


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
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        logger.debug { "Adding seat to cart: $request, token=$effectiveToken" }

        val result = cartService.addSeatToCart(request, effectiveToken)

        setCartCookie(response, result.token)

        return ApiResponse.success(
            data = result,
            message = "Seat added to cart"
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
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        logger.debug { "Adding GA to cart: $request, token=$effectiveToken" }

        val result = cartService.addGAToCart(request, effectiveToken)

        setCartCookie(response, result.token)

        return ApiResponse.success(
            data = result,
            message = "GA tickets added to cart"
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
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        logger.debug { "Adding Table to cart: $request, token=$effectiveToken" }

        val result = cartService.addTableToCart(request, effectiveToken)

        setCartCookie(response, result.token)

        return ApiResponse.success(
            data = result,
            message = "Table added to cart"
        )
    }

    /**
     * Get cart summary.
     */
    @GetMapping("/summary")
    @Operation(
        summary = "Get cart summary",
        description = "Retrieve complete cart with all items and pricing. Uses token from cookie or query param."
    )
    fun getCartSummary(
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        ?: throw IllegalArgumentException("Cart token is required (query param or cookie)")

        logger.debug { "Fetching cart: $effectiveToken" }

        val cart = cartQueryService.getCartSummary(effectiveToken)

        // Refresh cookie expiration on read activity
        setCartCookie(response, effectiveToken)

        return ApiResponse.success(
            data = cart,
            message = "Cart retrieved successfully"
        )
    }

    /**
     * Remove seat from cart.
     */
    @DeleteMapping("/seats/{seatIdentifier}")
    @Operation(
        summary = "Remove seat from cart",
        description = "Remove a specific seat from cart"
    )
    fun removeSeatFromCart(
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @PathVariable seatIdentifier: String
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        ?: throw IllegalArgumentException("Cart token is required")

        logger.debug { "Removing seat from cart: token=$effectiveToken, seatIdentifier=$seatIdentifier" }

        val result = cartService.removeSeatFromCart(effectiveToken, seatIdentifier)

        return ApiResponse.success(
            data = result,
            message = "Seat removed from cart"
        )
    }

    /**
     * Update GA ticket quantity in cart.
     */
    @PutMapping("/ga/{levelIdentifier}")
    @Operation(
        summary = "Update GA quantity",
        description = "Update quantity of GA tickets for a level. Setting quantity to 0 removes the item."
    )
    fun updateGAQuantity(
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @PathVariable levelIdentifier: String,
        @Valid @RequestBody request: UpdateGAQuantityRequest
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        ?: throw IllegalArgumentException("Cart token is required")

        logger.debug { "Updating GA quantity: token=$effectiveToken, level=$levelIdentifier, qty=${request.quantity}" }

        val result = cartService.updateGAQuantity(effectiveToken, levelIdentifier, request)

        return ApiResponse.success(
            data = result,
            message = "GA quantity updated successfully"
        )
    }

    /**
     * Remove GA item from cart.
     */
    @DeleteMapping("/ga/{levelIdentifier}")
    @Operation(
        summary = "Remove GA item from cart",
        description = "Remove all GA tickets for a specific level from cart."
    )
    fun removeGAFromCart(
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @PathVariable levelIdentifier: String
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        ?: throw IllegalArgumentException("Cart token is required")

        logger.debug { "Removing GA item: token=$effectiveToken, level=$levelIdentifier" }

        val result = cartService.removeGAFromCart(effectiveToken, levelIdentifier)

        return ApiResponse.success(
            data = result,
            message = "GA item removed from cart"
        )
    }

    /**
     * Remove table from cart.
     */
    @DeleteMapping("/tables/{tableIdentifier}")
    @Operation(
        summary = "Remove table from cart",
        description = "Remove a specific table from cart."
    )
    fun removeTableFromCart(
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @PathVariable tableIdentifier: String
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        ?: throw IllegalArgumentException("Cart token is required")

        logger.debug { "Removing table: token=$effectiveToken, table=$tableIdentifier" }

        val result = cartService.removeTableFromCart(effectiveToken, tableIdentifier)

        return ApiResponse.success(
            data = result,
            message = "Table removed from cart"
        )
    }

    /**
     * Clear cart.
     */
    @DeleteMapping("/clear")
    @Operation(
        summary = "Clear cart",
        description = "Remove all items from cart"
    )
    fun clearCart(
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CartSummaryResponse> {
        val effectiveToken = token ?: cookieToken
        ?: throw IllegalArgumentException("Cart token is required")

        logger.debug { "Clearing cart: $effectiveToken" }

        val result = cartService.clearCart(effectiveToken)

        // Clear cookie
        val cookie = Cookie("cart_token", "")
        cookie.path = "/"
        cookie.maxAge = 0
        cookie.secure = cookieSecure
        response.addCookie(cookie)

        return ApiResponse.success(
            data = result,
            message = "Cart cleared"
        )
    }

    /**
     * Apply promo code to cart.
     */
    @PostMapping("/promo-code")
    @Operation(
        summary = "Apply promo code",
        description = "Apply a promo code to the cart and recalculate totals."
    )
    fun applyPromoCode(
        @RequestParam(required = false) token: UUID?,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        @Valid @RequestBody request: ApplyPromoCodeRequest
    ): ApiResponse<PromoCodeAppliedResponse> {
        val effectiveToken = token ?: cookieToken
        ?: throw IllegalArgumentException("Cart token is required")

        logger.debug { "Applying promo code: token=$effectiveToken, code=${request.code}" }

        val result = cartService.applyPromoCode(effectiveToken, request.code)

        return ApiResponse.success(
            data = result,
            message = "Promo code applied successfully"
        )
    }
}
