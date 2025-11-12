package app.venues.booking.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant

/**
 * Table booking in shopping cart.
 *
 * Represents a whole table reservation (all seats in the table as a unit).
 * Price is snapshotted at add-to-cart time.
 * Expires when the parent Cart session expires.
 *
 * Business Rules:
 * - One cart can have multiple tables from different areas
 * - Cannot have individual seats from a table if the table itself is in cart
 * - When table is added, all its seats are marked as BLOCKED in session_seat_configs
 */
@Entity
@Table(
    name = "cart_tables",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_cart_table_session_table", columnNames = ["session_id", "table_id"])
    ],
    indexes = [
        Index(name = "idx_cart_table_session_id", columnList = "session_id"),
        Index(name = "idx_cart_table_cart", columnList = "cart_id"),
        Index(name = "idx_cart_table_table_id", columnList = "table_id")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class CartTable(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: Cart,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long,

    /**
     * Table ID - references Level entity with isTable = true
     */
    @Column(name = "table_id", nullable = false)
    var tableId: Long,

    /**
     * Table price (snapshotted at add-to-cart time)
     */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    /**
     * Number of seats in this table (for display)
     */
    @Column(name = "seat_count", nullable = false)
    var seatCount: Int,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)

