package app.venues.event.api.dto

import java.util.*

/**
 * Response DTO for session seating chart with pricing and availability.
 *
 * Simplified structure with flat seats array for easy UI rendering.
 * Optimized for large venues (10k+ seats).
 */
data class SessionSeatingResponse(
    val sessionId: UUID,
    val eventId: UUID,
    val eventTitle: String,
    val seatingChartId: UUID,
    val seatingChartName: String,
    val chartWidth: Int,
    val chartHeight: Int,
    val priceTemplates: List<SessionPriceTemplateResponse>,
    val zones: List<SessionZoneResponse>,

    // Flat array of all seats - easy to render
    val seats: List<SessionSeatResponse>,

    // GA (General Admission) areas
    val gaAreas: List<SessionGaAreaResponse>,

    // Tables (complete booking units)
    val tables: List<SessionTableResponse>,

    // Statistics
    val totalSeats: Int,
    val availableSeats: Int,
    val soldSeats: Int
)

/**
 * Seat with embedded level hierarchy.
 * Simple flat structure for UI rendering.
 */
data class SessionSeatResponse(
    val code: String,
    val seatNumber: String?,
    val rowLabel: String?,

    val isBookable: Boolean,

    // Level hierarchy from root to leaf (e.g., ["Orchestra", "Row A"])
    val levels: List<String>,
    val zoneCode: String,
    val tableCode: String,
    val categoryKey: String,
    val isAccessible: Boolean,
    val isObstructed: Boolean,
    val positionX: Double?,
    val positionY: Double?,
    val rotation: Double?,

    // Session-specific data
    val price: String?,
    val priceTemplateName: String?,
    val priceTemplateColor: String?
)

/**
 * General Admission area with capacity.
 */
data class SessionGaAreaResponse(
    val code: String,
    val levelName: String,

    val isBookable: Boolean,

    val levels: List<String>,  // Hierarchy (e.g., ["Balcony", "Standing Area"])
    val zoneCode: String,
    val capacity: Int,
    val available: Int,
    val price: String?,
    val priceTemplateName: String?,
    val boundaryPath: String?,
    val displayColor: String?
)

/**
 * Table booking information.
 * Represents a group of seats that can be booked as a complete unit.
 */
data class SessionTableResponse(
    val tableName: String,
    val code: String,

    val isBookable: Boolean,

    // Level hierarchy (e.g., ["VIP Lounge", "VIP Table 1"])
    val levels: List<String>,
    val zoneCode: String,
    val positionX: Double?,
    val positionY: Double?,
    // Table booking configuration
    val width: Double?,
    val height: Double?,
    val rotation: Double?,
    val shape: String?,
    val bookingMode: String,  // TABLE_ONLY, FLEXIBLE, SEATS_ONLY
    val seatCount: Int,
    val seatCodes: List<String>,
    val status: String,
    val price: String?,
    val priceTemplateName: String?,
    val priceTemplateColor: String?
)

data class SessionZoneResponse(
    val name: String,
    val code: String,
    val parentZoneCode: String,
    val positionX: Double?,
    val positionY: Double?,
    val rotation: Double?,
    val boundaryPath: String?,
    val displayColor: String?
)

/**
 * Price template for the session.
 */
data class SessionPriceTemplateResponse(
    val id: UUID,
    val templateName: String,
    val color: String?,
    val price: String,
    val isOverride: Boolean  // true if session-specific override
)

/**
 * Lightweight response for checking seat availability.
 */
data class SeatAvailabilityResponse(
    val sessionId: UUID,
    val totalSeats: Int,
    val availableSeats: Int,
    val soldSeats: Int,
    val reservedSeats: Int,
    val availableSeatIdentifiers: List<String>  // Only for small venues
)

/**
 * Request DTO for assigning price templates to inventory items.
 */
data class AssignPriceTemplateRequest(
    val templateId: UUID?, // Null to clear price
    val seatIds: List<Long> = emptyList(),
    val tableIds: List<Long> = emptyList(),
    val gaIds: List<Long> = emptyList()
)

