package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * Seat in shopping cart.
 *
 * Prices are snapshotted at add-to-cart time.
 * Expires when the parent Cart session expires.
 */
@Entity
@Table(
    name = "cart_seats",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_cart_seat_session_seat", columnNames = ["session_id", "seat_id"])
    ],
    indexes = [
        Index(name = "idx_cart_seat_session_id", columnList = "session_id"),
        Index(name = "idx_cart_seat_cart", columnList = "cart_id")
    ]
)
class CartSeat(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: Cart,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "seat_id", nullable = false)
    var seatId: Long,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,
) : AbstractLongEntity()

