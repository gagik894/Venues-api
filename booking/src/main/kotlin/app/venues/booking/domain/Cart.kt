package app.venues.booking.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*


//TODO
//1. The Parent (Cart)
//Do not use FetchType.EAGER. Keep it Lazy.
//
//Kotlin
//
//@Entity
//@Table(name = "carts", ...)
//class Cart(
//    // ... fields ...
//) : AbstractUuidEntity() {
//
//    // ✅ RELATIONAL MAPPING
//    // Cascade ALL: If I delete the Cart, the items disappear automatically.
//    // Orphan Removal: If I remove an item from this list, DB deletes the row.
//    @OneToMany(mappedBy = "cart", cascade = [CascadeType.ALL], orphanRemoval = true)
//    var seats: MutableList<CartSeat> = mutableListOf()
//
//    @OneToMany(mappedBy = "cart", cascade = [CascadeType.ALL], orphanRemoval = true)
//    var tables: MutableList<CartTable> = mutableListOf()
//
//    // Helper to calculate total price in memory
//    fun getTotalPrice(): BigDecimal {
//        val seatTotal = seats.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.unitPrice) }
//        val tableTotal = tables.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.unitPrice) }
//        return seatTotal.add(tableTotal).subtract(discountAmount ?: BigDecimal.ZERO)
//    }
//}
//2. The Child (CartSeat)
//Ensure you have the Foreign Key to the physical seat! This is your safety line.
//
//Kotlin
//
//@Entity
//@Table(name = "cart_seats", ...)
//class CartSeat(
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "cart_id", nullable = false)
//    var cart: Cart,
//
//    @Column(name = "seat_id", nullable = false)
//    var seatId: Long,
//    // ^^^ You COULD make this a @ManyToOne to ChartSeat entity if you want
//    // strict DB constraints, but storing the Long ID is acceptable for performance
//    // as long as you trust your app logic.
//    // Ideally: Use @ManyToOne to ChartSeat for maximum safety.
//
//) : AbstractLongEntity()
//3. The "Entity Graph" Trick (Performance Secret)
//To solve the "N+1 Select Problem" (the slowness I was worried about), you don't need JSON. You just need a JPA Entity Graph.
//
//When you load the cart for the Checkout page, you want the Cart + Seats + Tables in one single query.
//
//In your Repository:
//
//Kotlin
//
//interface CartRepository : JpaRepository<Cart, UUID> {
//
//    // This annotation forces Hibernate to do a JOIN FETCH
//    // It grabs the Cart and all its items in ONE SQL call.
//    @EntityGraph(attributePaths = ["seats", "tables"])
//    fun findByToken(token: UUID): Optional<Cart>
//}

/**
 * Represents a temporary shopping cart session.
 *
 * @param sessionId The [UUID] of the EventSession.
 * @param expiresAt The absolute timestamp when the cart reservations expire.
 * @param token A unique public token for API access.
 * @param userId The [UUID] of the authenticated user (nullable).
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
    var discountAmount: java.math.BigDecimal? = null

) : AbstractUuidEntity() {

    @Column(name = "last_activity_at", nullable = false)
    @Access(AccessType.FIELD)
    var lastActivityAt: Instant = Instant.now()
        protected set

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    /**
     * Extends the cart's expiration time and updates its activity.
     * Respects a hard limit relative to creation time to prevent infinite holding.
     */
    fun extendExpiration(minutes: Long, maxTtlMinutes: Long) {
        val now = Instant.now()
        val proposed = now.plusSeconds(minutes * 60)
        val hardLimit = this.createdAt.plusSeconds(maxTtlMinutes * 60)

        this.expiresAt = if (proposed.isAfter(hardLimit)) hardLimit else proposed
        this.lastActivityAt = now
    }

    /**
     * Updates the last activity time without extending expiration.
     */
    fun touch() {
        this.lastActivityAt = Instant.now()
    }
}
