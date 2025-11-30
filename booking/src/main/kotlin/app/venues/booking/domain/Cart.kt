package app.venues.booking.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Represents a temporary shopping cart session.
 *
 * This entity manages the lifecycle of a cart and its associated items (seats, GA tickets, tables).
 * Uses proper JPA relationships with cascade operations and orphan removal for data consistency.
 *
 * Performance Note: Child collections use LAZY fetch with @EntityGraph optimization
 * to prevent N+1 queries during cart retrieval operations.
 *
 * @param sessionId The [UUID] of the EventSession this cart is for.
 * @param expiresAt The absolute timestamp when the cart reservations expire.
 * @param token A unique public token for API access (used for anonymous carts).
 * @param userId The [UUID] of the authenticated user (nullable for guest checkout).
 * @param promoCode Optional promotional code applied to this cart.
 * @param discountAmount Optional discount amount (calculated from promo code).
 */
@Entity
@Table(
    name = "carts",
    indexes = [
        Index(name = "idx_cart_token", columnList = "token"),
        Index(name = "idx_cart_user_id", columnList = "user_id"),
        Index(name = "idx_cart_expires_at", columnList = "expires_at")
    ]
)
class Cart(
    @Column(name = "event_session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "token", nullable = false, unique = true)
    var token: UUID = UUID.randomUUID(),

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "promo_code")
    var promoCode: String? = null,

    @Column(name = "discount_amount", precision = 10, scale = 2)
    var discountAmount: BigDecimal? = null

) : AbstractUuidEntity() {



    /**
     * Individual seat selections in this cart.
     *
     * Cascade ALL: When cart is deleted, all seats are automatically removed.
     * Orphan Removal: When a seat is removed from this collection, it's deleted from DB.
     *
     * LAZY fetch: Collection is only loaded when explicitly accessed or via @EntityGraph.
     */
    @OneToMany(
        mappedBy = "cart",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var seats: MutableSet<CartSeat> = mutableSetOf()

    /**
     * General Admission (GA) ticket items in this cart.
     *
     * Cascade ALL: When cart is deleted, all GA items are automatically removed.
     * Orphan Removal: When a GA item is removed from this collection, it's deleted from DB.
     *
     * LAZY fetch: Collection is only loaded when explicitly accessed or via @EntityGraph.
     */
    @OneToMany(
        mappedBy = "cart",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var gaItems: MutableSet<CartItem> = mutableSetOf()

    /**
     * Complete table bookings in this cart.
     *
     * Cascade ALL: When cart is deleted, all table bookings are automatically removed.
     * Orphan Removal: When a table is removed from this collection, it's deleted from DB.
     *
     * LAZY fetch: Collection is only loaded when explicitly accessed or via @EntityGraph.
     */
    @OneToMany(
        mappedBy = "cart",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var tables: MutableSet<CartTable> = mutableSetOf()

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    /**
     * Extends the cart's expiration time.
     * Respects a hard limit relative to creation time to prevent infinite holding.
     *
     * @param minutes Number of minutes to extend from current time.
     * @param maxTtlMinutes Maximum total lifetime from cart creation.
     */
    fun extendExpiration(minutes: Long, maxTtlMinutes: Long) {
        val now = Instant.now()
        val proposed = now.plusSeconds(minutes * 60)
        val hardLimit = this.createdAt.plusSeconds(maxTtlMinutes * 60)

        this.expiresAt = if (proposed.isAfter(hardLimit)) hardLimit else proposed
    }


    /**
     * Calculates the total price of all items in the cart using snapshotted prices.
     *
     * This method aggregates:
     * - Individual seat prices (from seats collection)
     * - GA ticket prices (unitPrice * quantity for each GA item)
     * - Table prices (from tables collection)
     * - Subtracts any discount amount from promo codes
     *
     * Note: Uses snapshotted prices captured at add-to-cart time, NOT current live prices.
     * This ensures price consistency during the checkout flow.
     *
     * @return Total cart value as [BigDecimal] with 2 decimal places.
     */
    fun getTotalPrice(): BigDecimal {
        val seatTotal = seats.fold(BigDecimal.ZERO) { acc, seat ->
            acc.add(seat.unitPrice)
        }

        val gaTotal = gaItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.unitPrice.multiply(BigDecimal(item.quantity)))
        }

        val tableTotal = tables.fold(BigDecimal.ZERO) { acc, table ->
            acc.add(table.unitPrice)
        }

        val subtotal = seatTotal.add(gaTotal).add(tableTotal)
        val discount = discountAmount ?: BigDecimal.ZERO

        return subtotal.subtract(discount).max(BigDecimal.ZERO)
    }

    /**
     * Returns total number of items (not quantity) in the cart.
     * Useful for UI display and validation limits.
     *
     * @return Count of distinct items (seats + GA items + tables).
     */
    fun getItemCount(): Int = seats.size + gaItems.size + tables.size

    /**
     * Returns total ticket quantity accounting for GA multiples.
     * Used for capacity validation and order processing.
     *
     * @return Total ticket count (seats + sum of GA quantities + table seat counts).
     */
    fun getTotalTicketCount(): Int {
        val seatCount = seats.size
        val gaCount = gaItems.sumOf { it.quantity }
        val tableCount = tables.size // Each table counts as 1 unit for now
        return seatCount + gaCount + tableCount
    }

    /**
     * Checks if the cart is empty (no items).
     *
     * @return true if cart has no seats, GA items, or tables.
     */
    fun isEmpty(): Boolean = seats.isEmpty() && gaItems.isEmpty() && tables.isEmpty()
}
