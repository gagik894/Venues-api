package app.venues.booking.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * Represents a specific line item within a [Booking].
 * This entity maps to either a specific [Seat] or a quantity of General Admission (GA) tickets.
 *
 * @param booking The parent [Booking].
 * @param quantity The number of units (always 1 for seats, >=1 for GA).
 * @param unitPrice The price per unit at the time of booking.
 * @param seatId The ID of the specific [Seat] (if applicable).
 * @param levelId The ID of the [Level] (if this is a GA ticket or a table).
 * @param priceTemplateName The name of the price template applied (e.g., "VIP").
 */
@Entity
@Table(name = "booking_items")
class BookingItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    var booking: Booking,

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 1,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    /**
     * The `Seat.id` (a Long), if this is a seat.
     */
    @Column(name = "seat_id")
    var seatId: Long? = null,

    /**
     * The `Level.id` (a Long), if this is GA or Tables.
     */
    @Column(name = "level_id")
    var levelId: Long? = null,

    @Column(name = "price_template_name", length = 100)
    var priceTemplateName: String? = null,

    ) : AbstractLongEntity() {

    fun getTotalPrice(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))

    fun isSeat(): Boolean = seatId != null

    fun isGA(): Boolean = levelId != null
}