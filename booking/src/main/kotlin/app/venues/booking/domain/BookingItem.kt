package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * Booking item entity - items within a booking.
 *
 * Can be either:
 * - A seat (quantity = 1)
 * - GA tickets for a level (quantity > 0)
 *
 * One booking can have multiple seats AND multiple GA levels.
 *
 * Cross-module relationships:
 * - seatId references seating module
 * - levelId references seating module
 * - sessionSeatConfigId references event module
 */
@Entity
@Table(
    name = "booking_items",
    indexes = [
        Index(name = "idx_booking_item_booking_id", columnList = "booking_id"),
        Index(name = "idx_booking_item_seat_id", columnList = "seat_id"),
        Index(name = "idx_booking_item_level_id", columnList = "level_id"),
    ]
)
class BookingItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, columnDefinition = "UUID")
    var booking: Booking,

    /**
     * Seat ID - references seating module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "seat_id")
    var seatId: Long? = null,

    /**
     * Level ID - references seating module (for GA tickets)
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "level_id")
    var levelId: Long? = null,

    @Column(nullable = false)
    var quantity: Int = 1,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    @Column(name = "price_template_name", length = 100)
    var priceTemplateName: String? = null,
) : AbstractLongEntity() {
    fun getTotalPrice(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))

    fun isSeat(): Boolean = seatId != null

    fun isGA(): Boolean = levelId != null
}

