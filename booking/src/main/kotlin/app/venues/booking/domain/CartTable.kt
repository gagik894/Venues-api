package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * A full table item in a shopping cart.
 * This is a high-volume child entity.
 *
 * @param cart The parent cart.
 * @param sessionId The `EventSession.id`.
 * @param tableId The `Level.id` (a Long) of the table.
 * @param unitPrice The snapshotted price for the whole table.
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