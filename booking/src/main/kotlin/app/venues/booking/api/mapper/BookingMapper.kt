package app.venues.booking.api.mapper

import app.venues.booking.api.dto.BookingItemResponse
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.domain.Booking
import app.venues.booking.support.toMoney
import app.venues.shared.money.MoneyAmount
import org.springframework.stereotype.Component

/**
 * Mapper for converting between Booking entities and DTOs.
 *
 * Requires external data for cross-module entities to maintain strict module boundaries.
 */
@Component
class BookingMapper {

    /**
     * Convert Booking entity to BookingResponse DTO.
     *
     * @param booking The booking entity
     * @param eventTitle Event title (from event module)
     * @param eventDescription Event description (from event module)
     * @param sessionStartTime Session start time (from event module)
     * @param sessionEndTime Session end time (from event module)
     * @param customerEmail Customer email (from user/guest)
     * @param customerName Customer name (from user/guest)
     * @param itemsData Booking items with external data
     */
    fun toResponse(
        booking: Booking,
        eventTitle: String,
        eventDescription: String?,
        sessionStartTime: String,
        sessionEndTime: String,
        customerEmail: String,
        customerName: String,
        itemsData: List<BookingItemData>
    ): BookingResponse {
        val currency = booking.currency
        return BookingResponse(
            id = booking.id.toString(),
            sessionId = booking.sessionId,
            eventTitle = eventTitle,
            eventDescription = eventDescription,
            sessionStartTime = sessionStartTime,
            sessionEndTime = sessionEndTime,
            customerEmail = customerEmail,
            customerName = customerName,
            items = itemsData.map { toItemResponse(it) },
            totalPrice = booking.totalPrice.toMoney(currency),
            serviceFeeAmount = booking.serviceFeeAmount.toMoney(currency),
            discountAmount = booking.discountAmount.toMoney(currency),
            promoCode = booking.promoCode,
            currency = booking.currency,
            status = booking.status,
            confirmedAt = booking.confirmedAt?.toString(),
            cancelledAt = booking.cancelledAt?.toString(),
            cancellationReason = booking.cancellationReason,
            paymentId = booking.paymentId?.toString(),
            createdAt = booking.createdAt.toString()
        )
    }

    /**
     * Convert BookingItem data to response.
     */
    private fun toItemResponse(itemData: BookingItemData): BookingItemResponse {
        return BookingItemResponse(
            id = itemData.id,
            seatId = itemData.seatId,
            seatIdentifier = itemData.seatIdentifier,
            levelId = itemData.levelId,
            levelName = itemData.levelName,
            tableId = itemData.tableId,
            quantity = itemData.quantity,
            unitPrice = itemData.unitPrice,
            totalPrice = itemData.totalPrice,
            priceTemplateName = itemData.priceTemplateName
        )
    }
}

/**
 * Data class to hold booking item data with cross-module information.
 */
data class BookingItemData(
    val id: Long,
    val seatId: Long?,
    val seatIdentifier: String?,
    val levelId: Long?,
    val levelName: String?,
    val tableId: Long?,
    val quantity: Int,
    val unitPrice: MoneyAmount,
    val totalPrice: MoneyAmount,
    val priceTemplateName: String?
)

