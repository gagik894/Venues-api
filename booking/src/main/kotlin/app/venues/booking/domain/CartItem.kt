package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * Represents General Admission (GA) ticket selections in a shopping cart.
 *
 * GA areas are standing/general admission zones from the seating module
 * with capacity tracking (e.g., "Pit A", "Standing Area").
 *
 * @property cart The parent cart
 * @property sessionId Event session ID
 * @property levelId GA area ID from seating module
 * @property unitPrice Snapshotted price per ticket (captured at add-to-cart time)
 * @property quantity Number of tickets selected
 */
@Entity
@Table(name = "cart_item")
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

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    ) : AbstractLongEntity()