package app.venues.booking.service.model

import app.venues.booking.domain.*
import app.venues.event.api.dto.EventSessionDto
import java.math.BigDecimal
import java.util.*

/**
 * Immutable snapshot of a cart and its associated session.
 * Prevents further lazy loading mutations once booking creation starts.
 */
data class CartSnapshot(
    val cart: Cart,
    val seats: List<CartSeat>,
    val gaItems: List<CartItem>,
    val tables: List<CartTable>,
    val session: EventSessionDto
) {
    val sessionId: UUID = cart.sessionId
}

/**
 * Context required to build a booking from a cart snapshot.
 */
data class BookingCreationContext(
    val userId: UUID?,
    val guest: Guest?,
    val platformId: UUID?,
    val paymentReference: String? = null,
    val salesChannel: SalesChannel = SalesChannel.WEBSITE
)

/**
 * Simple breakdown of booking pricing for logging/auditing.
 */
data class PricingBreakdown(
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val serviceFee: BigDecimal,
    val total: BigDecimal
)

/**
 * Result of assembling a booking aggregate.
 */
data class BookingCreationResult(
    val booking: Booking,
    val pricing: PricingBreakdown
)
