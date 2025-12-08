package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.event.api.dto.*
import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.EventPriceTemplate
import app.venues.event.domain.EventSession
import app.venues.event.domain.SessionSeatConfig
import app.venues.event.repository.*
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
class EventApiService(
    private val eventSessionRepository: EventSessionRepository,
    private val eventRepository: EventRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionGAConfigRepository: SessionGAConfigRepository,
    private val sessionTableConfigRepository: SessionTableConfigRepository,
    private val seatingApi: SeatingApi,
    private val seatConfigSparseService: SeatConfigSparseService,
    private val sessionSeatingService: SessionSeatingService,
    private val redisTemplate: StringRedisTemplate
) : EventApi {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    override fun getEventSessionInfo(sessionId: UUID): EventSessionDto? {
        val session = eventSessionRepository.findById(sessionId).getOrNull() ?: return null
        val event = session.event

        return EventSessionDto(
            sessionId = session.id,
            eventId = event.id,
            venueId = event.venueId,
            eventTitle = event.title,
            eventDescription = event.description,
            currency = event.currency,
            startTime = session.startTime,
            endTime = session.endTime
        )
    }

    @Transactional(readOnly = true)
    override fun getSessionInventory(sessionId: UUID): SessionInventoryResponse? {
        return try {
            sessionSeatingService.getSessionInventory(sessionId)
        } catch (ex: VenuesException.ResourceNotFound) {
            null
        }
    }

    @Transactional(readOnly = true)
    override fun getEventTitleTranslated(eventId: UUID, language: String?): String? {
        val event = eventRepository.findById(eventId).getOrNull() ?: return null

        // If no language specified or language is default, return default title
        if (language.isNullOrBlank()) {
            return event.title
        }

        // Try to find translation for requested language
        val translation = event.translations.find { it.language.equals(language, ignoreCase = true) }
        return translation?.title ?: event.title
    }

    @Transactional(readOnly = true)
    override fun getSessionIdsForEvent(eventId: UUID): List<UUID> {
        return eventSessionRepository.findSessionIdsByEventId(eventId)
    }

    @Transactional
    override fun reserveSeat(sessionId: UUID, seatId: Long): BigDecimal? {
        // Redis Guard: Prevent thundering herd on the database
        val lockKey = "lock:seat:$sessionId:$seatId"
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5))
        if (acquired != true) {
            return null // Fast fail: Seat is busy
        }

        try {
            // 1. Try atomic update first (handles "exists and available" case)
            val updatedRows = sessionSeatConfigRepository.reserveSeatAtomic(sessionId, seatId)

            if (updatedRows > 0) {
                // Successfully reserved existing row. Now fetch it to calculate price.
                val config = sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, seatId)
                    ?: throw VenuesException.ResourceNotFound("Config missing after reservation")

                val session = config.session
                val template = config.priceTemplate

                return if (template != null) {
                    val override = session.priceTemplateOverrides.find { it.templateName == template.templateName }
                    override?.price ?: template.price
                } else {
                    BigDecimal.ZERO
                }
            }

            // 2. Update failed. Check if row exists.
            val existing = sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, seatId)
            if (existing != null) {
                // Row exists but update failed -> it was not AVAILABLE
                return null
            }

            // 3. Row missing: Implicitly AVAILABLE -> Create RESERVED
            try {
                val session = eventSessionRepository.findById(sessionId).getOrNull() ?: return null
                val seatInfo = seatingApi.getSeatInfo(seatId) ?: return null

                // Resolve template
                val (price, template) = resolvePriceAndTemplate(session, seatInfo.categoryKey)

                if (template == null) {
                    // Cannot reserve if no price template
                    return null
                }

                val newConfig = SessionSeatConfig(
                    session = session,
                    seatId = seatId,
                    priceTemplate = template,
                    status = ConfigStatus.RESERVED
                )
                sessionSeatConfigRepository.saveAndFlush(newConfig)
                return price
            } catch (e: DataIntegrityViolationException) {
                // Race condition: someone else created the row
                return null
            }
        } finally {
            redisTemplate.delete(lockKey)
        }
    }

    private fun resolvePriceAndTemplate(
        session: EventSession,
        categoryKey: String
    ): Pair<BigDecimal?, EventPriceTemplate?> {
        val event = session.event
        val template = event.priceTemplates.find { it.templateName == categoryKey } ?: return null to null

        val override = session.priceTemplateOverrides.find { it.templateName == categoryKey }
        val price = override?.price ?: template.price

        return price to template
    }

    @Transactional
    override fun releaseSeat(sessionId: UUID, seatId: Long) {
        sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, seatId)?.let { config ->
            config.release()
            sessionSeatConfigRepository.save(config)
            seatConfigSparseService.purgeDefaultRows(sessionId, listOf(seatId))
        }
    }

    @Transactional
    override fun releaseSeatsBatch(sessionId: UUID, seatIds: List<Long>) {
        if (seatIds.isNotEmpty()) {
            sessionSeatConfigRepository.releaseSeats(sessionId, seatIds)
            seatConfigSparseService.purgeDefaultRows(sessionId, seatIds)
        }
    }

    @Transactional
    override fun reserveGa(sessionId: UUID, gaAreaId: Long, quantity: Int): BigDecimal? {
        return sessionGAConfigRepository.reserveGAAndGetPrice(sessionId, gaAreaId, quantity)
    }

    @Transactional
    override fun adjustGa(sessionId: UUID, gaAreaId: Long, quantityDelta: Int): Boolean {
        val rows = sessionGAConfigRepository.adjustGATickets(sessionId, gaAreaId, quantityDelta)
        return rows > 0
    }

    @Transactional
    override fun releaseGa(sessionId: UUID, gaAreaId: Long, quantity: Int) {
        sessionGAConfigRepository.findBySessionIdAndGaAreaId(sessionId, gaAreaId)?.let { config ->
            config.refund(quantity)
            sessionGAConfigRepository.save(config)
        }
    }

    @Transactional
    override fun releaseGaBatch(sessionId: UUID, gaAreaQuantities: Map<Long, Int>) {
        gaAreaQuantities.forEach { (gaAreaId, quantity) ->
            releaseGa(sessionId, gaAreaId, quantity)
        }
    }

    @Transactional(readOnly = true)
    override fun getGaAvailability(sessionId: UUID, gaAreaId: Long): GaAvailabilityDto? {
        return sessionGAConfigRepository.findBySessionIdAndGaAreaId(sessionId, gaAreaId)?.let {
            GaAvailabilityDto(
                capacity = it.capacity ?: 0,
                soldCount = it.soldCount
            )
        }
    }

    @Transactional
    override fun reserveTable(sessionId: UUID, tableId: Long): BigDecimal? {
        val rows = sessionTableConfigRepository.reserveTableIfAvailable(sessionId, tableId)
        if (rows == 0) return null
        return sessionTableConfigRepository.getTablePriceIfAvailable(sessionId, tableId)
    }

    @Transactional
    override fun releaseTable(sessionId: UUID, tableId: Long) {
        sessionTableConfigRepository.findBySessionIdAndTableId(sessionId, tableId)?.let { config ->
            config.release()
            sessionTableConfigRepository.save(config)
        }
    }

    @Transactional
    override fun releaseTablesBatch(sessionId: UUID, tableIds: List<Long>) {
        if (tableIds.isNotEmpty()) {
            sessionTableConfigRepository.releaseTables(sessionId, tableIds)
        }
    }

    @Transactional(readOnly = true)
    override fun getTableBookingMode(sessionId: UUID, tableId: Long): String? {
        return sessionTableConfigRepository.findBySessionIdAndTableId(sessionId, tableId)?.bookingMode?.name
    }

    @Transactional
    override fun blockSeats(sessionId: UUID, seatIds: List<Long>): Int {
        if (seatIds.isEmpty()) return 0

        // 1. Update existing rows (set to BLOCKED)
        val updated = sessionSeatConfigRepository.blockSeats(sessionId, seatIds)

        // 2. Handle missing rows (Sparse Matrix: Create them as BLOCKED)
        val existingConfigs = sessionSeatConfigRepository.findBySessionIdAndSeatIdIn(sessionId, seatIds)
        val existingSeatIds = existingConfigs.map { it.seatId }.toSet()

        val missingSeatIds = seatIds.filter { it !in existingSeatIds }

        if (missingSeatIds.isNotEmpty()) {
            val session = eventSessionRepository.findById(sessionId).getOrNull() ?: return updated

            // We need to fetch seat info to get category keys for templates
            // This might be slow for large lists, but blocking is usually admin action
            val newConfigs = mutableListOf<SessionSeatConfig>()

            missingSeatIds.forEach { seatId ->
                val seatInfo = seatingApi.getSeatInfo(seatId)
                if (seatInfo != null) {
                    val (_, template) = resolvePriceAndTemplate(session, seatInfo.categoryKey)

                    newConfigs.add(
                        SessionSeatConfig(
                            session = session,
                            seatId = seatId,
                            priceTemplate = template,
                            status = ConfigStatus.BLOCKED
                        )
                    )
                }
            }

            if (newConfigs.isNotEmpty()) {
                sessionSeatConfigRepository.saveAll(newConfigs)
                return updated + newConfigs.size
            }
        }

        return updated
    }

    @Transactional
    override fun unblockSeats(sessionId: UUID, seatIds: List<Long>): Int {
        if (seatIds.isEmpty()) return 0
        val updated = sessionSeatConfigRepository.unblockSeats(sessionId, seatIds)
        if (updated > 0) {
            seatConfigSparseService.purgeDefaultRows(sessionId, seatIds)
        }
        return updated
    }

    @Transactional
    override fun blockTable(sessionId: UUID, tableId: Long): Int {
        return sessionTableConfigRepository.blockTable(sessionId, tableId)
    }

    @Transactional
    override fun unblockTableIfAllSeatsAvailable(sessionId: UUID, tableId: Long, seatIds: List<Long>): Int {
        return sessionTableConfigRepository.unblockTableIfAllSeatsAreAvailable(sessionId, tableId, seatIds)
    }

    @Transactional(readOnly = true)
    override fun getSeatPriceTemplateNames(sessionId: UUID, seatIds: List<Long>): Map<Long, String?> {
        if (seatIds.isEmpty()) return emptyMap()
        return sessionSeatConfigRepository.findBySessionIdAndSeatIdIn(sessionId, seatIds)
            .associate { it.seatId to it.priceTemplate?.templateName }
    }

    @Transactional(readOnly = true)
    override fun getGaPriceTemplateNames(sessionId: UUID, gaAreaIds: List<Long>): Map<Long, String?> {
        if (gaAreaIds.isEmpty()) return emptyMap()
        return sessionGAConfigRepository.findBySessionIdAndGaAreaIdIn(sessionId, gaAreaIds)
            .associate { it.gaAreaId to it.priceTemplate?.templateName }
    }

    @Transactional(readOnly = true)
    override fun getTablePriceTemplateNames(sessionId: UUID, tableIds: List<Long>): Map<Long, String?> {
        if (tableIds.isEmpty()) return emptyMap()
        return sessionTableConfigRepository.findBySessionIdAndTableIdIn(sessionId, tableIds)
            .associate { it.tableId to it.priceTemplate?.templateName }
    }

    @Transactional
    override fun sellSeat(sessionId: UUID, seatId: Long) {
        val updated = sessionSeatConfigRepository.sellSeats(sessionId, listOf(seatId))
        if (updated == 0) {
            // Check if it was already sold or not reserved
            val config = sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, seatId)
                ?: throw app.venues.common.exception.VenuesException.ResourceNotFound("Seat config not found")

            if (config.status != ConfigStatus.RESERVED) {
                throw app.venues.common.exception.VenuesException.ResourceConflict("Seat $seatId is not RESERVED (status: ${config.status})")
            }
        }
    }

    @Transactional
    override fun sellSeatsBatch(sessionId: UUID, seatIds: List<Long>) {
        if (seatIds.isNotEmpty()) {
            sessionSeatConfigRepository.sellSeats(sessionId, seatIds)
        }
    }

    @Transactional
    override fun sellGa(sessionId: UUID, gaAreaId: Long, quantity: Int) {
        // For GA, reservation already increments soldCount.
        // So we just need to verify it's valid, but no state change needed if soldCount tracks both.
        // However, if we want to distinguish, we might need a separate field.
        // Given current implementation: reserveGa increments soldCount.
        // So sellGa is a no-op regarding inventory count, but we might want to log or verify.
        // If we don't have a separate "reserved" count, we assume reservation == sale for capacity purposes.
    }

    @Transactional
    override fun sellGaBatch(sessionId: UUID, gaAreaQuantities: Map<Long, Int>) {
        // No-op for GA as per above.
    }

    @Transactional
    override fun sellTable(sessionId: UUID, tableId: Long) {
        val updated = sessionTableConfigRepository.sellTables(sessionId, listOf(tableId))
        if (updated == 0) {
            val config = sessionTableConfigRepository.findBySessionIdAndTableId(sessionId, tableId)
                ?: throw app.venues.common.exception.VenuesException.ResourceNotFound("Table config not found")

            if (config.status != ConfigStatus.RESERVED) {
                throw app.venues.common.exception.VenuesException.ResourceConflict("Table $tableId is not RESERVED (status: ${config.status})")
            }
        }
    }

    @Transactional
    override fun sellTablesBatch(sessionId: UUID, tableIds: List<Long>) {
        if (tableIds.isNotEmpty()) {
            sessionTableConfigRepository.sellTables(sessionId, tableIds)
        }
    }

    @Transactional
    override fun incrementTicketsSold(sessionId: UUID, quantity: Int): Boolean {
        if (quantity <= 0) {
            return true
        }
        val updated = eventSessionRepository.incrementTicketsSold(sessionId, quantity)
        if (updated == 0) {
            logger.warn { "Failed to increment ticketsSold for session $sessionId by $quantity due to capacity limit" }
        }
        return updated > 0
    }

    @Transactional
    override fun decrementTicketsSold(sessionId: UUID, quantity: Int): Boolean {
        if (quantity <= 0) {
            return true
        }
        val updated = eventSessionRepository.decrementTicketsSold(sessionId, quantity)
        if (updated == 0) {
            logger.warn { "Failed to decrement ticketsSold for session $sessionId by $quantity because there are not enough sold tickets" }
        }
        return updated > 0
    }

    @Transactional(readOnly = true)
    override fun getSessionTicketStats(sessionId: UUID): SessionTicketStatsDto? {
        val session = eventSessionRepository.findById(sessionId).getOrNull() ?: return null
        val event = session.event

        return SessionTicketStatsDto(
            sessionId = session.id,
            eventId = event.id,
            currency = event.currency,
            ticketsSold = session.ticketsSold,
            ticketsTotal = session.ticketsCount
        )
    }

    @Transactional(readOnly = true)
    override fun getEventTicketStats(eventId: UUID): List<SessionTicketStatsDto> {
        val sessions = eventSessionRepository.findByEventIdOrderByStartTimeAsc(eventId)

        return sessions.map { session ->
            val event = session.event
            SessionTicketStatsDto(
                sessionId = session.id,
                eventId = event.id,
                currency = event.currency,
                ticketsSold = session.ticketsSold,
                ticketsTotal = session.ticketsCount
            )
        }
    }

    @Transactional(readOnly = true)
    override fun getEventsByVenue(
        venueId: UUID,
        language: String?,
        limit: Int,
        offset: Int
    ): List<EventSummaryDto> {
        val events = eventRepository.findByVenueIdAndStatusOrderByFirstSessionStartAsc(
            venueId,
            app.venues.event.domain.EventStatus.PUBLISHED
        ).drop(offset).take(limit)

        return events.map { event ->
            val categoryName = event.category?.getName(language ?: "en")
            val nextSession = event.sessions.firstOrNull { it.startTime.isAfter(java.time.Instant.now()) }
                ?: event.sessions.firstOrNull()

            // Get translated title if language specified
            val title = if (!language.isNullOrBlank()) {
                event.translations.find { it.language.equals(language, ignoreCase = true) }?.title
                    ?: event.title
            } else {
                event.title
            }

            EventSummaryDto(
                id = event.id,
                title = title,
                imgUrl = event.imgUrl,
                venueId = event.venueId,
                venueName = "", // Not needed for same-venue listings
                location = event.location,
                categoryName = categoryName,
                priceRange = event.priceRange,
                currency = event.currency,
                status = event.status.name,
                startDateTime = nextSession?.startTime?.toString()
            )
        }
    }
}

