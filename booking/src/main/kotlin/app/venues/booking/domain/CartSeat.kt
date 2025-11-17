package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * Represents a specific Seat selection within a [Cart].
 *
 * @param cart The parent Cart.
 * @param sessionId The EventSession UUID.
 * @param seatId The ID of the Seat.
 * @param unitPrice The snapshotted price.
 */
@Entity
@Table(
    name = "cart_seats",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_cart_seat_session_seat", columnNames = ["session_id", "seat_id"])
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