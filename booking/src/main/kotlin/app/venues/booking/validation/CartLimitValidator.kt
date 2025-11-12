package app.venues.booking.validation

import app.venues.booking.domain.Cart
import app.venues.booking.repository.CartItemRepository
import app.venues.booking.repository.CartSeatRepository
import app.venues.booking.repository.CartTableRepository
import app.venues.common.exception.VenuesException
import org.springframework.stereotype.Component

/**
 * Validates cart quantity limits and rules.
 * Enforces total cart item limits and per-operation quantity constraints.
 */
@Component
class CartLimitValidator(
    private val cartSeatRepository: CartSeatRepository,
    private val cartItemRepository: CartItemRepository,
    private val cartTableRepository: CartTableRepository
) {
    companion object {
        const val MAX_TOTAL_TICKETS_IN_CART = 30
        const val MAX_TABLES_PER_CART = 5
    }

    fun validateAddSeatLimit(cart: Cart) {
        val currentTotal = calculateCurrentTotalTickets(cart)

        if (currentTotal >= MAX_TOTAL_TICKETS_IN_CART) {
            throw VenuesException.ValidationFailure(
                "Cart limit reached. You have $currentTotal ticket(s). " +
                        "Maximum $MAX_TOTAL_TICKETS_IN_CART tickets allowed per cart."
            )
        }
    }

    fun validateAddGALimit(cart: Cart, requestedQuantity: Int, existingItemQuantity: Int? = null) {
        if (requestedQuantity <= 0) {
            throw VenuesException.ValidationFailure("Quantity must be at least 1")
        }

        val currentTotal = calculateCurrentTotalTickets(cart)
        val newTotal = currentTotal + requestedQuantity

        if (newTotal > MAX_TOTAL_TICKETS_IN_CART) {
            val existingNote = existingItemQuantity?.let { " (current: $it)" } ?: ""
            throw VenuesException.ValidationFailure(
                "Cart limit reached. You have $currentTotal ticket(s), " +
                        "adding $requestedQuantity would total $newTotal$existingNote. " +
                        "Maximum $MAX_TOTAL_TICKETS_IN_CART tickets allowed per cart."
            )
        }
    }

    fun validateAddTableLimit(cart: Cart) {
        val currentTables = cartTableRepository.countByCart(cart)

        if (currentTables >= MAX_TABLES_PER_CART) {
            throw VenuesException.ValidationFailure(
                "Maximum $MAX_TABLES_PER_CART tables per cart. You currently have $currentTables table(s)."
            )
        }

        // Also check total tickets including table seats
        val currentTotal = calculateCurrentTotalTickets(cart)
        if (currentTotal >= MAX_TOTAL_TICKETS_IN_CART) {
            throw VenuesException.ValidationFailure(
                "Cart limit reached. You have $currentTotal ticket(s). " +
                        "Maximum $MAX_TOTAL_TICKETS_IN_CART tickets allowed per cart."
            )
        }
    }

    fun calculateCurrentTotalTickets(cart: Cart): Int {
        val seatCount = cartSeatRepository.findByCart(cart).size
        val gaTicketCount = cartItemRepository.findByCart(cart).sumOf { it.quantity }
        val tableSeatCount = cartTableRepository.findByCart(cart).sumOf { it.seatCount }
        return seatCount + gaTicketCount + tableSeatCount
    }
}