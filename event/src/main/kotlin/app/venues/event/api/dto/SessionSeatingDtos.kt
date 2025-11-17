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
    val priceTemplates: List<SessionPriceTemplateResponse>,

    // Flat array of all seats - easy to render
    val seats: List<SessionSeatResponse>,

    // GA (General Admission) areas
    val gaAreas: List<SessionGAAreaResponse>,

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
    val seatIdentifier: String,
    val seatNumber: String?,
    val rowLabel: String?,

    // Level hierarchy from root to leaf (e.g., ["Orchestra", "Row A"])
    val levels: List<String>,

    // Position for rendering
    val positionX: Double?,
    val positionY: Double?,

    // Session-specific data
    val status: String,  // AVAILABLE, RESERVED, SOLD, CLOSED, BLOCKED
    val price: String?,
    val priceTemplateName: String?,
    val priceTemplateColor: String?
)

/**
 * General Admission area with capacity.
 */
data class SessionGAAreaResponse(
    val levelIdentifier: String,
    val levelName: String,
    val levels: List<String>,  // Hierarchy (e.g., ["Balcony", "Standing Area"])
    val capacity: Int,
    val available: Int,
    val price: String?,
    val priceTemplateName: String?
)

/**
 * Table booking information.
 * Represents a group of seats that can be booked as a complete unit.
 */
data class SessionTableResponse(
    val tableId: Long,
    val tableName: String,
    val tableIdentifier: String?,

    // Level hierarchy (e.g., ["VIP Lounge", "VIP Table 1"])
    val levels: List<String>,

    // Position for rendering
    val positionX: Double?,
    val positionY: Double?,

    // Table booking configuration
    val bookingMode: String,  // TABLE_ONLY, FLEXIBLE, SEATS_ONLY
    val seatCount: Int,
    val seatIdentifiers: List<String>,  // All seat IDs in this table

    // Session-specific data
    val status: String,  // AVAILABLE, RESERVED, SOLD, BLOCKED
    val price: String?,
    val priceTemplateName: String?,
    val priceTemplateColor: String?
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

