package app.venues.booking.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
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
 * @property gaAreaId GA area ID from seating module
 * @property unitPrice Snapshotted price per ticket (captured at add-to-cart time)
 * @property quantity Number of tickets selected
 */
@Entity
@Table(name = "cart_ga_item")
class CartItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: Cart,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "ga_area_id", nullable = false)
    var gaAreaId: Long,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    ) : AbstractLongEntity()
