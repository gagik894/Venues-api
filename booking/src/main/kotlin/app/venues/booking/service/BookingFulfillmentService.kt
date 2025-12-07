package app.venues.booking.service

import app.venues.booking.domain.Booking
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.ticket.api.TicketApi
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class BookingFulfillmentService(
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi,
    private val venueApi: VenueApi,
    private val ticketApi: TicketApi
) {
    private val logger = KotlinLogging.logger {}

    fun redeemPromoIfNeeded(booking: Booking) {
        val code = booking.promoCode
        val venueId = booking.venueId
        if (code != null && venueId != null) {
            venueApi.redeemPromoCode(venueId, code)
            logger.debug { "Redeemed promo code $code for booking ${booking.id}" }
        }
    }

    fun releasePromoIfNeeded(booking: Booking) {
        val code = booking.promoCode
        val venueId = booking.venueId
        if (code != null && venueId != null) {
            try {
                venueApi.releasePromoCode(venueId, code)
                logger.debug { "Released promo code $code for booking ${booking.id}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to release promo code $code for booking ${booking.id}" }
            }
        }
    }

    /**
     * Finalize inventory (RESERVED -> SOLD).
     */
    fun finalizeBookingInventory(booking: Booking) {
        // 1. Finalize Seats
        val seatIds = booking.items.mapNotNull { it.seatId }
        if (seatIds.isNotEmpty()) {
            eventApi.sellSeatsBatch(booking.sessionId, seatIds)
        }

        // 2. Finalize GA
        val gaItems = booking.items.filter { it.gaAreaId != null }
        if (gaItems.isNotEmpty()) {
            val gaQuantities = gaItems.mapNotNull { item ->
                item.gaAreaId?.let { it to item.quantity }
            }.toMap()
            eventApi.sellGaBatch(booking.sessionId, gaQuantities)
        }

        // 3. Finalize Tables
        val tableIds = booking.items.mapNotNull { it.tableId }
        if (tableIds.isNotEmpty()) {
            eventApi.sellTablesBatch(booking.sessionId, tableIds)
        }

        recordTicketsSold(booking)
    }

    /**
     * Release inventory (RESERVED/SOLD -> AVAILABLE).
     */
    fun releaseBookingInventory(booking: Booking) {
        // 1. Release Seats
        val seatIds = booking.items.mapNotNull { it.seatId }
        if (seatIds.isNotEmpty()) {
            eventApi.releaseSeatsBatch(booking.sessionId, seatIds)
        }

        // 2. Release GA
        val gaItems = booking.items.filter { it.gaAreaId != null }
        if (gaItems.isNotEmpty()) {
            val gaQuantities = gaItems.mapNotNull { item ->
                item.gaAreaId?.let { it to item.quantity }
            }.toMap()
            eventApi.releaseGaBatch(booking.sessionId, gaQuantities)
        }

        // 3. Release Tables
        val tableIds = booking.items.mapNotNull { it.tableId }
        if (tableIds.isNotEmpty()) {
            eventApi.releaseTablesBatch(booking.sessionId, tableIds)
        }
    }

    fun recordTicketsSold(booking: Booking) {
        val quantity = calculateTicketQuantity(booking)
        if (quantity == 0) {
            return
        }
        val updated = eventApi.incrementTicketsSold(booking.sessionId, quantity)
        if (!updated) {
            logger.warn { "Failed to increment tickets sold for booking ${booking.id} in session ${booking.sessionId}" }
        }
    }

    fun rollbackTicketsSold(booking: Booking) {
        val quantity = calculateTicketQuantity(booking)
        if (quantity == 0) {
            return
        }
        val updated = eventApi.decrementTicketsSold(booking.sessionId, quantity)
        if (!updated) {
            logger.warn { "Failed to decrement tickets sold for booking ${booking.id} in session ${booking.sessionId}" }
        }
    }

    fun calculateTicketQuantity(booking: Booking): Int {
        if (booking.items.isEmpty()) {
            return 0
        }

        val seatTickets = booking.items.count { it.seatId != null }
        val gaTickets = booking.items.filter { it.gaAreaId != null }.sumOf { it.quantity }
        val tableTickets = calculateTableTicketQuantity(booking)
        return seatTickets + gaTickets + tableTickets
    }

    fun calculateTableTicketQuantity(booking: Booking): Int {
        val tableIds = booking.items.mapNotNull { it.tableId }.toSet()
        if (tableIds.isEmpty()) {
            return 0
        }

        return tableIds.sumOf { tableId ->
            try {
                val seats = seatingApi.getSeatsForTable(tableId)
                if (seats.isNotEmpty()) {
                    seats.size
                } else {
                    seatingApi.getTableInfo(tableId)?.seatCapacity ?: 0
                }
            } catch (ex: Exception) {
                logger.error(ex) { "Failed to resolve seat capacity for table $tableId" }
                0
            }
        }
    }

    fun generateTickets(booking: Booking) {
        booking.items.forEach { item ->
            val ticketType = when {
                item.seatId != null -> "SEAT"
                item.gaAreaId != null -> "GA"
                item.tableId != null -> "TABLE"
                else -> throw IllegalStateException("Booking item has no inventory reference")
            }

            ticketApi.generateTicketsForBookingItem(
                bookingId = booking.id,
                bookingItemId = requireNotNull(item.id) { "Booking item ID must not be null" },
                eventSessionId = booking.sessionId,
                ticketType = ticketType,
                seatId = item.seatId,
                gaAreaId = item.gaAreaId,
                tableId = item.tableId,
                quantity = item.quantity,
                qrCodes = null // Venue generates QR
            )
        }
    }
}
