package app.venues.event.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Session table configuration.
 *
 * Manages table bookings - tracks whether a table is available as a whole unit.
 * A table (Level with isTable=true) contains multiple seats that can be:
 * - Booked individually (if allowed by tableBookingMode)
 * - Booked as a complete table (if allowed by tableBookingMode)
 *
 * Business Rules:
 * - If ANY seat in the table is RESERVED or SOLD → table status becomes BLOCKED
 * - If table is RESERVED or SOLD → all individual seats become BLOCKED
 * - Table has its own price (can differ from sum of seat prices)
 *
 * Cross-module relationships:
 * - tableId references Level entity in seating module (Level.isTable = true)
 */
@Entity
@Table(
    name = "session_table_configs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_session_table_config", columnNames = ["session_id", "table_id"])
    ],
    indexes = [
        Index(name = "idx_session_table_config_session", columnList = "session_id"),
        Index(name = "idx_session_table_config_table", columnList = "table_id"),
        Index(name = "idx_session_table_config_template", columnList = "price_template_id"),
        Index(name = "idx_session_table_config_status", columnList = "status")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class SessionTableConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    /**
     * Table ID - references Level entity with isTable = true
     */
    @Column(name = "table_id", nullable = false)
    var tableId: Long,

    /**
     * Price template for booking the entire table
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_template_id")
    var priceTemplate: EventPriceTemplate? = null,

    /**
     * Table status
     * - AVAILABLE: Table can be booked as a unit
     * - RESERVED: Table is in someone's cart
     * - SOLD: Table has been purchased
     * - BLOCKED: One or more seats are reserved/sold, table unavailable
     * - CLOSED: Manually closed by venue
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: ConfigStatus = ConfigStatus.AVAILABLE,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE
    fun isPriced(): Boolean = priceTemplate != null
    fun isBlocked(): Boolean = status == ConfigStatus.BLOCKED
}

