package app.venues.booking.service

import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.mapper.BookingItemData
import app.venues.booking.api.mapper.BookingMapper
import app.venues.booking.domain.Booking
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.shared.money.toMoney
import app.venues.user.api.UserApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for preparing booking responses with enriched data.
 *
 * Extracted from BookingService to break circular dependency with DirectSalesService.
 * Uses API interfaces (UserApi, EventApi, SeatingApi) for cross-module communication,
 * enforcing Hexagonal Architecture boundaries.
 */
@Service
@Transactional(readOnly = true)
class BookingResponseService(
    private val bookingMapper: BookingMapper,
    private val userApi: UserApi,
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi
) {
    /**
     * Prepare booking response with enriched data from other modules.
     */
    fun prepareBookingResponse(booking: Booking): BookingResponse {
        val currency = booking.currency
        // Fetch session from event module
        val sessionDto = eventApi.getEventSessionInfo(booking.sessionId)
            ?: throw VenuesException.ResourceNotFound("Event session not found")

        // Get customer info using UserApi (Hexagonal Architecture)
        val customerEmail = booking.userId?.let {
            userApi.getUserEmail(it) ?: ""
        } ?: booking.guest?.email ?: ""

        val customerName = booking.userId?.let {
            userApi.getUserFullName(it) ?: ""
        } ?: booking.guest?.name ?: ""

        // Prepare items data using SeatingApi (Hexagonal Architecture)
        val itemsData = booking.items.map { item ->
            val seatId = item.seatId
            val levelId = item.gaAreaId

            val seatIdentifier = seatId?.let {
                seatingApi.getSeatInfo(it)?.code
            }
            val levelName = when {
                seatId != null -> seatingApi.getSeatInfo(seatId)?.zoneName
                levelId != null -> seatingApi.getGaInfo(levelId)?.name
                else -> null
            }

            BookingItemData(
                id = item.id ?: error("Booking item ID cannot be null"),
                seatId = seatId,
                seatIdentifier = seatIdentifier,
                levelId = levelId,
                levelName = levelName,
                tableId = item.tableId,
                quantity = item.quantity,
                unitPrice = item.unitPrice.toMoney(currency),
                totalPrice = item.getTotalPrice().toMoney(currency),
                priceTemplateName = item.priceTemplateName
            )
        }

        return bookingMapper.toResponse(
            booking = booking,
            eventTitle = sessionDto.eventTitle,
            eventDescription = sessionDto.eventDescription,
            sessionStartTime = sessionDto.startTime.toString(),
            sessionEndTime = sessionDto.endTime.toString(),
            customerEmail = customerEmail,
            customerName = customerName,
            itemsData = itemsData
        )
    }
}
