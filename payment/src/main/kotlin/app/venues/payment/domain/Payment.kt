package app.venues.payment.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.*

/**
 * Represents a payment transaction record in the system.
 *
 * This entity stores the state and details of a payment attempt associated with a booking.
 * It tracks the amount, currency, status, and external references to payment providers.
 *
 * Design Decisions:
 * - Uses UUIDv7 (via AbstractUuidEntity) for time-ordered primary keys.
 * - Decoupled from Booking entity via ID reference (Modular Monolith pattern).
 * - Stores explicit column names to avoid reliance on naming strategies.
 *
 * @property bookingId The UUID of the booking this payment pays for.
 * @property amount The monetary amount of the payment.
 * @property currency The 3-letter ISO 4217 currency code (e.g., "USD", "EUR").
 * @property status The current status of the payment (PENDING, COMPLETED, FAILED, REFUNDED).
 * @property externalReference The ID or reference string provided by the external payment gateway (e.g., Stripe Intent ID).
 * @property merchantId The ID of the merchant profile used for this transaction.
 */
@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payment_booking_id", columnList = "booking_id"),
        Index(name = "idx_payment_external_ref", columnList = "external_reference")
    ]
)
class Payment(
    @Column(name = "booking_id", nullable = false)
    var bookingId: UUID,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    var currency: String,

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "PENDING",

    @Column(name = "external_reference", length = 255)
    var externalReference: String? = null,

    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID,

    @Column(name = "provider_id", nullable = false, length = 50)
    var providerId: String

) : AbstractUuidEntity()
