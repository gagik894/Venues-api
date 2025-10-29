package app.venues.booking.domain

import app.venues.event.domain.SessionSeatConfig
import app.venues.seating.domain.Level
import app.venues.seating.domain.Seat
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant

/**
 * Booking item entity - items within a booking.
 *
 * Can be either:
 * - A seat (quantity = 1)
 * - GA tickets for a level (quantity > 0)
 *
 * One booking can have multiple seats AND multiple GA levels.
 */
@Entity
@Table(
    name = "booking_items",
    indexes = [
        Index(name = "idx_booking_item_booking_id", columnList = "booking_id"),
        Index(name = "idx_booking_item_seat_id", columnList = "seat_id"),
        Index(name = "idx_booking_item_level_id", columnList = "level_id"),
        Index(name = "idx_booking_item_config_id", columnList = "session_seat_config_id")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class BookingItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, columnDefinition = "UUID")
    var booking: Booking,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    var seat: Seat? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    var level: Level? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_seat_config_id")
    var sessionSeatConfig: SessionSeatConfig? = null,

    @Column(nullable = false)
    var quantity: Int = 1,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    @Column(name = "price_template_name", length = 100)
    var priceTemplateName: String? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    fun getTotalPrice(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))

    fun isSeat(): Boolean = seat != null

    fun isGA(): Boolean = level != null
}

