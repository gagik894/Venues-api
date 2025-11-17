package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * A single seat item in a shopping cart.
 * This is a high-volume child entity.
 *
 * @param cart The parent cart.
 * @param sessionId The `EventSession.id`.
 * @param seatId The `Seat.id` (a Long).
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