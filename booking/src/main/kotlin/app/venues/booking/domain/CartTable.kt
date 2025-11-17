package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * Represents a full Table selection within a [Cart].
 *
 * @param cart The parent Cart.
 * @param sessionId The EventSession UUID.
 * @param tableId The ID of the Level (Table).
 * @param unitPrice The snapshotted price for the entire table.
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