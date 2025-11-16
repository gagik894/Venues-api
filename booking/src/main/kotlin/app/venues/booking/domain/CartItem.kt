package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * GA ticket in shopping cart.
 *
 * Prices are snapshotted at add-to-cart time.
 * Expires when the parent Cart session expires.
 */
@Entity
@Table(
    name = "cart_items",
    indexes = [
        Index(name = "idx_cart_item_session_id", columnList = "session_id"),
        Index(name = "idx_cart_item_cart", columnList = "cart_id")
    ]
)
class CartItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: Cart,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long,

    @Column(name = "level_id", nullable = false)
    var levelId: Long,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    @Column(nullable = false)
    var quantity: Int,
) : AbstractLongEntity() {
    fun getTotalPrice(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))
}



