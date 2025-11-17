package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * A GA ticket item in a shopping cart.
 * This is a high-volume child entity.
 *
 * @param cart The parent cart.
 * @param sessionId The `EventSession.id`.
 * @param levelId The `Level.id` (a Long).
 * @param unitPrice The snapshotted price.
 * @param quantity The number of tickets.
 */
@Entity
@Table(name = "cart_gas")
class CartItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: Cart,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "level_id", nullable = false)
    var levelId: Long,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    @Column(nullable = false)
    var quantity: Int,

    ) : AbstractLongEntity()