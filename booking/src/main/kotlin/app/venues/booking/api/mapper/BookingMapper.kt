package app.venues.booking.api.mapper

import app.venues.booking.api.dto.BookingItemResponse
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.domain.Booking
import app.venues.booking.domain.BookingItem
import org.springframework.stereotype.Component

/**
 * Mapper for converting between Booking entities and DTOs.
 */
@Component
class BookingMapper {

    /**
     * Convert Booking entity to BookingResponse DTO.
     */
    fun toResponse(booking: Booking): BookingResponse {
        val customerEmail = booking.user?.email ?: booking.guest?.email ?: ""
        val customerName = booking.user?.let { "${it.firstName} ${it.lastName}" }
            ?: booking.guest?.name ?: ""

        return BookingResponse(
            id = booking.id.toString(),
            sessionId = booking.session.id!!,
            eventTitle = booking.session.event.title,
            eventDescription = booking.session.event.description,
            venueName = booking.session.event.venue.name,
            sessionStartTime = booking.session.startTime.toString(),
            sessionEndTime = booking.session.endTime.toString(),
            customerEmail = customerEmail,
            customerName = customerName,
            items = booking.items.map { toItemResponse(it) },
            totalPrice = booking.totalPrice.toString(),
            currency = booking.currency,
            status = booking.status,
            confirmedAt = booking.confirmedAt?.toString(),
            cancelledAt = booking.cancelledAt?.toString(),
            cancellationReason = booking.cancellationReason,
            paymentId = booking.paymentId,
            createdAt = booking.createdAt.toString()
        )
    }

    /**
     * Convert BookingItem to response.
     */
    private fun toItemResponse(item: BookingItem): BookingItemResponse {
        return BookingItemResponse(
            id = item.id!!,
            seatId = item.seat?.id,
            seatIdentifier = item.seat?.seatIdentifier,
            levelId = item.level?.id,
            levelName = item.level?.levelName,
            quantity = item.quantity,
            unitPrice = item.unitPrice.toString(),
            totalPrice = item.getTotalPrice().toString(),
            priceTemplateName = item.priceTemplateName
        )
    }
}

