package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * Represents a General Admission (GA) ticket selection within a [Cart].
 *
 * @param cart The parent Cart.
 * @param sessionId The EventSession UUID.
 * @param levelId The ID of the Level (GA Area).
 * @param unitPrice The snapshotted price per ticket.
 * @param quantity The number of tickets selected.
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

    @Column(nullable = false)
    var quantity: Int,

    ) : AbstractLongEntity()