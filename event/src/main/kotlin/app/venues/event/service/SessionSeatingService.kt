package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.*
import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.EventSession
import app.venues.event.domain.SessionSeatConfig
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import app.venues.shared.money.toMoney
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for session-specific inventory (Split Strategy - Dynamic Layer).
 *
 * Architecture (Split Strategy for 10k+ seat venues):
 * - Static Structure: GET /seating-charts/{chartId}/structure (cached 24h)
 * - Dynamic Inventory: GET /sessions/{sessionId}/inventory (real-time, this service)
 *
 * This service provides ONLY dynamic state (status, pricing) without geometry.
 * Client merges with cached static structure using ID matching.
 *
 * Performance optimizations:
 * - Zero calls to SeatingApi for inventory queries
 * - Prices in cents (Long) for efficient JSON transfer
 * - Short status codes (A/R/S/B/C) to reduce payload
 * - ID-keyed maps for O(1) client-side lookup
 */
@Service
@Transactional
class SessionSeatingService(
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionGAConfigRepository: SessionGAConfigRepository,
    private val sessionTableConfigRepository: SessionTableConfigRepository,
    private val eventSessionRepository: EventSessionRepository,
    private val seatingApi: SeatingApi
) {

    private val logger = KotlinLogging.logger {}

    // =================================================================================
    // DYNAMIC INVENTORY API (Split Strategy)
    // =================================================================================

    /**
     * Get lightweight session inventory without geometry.
     *
     * Returns only dynamic state (status, pricing) keyed by ID for client-side
     * merging with cached static structure from /seating-charts/{chartId}/structure.
     *
     * This endpoint is designed for high-frequency polling:
     * - No geometry data (X, Y, rotation)
     * - Maps keyed by ID for O(1) lookup
     * - Prices in cents for efficient transfer
     * - Short status codes (A/R/S/B/C)
     *
     * Uses Waterfall pricing logic: Seat Config > Session Override > Event Template
     */
    @Transactional(readOnly = true)
    fun getSessionInventory(sessionId: UUID): SessionInventoryResponse {
        logger.debug { "Fetching session inventory for session: $sessionId" }

        val session = getSessionOrThrow(sessionId)
        val event = session.event
        val seatingChartId = event.seatingChartId
            ?: throw VenuesException.ValidationFailure("Event does not have a seating chart assigned")

        // Batch load all session configs (3 queries total - no calls to seating module)
        val seatConfigs = sessionSeatConfigRepository.findBySessionId(sessionId)
        val gaConfigs = sessionGAConfigRepository.findBySessionId(sessionId)
        val tableConfigs = sessionTableConfigRepository.findBySessionId(sessionId)

        // Build category key to template map for waterfall pricing
        val categoryTemplateMap = event.priceTemplates.associateBy { it.templateName }
        val sessionOverrideMap = session.priceTemplateOverrides.associateBy { it.templateName }

        // Map seat states
        val seatStates = seatConfigs.associate { config ->
            val (price, templateName, color) = resolveSeatPricingFromConfig(config, sessionOverrideMap)
            config.seatId to SeatStateDto(
                status = config.status.toShortCode(),
                templateName = templateName
            )
        }

        // Map table states
        val tableStates = tableConfigs.associate { config ->
            val template = config.priceTemplate

            config.tableId to TableStateDto(
                status = config.status.toShortCode(),
                templateName = template?.templateName,
                bookingMode = config.bookingMode.name
            )
        }

        // Map GA area states
        val gaStates = gaConfigs.associate { config ->
            val template = config.priceTemplate
            val override = template?.let { sessionOverrideMap[it.templateName] }
            val finalPrice = override?.price ?: template?.price
            val available = config.getAvailableCapacity() ?: 0

            config.gaAreaId to GaAreaStateDto(
                status = config.status.toShortCode(),
                available = available,
                soldCount = config.soldCount,
                templateName = template?.templateName
            )
        }

        // Build price templates list
        val priceTemplates = event.priceTemplates.map { template ->
            val override = sessionOverrideMap[template.templateName]
            InventoryPriceTemplateDto(
                id = template.id,
                templateName = template.templateName,
                color = template.color,
                price = override?.price?.toMoney(event.currency) ?: template.price.toMoney(event.currency),
                isOverride = override != null
            )
        }

        // Calculate stats (using optimized seat count from seating module)
        val totalSeats = seatingApi.getSeatCount(seatingChartId)
        val unavailableSeats = seatConfigs.count { !it.isAvailable() }
        val availableSeats = (totalSeats - unavailableSeats).coerceAtLeast(0)
        val reservedSeats = seatConfigs.count { it.status.name == "RESERVED" }
        val soldSeats = seatConfigs.count { it.status.name == "SOLD" }
        val blockedSeats = seatConfigs.count { it.status.name == "BLOCKED" }
        val totalGaCapacity = gaConfigs.sumOf { it.capacity ?: 0 }
        val availableGaCapacity = gaConfigs.sumOf { config ->
            if (config.status == ConfigStatus.AVAILABLE || config.status == ConfigStatus.RESERVED) {
                config.getAvailableCapacity() ?: 0
            } else 0
        }

        val stats = InventoryStatsDto(
            totalSeats = totalSeats,
            availableSeats = availableSeats,
            reservedSeats = reservedSeats,
            soldSeats = soldSeats,
            blockedSeats = blockedSeats,
            totalGaCapacity = totalGaCapacity,
            availableGaCapacity = availableGaCapacity
        )

        logger.info { "Session inventory loaded: $totalSeats seats, ${tableConfigs.size} tables, ${gaConfigs.size} GA areas" }

        return SessionInventoryResponse(
            sessionId = sessionId,
            eventId = event.id,
            seatingChartId = seatingChartId,
            seats = seatStates,
            tables = tableStates,
            gaAreas = gaStates,
            priceTemplates = priceTemplates,
            stats = stats
        )
    }

    /**
     * Get quick availability statistics.
     * Optimized for high-frequency polling without full structure.
     */
    @Transactional(readOnly = true)
    fun getSessionAvailability(sessionId: UUID): SeatAvailabilityResponse {
        logger.debug { "Fetching seat availability for session: $sessionId" }

        val session = getSessionOrThrow(sessionId)
        val event = session.event
        val seatingChartId = event.seatingChartId
            ?: throw VenuesException.ValidationFailure("Event does not have a seating chart assigned")

        // Use optimized COUNT query instead of fetching full structure
        val totalSeats = seatingApi.getSeatCount(seatingChartId).toInt()
        val seatConfigs = sessionSeatConfigRepository.findBySessionId(sessionId)

        // Count by status
        val statusCounts = seatConfigs.groupBy { it.status.name }
        val soldSeats = statusCounts["SOLD"]?.size ?: 0
        val reservedSeats = statusCounts["RESERVED"]?.size ?: 0
        val blockedSeats = statusCounts["BLOCKED"]?.size ?: 0
        val closedSeats = statusCounts["CLOSED"]?.size ?: 0
        val unavailableSeats = soldSeats + blockedSeats + closedSeats
        val availableSeats = (totalSeats - unavailableSeats).coerceAtLeast(0)

        // GA area stats
        val gaConfigs = sessionGAConfigRepository.findBySessionId(sessionId)
        val totalGaCapacity = gaConfigs.sumOf { (it.capacity ?: 0) }
        val soldGaCount = gaConfigs.sumOf { it.soldCount }
        val availableGaCapacity = gaConfigs.sumOf { config ->
            if (config.status == ConfigStatus.AVAILABLE || config.status == ConfigStatus.RESERVED) {
                (config.capacity ?: 0) - config.soldCount
            } else 0
        }.coerceAtLeast(0)

        return SeatAvailabilityResponse(
            sessionId = sessionId,
            totalSeats = totalSeats,
            availableSeats = availableSeats,
            soldSeats = soldSeats,
            reservedSeats = reservedSeats,
            blockedSeats = blockedSeats,
            totalGaCapacity = totalGaCapacity,
            availableGaCapacity = availableGaCapacity
        )
    }

    // =================================================================================
    // PRIVATE HELPERS
    // =================================================================================

    private fun getSessionOrThrow(sessionId: UUID): EventSession {
        return eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Event session not found") }
    }

    /**
     * Resolves pricing from config using waterfall logic.
     */
    private fun resolveSeatPricingFromConfig(
        config: SessionSeatConfig,
        sessionOverrideMap: Map<String, app.venues.event.domain.EventSessionPriceOverride>
    ): Triple<java.math.BigDecimal?, String?, String?> {
        val template = config.priceTemplate ?: return Triple(null, null, null)
        val override = sessionOverrideMap[template.templateName]
        val finalPrice = override?.price ?: template.price
        return Triple(finalPrice, template.templateName, template.color)
    }
}

/**
 * Extension to convert ConfigStatus to short code for efficient transfer.
 */
private fun app.venues.event.domain.ConfigStatus.toShortCode(): String = when (this) {
    app.venues.event.domain.ConfigStatus.AVAILABLE -> "A"
    app.venues.event.domain.ConfigStatus.RESERVED -> "R"
    app.venues.event.domain.ConfigStatus.SOLD -> "S"
    app.venues.event.domain.ConfigStatus.BLOCKED -> "B"
    app.venues.event.domain.ConfigStatus.CLOSED -> "C"
}
