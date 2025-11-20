package app.venues.event.api

import app.venues.event.api.dto.EventSessionDto
import app.venues.event.api.dto.GaAvailabilityDto
import java.math.BigDecimal
import java.util.*

interface EventApi {
    fun getEventSessionInfo(sessionId: UUID): EventSessionDto?

    // Seat Reservation
    fun reserveSeat(sessionId: UUID, seatId: Long): BigDecimal?
    fun releaseSeat(sessionId: UUID, seatId: Long)
    fun releaseSeatsBatch(sessionId: UUID, seatIds: List<Long>)

    // GA Reservation
    fun reserveGa(sessionId: UUID, gaAreaId: Long, quantity: Int): BigDecimal?
    fun adjustGa(sessionId: UUID, gaAreaId: Long, quantityDelta: Int): Boolean
    fun releaseGa(sessionId: UUID, gaAreaId: Long, quantity: Int)
    fun releaseGaBatch(sessionId: UUID, gaAreaQuantities: Map<Long, Int>)
    fun getGaAvailability(sessionId: UUID, gaAreaId: Long): GaAvailabilityDto?

    // Table Reservation
    fun reserveTable(sessionId: UUID, tableId: Long): BigDecimal?
    fun releaseTable(sessionId: UUID, tableId: Long)
    fun releaseTablesBatch(sessionId: UUID, tableIds: List<Long>)
    fun getTableBookingMode(sessionId: UUID, tableId: Long): String? // Returns TableBookingMode name

    // Cross-resource blocking (Seat <-> Table)
    fun blockSeats(sessionId: UUID, seatIds: List<Long>): Int
    fun unblockSeats(sessionId: UUID, seatIds: List<Long>): Int
    fun blockTable(sessionId: UUID, tableId: Long): Int
    fun unblockTableIfAllSeatsAvailable(sessionId: UUID, tableId: Long, seatIds: List<Long>): Int

    // Price Template Info
    fun getSeatPriceTemplateNames(sessionId: UUID, seatIds: List<Long>): Map<Long, String?>
    fun getGaPriceTemplateNames(sessionId: UUID, gaAreaIds: List<Long>): Map<Long, String?>
}
