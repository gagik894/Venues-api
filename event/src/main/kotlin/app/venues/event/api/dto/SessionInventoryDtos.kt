package app.venues.event.api.dto

import java.util.*

/**
 * Lightweight session inventory response containing only dynamic state.
 *
 * This response is designed to be used alongside the static chart structure:
 * - Static structure: GET /seating-charts/{chartId}/structure (cached 24h)
 * - Dynamic inventory: GET /sessions/{sessionId}/inventory (real-time)
 *
 * Maps are keyed by item ID or code for efficient client-side merging.
 * NO geometry (X, Y, rotation) is included - that comes from static structure.
 */
data class SessionInventoryResponse(
    val sessionId: UUID,
    val eventId: UUID,
    val seatingChartId: UUID,

    /**
     * Seat states keyed by seat ID.
     * Client merges with static structure using ID matching.
     */
    val seats: Map<Long, SeatStateDto>,

    /**
     * Table states keyed by table ID.
     */
    val tables: Map<Long, TableStateDto>,

    /**
     * GA area states keyed by GA area ID.
     */
    val gaAreas: Map<Long, GaAreaStateDto>,

    /**
     * Price templates for the event/session.
     * Clients use templateName to display pricing labels.
     */
    val priceTemplates: List<InventoryPriceTemplateDto>,

    /**
     * Quick availability statistics.
     */
    val stats: InventoryStatsDto
)

/**
 * Lightweight seat state DTO.
 * NO geometry - only status, pricing, and display info.
 */
data class SeatStateDto(
    /**
     * Short status code: A=Available, R=Reserved, S=Sold, B=Blocked
     */
    val status: String,

    /**
     * Price in smallest currency unit (cents) for efficient transfer.
     * Null if not priced.
     */
    val price: Long?,

    /**
     * Display color from price template (hex code).
     */
    val color: String?,

    /**
     * Price template name for display (e.g., "VIP", "Standard").
     */
    val templateName: String?
)

/**
 * Lightweight table state DTO.
 * NO geometry - only status, pricing, and booking info.
 */
data class TableStateDto(
    /**
     * Short status code: A=Available, R=Reserved, S=Sold, B=Blocked
     */
    val status: String,

    /**
     * Price in smallest currency unit (cents).
     * For TABLE_ONLY mode, this is the whole table price.
     */
    val price: Long?,

    /**
     * Display color from price template (hex code).
     */
    val color: String?,

    /**
     * Price template name for display.
     */
    val templateName: String?,

    /**
     * Booking mode: TABLE_ONLY, SEATS_ONLY, FLEXIBLE
     */
    val bookingMode: String
)

/**
 * Lightweight GA area state DTO.
 * NO geometry - only status, capacity, and pricing.
 */
data class GaAreaStateDto(
    /**
     * Short status code: A=Available, B=Blocked
     */
    val status: String,

    /**
     * Remaining capacity (capacity - soldCount).
     */
    val available: Int,

    /**
     * Number of tickets sold.
     */
    val soldCount: Int,

    /**
     * Price in smallest currency unit (cents).
     */
    val price: Long?,

    /**
     * Display color from price template (hex code).
     */
    val color: String?,

    /**
     * Price template name for display.
     */
    val templateName: String?
)

/**
 * Price template for inventory display.
 */
data class InventoryPriceTemplateDto(
    val id: UUID,
    val templateName: String,
    val color: String?,

    /**
     * Price in smallest currency unit (cents).
     */
    val price: Long,

    /**
     * True if this is a session-specific override of an event template.
     */
    val isOverride: Boolean
)

/**
 * Quick inventory statistics.
 */
data class InventoryStatsDto(
    val totalSeats: Int,
    val availableSeats: Int,
    val reservedSeats: Int,
    val soldSeats: Int,
    val blockedSeats: Int,
    val totalGaCapacity: Int,
    val availableGaCapacity: Int
)

/**
 * Lightweight availability response for quick status checks.
 * Used for event listings, availability badges, dashboard statistics.
 */
data class SeatAvailabilityResponse(
    val sessionId: UUID,
    val totalSeats: Int,
    val availableSeats: Int,
    val soldSeats: Int,
    val reservedSeats: Int,
    val blockedSeats: Int,
    val totalGaCapacity: Int,
    val availableGaCapacity: Int
)
