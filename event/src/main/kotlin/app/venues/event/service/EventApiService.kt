package app.venues.event.service

import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSessionDto
import app.venues.event.api.dto.GaAvailabilityDto
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
class EventApiService(
    private val eventSessionRepository: EventSessionRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionGAConfigRepository: SessionGAConfigRepository,
    private val sessionTableConfigRepository: SessionTableConfigRepository
) : EventApi {

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

    @Transactional
    override fun reserveSeat(sessionId: UUID, seatId: Long): BigDecimal? {
        return sessionSeatConfigRepository.reserveSeatAndGetPrice(sessionId, seatId)
    }

    @Transactional
    override fun releaseSeat(sessionId: UUID, seatId: Long) {
        sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, seatId)?.let { config ->
            config.release()
            sessionSeatConfigRepository.save(config)
        }
    }

    @Transactional
    override fun releaseSeatsBatch(sessionId: UUID, seatIds: List<Long>) {
        if (seatIds.isNotEmpty()) {
            sessionSeatConfigRepository.releaseSeats(sessionId, seatIds)
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
            config.sell(maxOf(0, config.soldCount - quantity))
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
        return sessionSeatConfigRepository.blockSeats(sessionId, seatIds)
    }

    @Transactional
    override fun unblockSeats(sessionId: UUID, seatIds: List<Long>): Int {
        if (seatIds.isEmpty()) return 0
        return sessionSeatConfigRepository.unblockSeats(sessionId, seatIds)
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
}
