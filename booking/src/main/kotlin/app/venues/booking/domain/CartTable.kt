package app.venues.booking.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * Represents a complete table booking in a shopping cart.
 *
 * Tables are groups of seats that can be booked as a complete unit.
 * When a table is added to cart, all its individual seats are blocked.
 *
 * @property cart The parent cart
 * @property sessionId Event session ID
 * @property tableId Table ID from seating module
 * @property unitPrice Snapshotted price for the entire table (captured at add-to-cart time)
 */
@Entity
@Table(
    name = "cart_tables",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_cart_table_session_table", columnNames = ["session_id", "table_id"])
    ]
)
class CartTable(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: Cart,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "table_id", nullable = false)
    var tableId: Long,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,
) : AbstractLongEntity()
