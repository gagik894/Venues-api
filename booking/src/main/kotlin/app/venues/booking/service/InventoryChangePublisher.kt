package app.venues.booking.service

import app.venues.booking.event.*
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.*

/**
 * Centralized publisher for inventory state changes.
 * All inventory mutations should go through this to ensure webhooks are emitted.
 */
@Service
class InventoryChangePublisher(
    private val eventPublisher: ApplicationEventPublisher,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    fun seatsClosed(sessionId: UUID, seatIds: List<Long>) {
        if (seatIds.isEmpty()) return
        val seats = seatingApi.getSeatInfoBatch(seatIds)
        seats.forEach { seat ->
            eventPublisher.publishEvent(SeatClosedEvent(sessionId, seat.code))
        }
        logger.debug { "Published SeatClosedEvent for ${seats.size} seats, session=$sessionId" }
    }

    fun seatsOpened(sessionId: UUID, seatIds: List<Long>) {
        if (seatIds.isEmpty()) return
        val seats = seatingApi.getSeatInfoBatch(seatIds)
        seats.forEach { seat ->
            eventPublisher.publishEvent(SeatOpenedEvent(sessionId, seat.code))
        }
        logger.debug { "Published SeatOpenedEvent for ${seats.size} seats, session=$sessionId" }
    }

    fun tablesClosed(sessionId: UUID, tableIds: List<Long>) {
        if (tableIds.isEmpty()) return
        val tables = tableIds.mapNotNull { seatingApi.getTableInfo(it) }
        tables.forEach { table ->
            eventPublisher.publishEvent(TableClosedEvent(sessionId, table.code))
        }
        logger.debug { "Published TableClosedEvent for ${tables.size} tables, session=$sessionId" }
    }

    fun tablesOpened(sessionId: UUID, tableIds: List<Long>) {
        if (tableIds.isEmpty()) return
        val tables = tableIds.mapNotNull { seatingApi.getTableInfo(it) }
        tables.forEach { table ->
            eventPublisher.publishEvent(TableOpenedEvent(sessionId, table.code))
        }
        logger.debug { "Published TableOpenedEvent for ${tables.size} tables, session=$sessionId" }
    }

    fun gaAvailabilityChanged(
        sessionId: UUID,
        gaAreaId: Long,
        levelIdentifier: String,
        availableTickets: Int,
        totalCapacity: Int
    ) {
        eventPublisher.publishEvent(
            GAAvailabilityChangedEvent(
                sessionId = sessionId,
                levelIdentifier = levelIdentifier,
                levelName = "", // not used downstream
                availableTickets = availableTickets,
                totalCapacity = totalCapacity
            )
        )
    }
}

