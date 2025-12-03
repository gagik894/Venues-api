package app.venues.event.api.dto

import app.venues.shared.money.MoneyAmount
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
    val seats: Map<Long, SeatStateDto>,
    val tables: Map<Long, TableStateDto>,
    val gaAreas: Map<Long, GaAreaStateDto>,
    val priceTemplates: List<InventoryPriceTemplateDto>,
    val stats: InventoryStatsDto
)

/**
 * Lightweight seat state DTO.
 * NO geometry - only status, pricing, and display info.
 */
data class SeatStateDto(
    val status: String,
    val templateName: String?
)

/**
 * Lightweight table state DTO.
 * NO geometry - only status, pricing, and booking info.
 */
data class TableStateDto(
    val status: String,
    val templateName: String?,
    val bookingMode: String
)

/**
 * Lightweight GA area state DTO.
 * NO geometry - only status, capacity, and pricing.
 */
data class GaAreaStateDto(
    val status: String,
    val available: Int,
    val soldCount: Int,
    val templateName: String?
)

/**
 * Price template for inventory display.
 */
data class InventoryPriceTemplateDto(
    val id: UUID,
    val templateName: String,
    val color: String?,
    val price: MoneyAmount,
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
