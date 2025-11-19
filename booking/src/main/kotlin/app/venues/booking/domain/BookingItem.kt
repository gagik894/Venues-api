package app.venues.booking.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * Represents a confirmed booking line item.
 * Can be either an individual seat OR general admission tickets.
 *
 * Note: Tables are stored in cart but not yet implemented in bookings.
 *
 * @property booking The parent booking
 * @property quantity Number of units (1 for seats, >=1 for GA tickets)
 * @property unitPrice Price per unit at booking time (snapshot pricing)
 * @property seatId Seat ID from seating module (for seat bookings)
 * @property gaAreaId GA area ID from seating module (for GA bookings)
 * @property priceTemplateName Price template name for display (e.g., "VIP", "Standard")
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
     * Seat ID from seating module (populated for seat bookings).
     */
    @Column(name = "seat_id")
    var seatId: Long? = null,

    /**
     * GA area ID from seating module (populated for GA ticket bookings).
     */
    @Column(name = "ga_area_id")
    var gaAreaId: Long? = null,

    @Column(name = "price_template_name", length = 100)
    var priceTemplateName: String? = null

) : AbstractLongEntity() {

    /**
     * Calculate total price for this line item.
     */
    fun getTotalPrice(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))

    /**
     * Check if this is a seat booking.
     */
    fun isSeat(): Boolean = seatId != null

    /**
     * Check if this is a GA ticket booking.
     */
    fun isGA(): Boolean = gaAreaId != null && seatId == null
}
