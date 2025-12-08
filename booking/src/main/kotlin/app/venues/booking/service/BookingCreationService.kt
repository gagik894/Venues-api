package app.venues.booking.service

import app.venues.booking.domain.Booking
import app.venues.booking.domain.BookingItem
import app.venues.booking.service.model.BookingCreationContext
import app.venues.booking.service.model.BookingCreationResult
import app.venues.booking.service.model.CartSnapshot
import app.venues.booking.service.model.PricingBreakdown
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Responsible for converting a validated cart snapshot into a Booking aggregate.
 * Keeps booking assembly logic cohesive and unit-testable.
 */
@Service
class BookingCreationService(
    private val eventApi: EventApi,
    @Value("\${app.booking.service-fee-percent:0}")
    private val serviceFeePercent: BigDecimal
) {

    private val logger = KotlinLogging.logger {}
    private val hundred = BigDecimal(100)

    fun assembleBooking(
        snapshot: CartSnapshot,
        context: BookingCreationContext
    ): BookingCreationResult {
        validateSnapshot(snapshot)

        val seatTemplates = loadSeatTemplateNames(snapshot)
        val gaTemplates = loadGaTemplateNames(snapshot)
        val tableTemplates = loadTableTemplateNames(snapshot)

        val itemDrafts = mutableListOf<ItemDraft>()
        var subtotal = BigDecimal.ZERO

        snapshot.seats.forEach { seat ->
            itemDrafts.add(
                ItemDraft(
                    seatId = seat.seatId,
                    quantity = 1,
                    unitPrice = seat.unitPrice,
                    priceTemplateName = seatTemplates[seat.seatId]
                )
            )
            subtotal = subtotal.add(seat.unitPrice)
        }

        snapshot.gaItems.forEach { gaItem ->
            val itemTotal = gaItem.unitPrice.multiply(BigDecimal(gaItem.quantity))
            itemDrafts.add(
                ItemDraft(
                    gaAreaId = gaItem.gaAreaId,
                    quantity = gaItem.quantity,
                    unitPrice = gaItem.unitPrice,
                    priceTemplateName = gaTemplates[gaItem.gaAreaId]
                )
            )
            subtotal = subtotal.add(itemTotal)
        }

        snapshot.tables.forEach { tableItem ->
            itemDrafts.add(
                ItemDraft(
                    tableId = tableItem.tableId,
                    quantity = 1,
                    unitPrice = tableItem.unitPrice,
                    priceTemplateName = tableTemplates[tableItem.tableId]
                )
            )
            subtotal = subtotal.add(tableItem.unitPrice)
        }

        if (itemDrafts.isEmpty()) {
            throw VenuesException.ResourceNotFound("Cart is empty")
        }

        val discount = snapshot.cart.discountAmount ?: BigDecimal.ZERO
        val discounted = (subtotal.subtract(discount)).max(BigDecimal.ZERO)
        val serviceFee = calculateServiceFee(discounted)
        val total = discounted.add(serviceFee)

        val booking = Booking(
            userId = context.userId,
            guest = context.guest,
            sessionId = snapshot.session.sessionId,
            totalPrice = total,
            currency = snapshot.session.currency,
            salesChannel = context.salesChannel,
            platformId = context.platformId,
            staffId = null,  // Not a direct sale
            venueId = snapshot.session.venueId
        ).apply {
            discountAmount = discount
            serviceFeeAmount = serviceFee
            promoCode = snapshot.cart.promoCode
            externalOrderNumber = context.paymentReference
        }

        itemDrafts.forEach { draft ->
            val item = BookingItem(
                booking = booking,
                quantity = draft.quantity,
                unitPrice = draft.unitPrice,
                seatId = draft.seatId,
                gaAreaId = draft.gaAreaId,
                tableId = draft.tableId,
                priceTemplateName = draft.priceTemplateName
            )
            booking.addItem(item)
        }

        logger.debug {
            "Assembled booking aggregate: session=${snapshot.session.sessionId}, total=$total, " +
                    "serviceFee=$serviceFee, items=${booking.items.size}"
        }

        return BookingCreationResult(
            booking = booking,
            pricing = PricingBreakdown(
                subtotal = subtotal,
                discount = discount,
                serviceFee = serviceFee,
                total = total
            )
        )
    }

    private fun validateSnapshot(snapshot: CartSnapshot) {
        if (snapshot.cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }
        val sessionId = snapshot.sessionId
        snapshot.seats.firstOrNull { it.sessionId != sessionId }?.let {
            throw VenuesException.ValidationFailure("Seat ${it.seatId} does not belong to session $sessionId")
        }
        snapshot.gaItems.firstOrNull { it.sessionId != sessionId }?.let {
            throw VenuesException.ValidationFailure("GA item ${it.gaAreaId} does not belong to session $sessionId")
        }
        snapshot.tables.firstOrNull { it.sessionId != sessionId }?.let {
            throw VenuesException.ValidationFailure("Table ${it.tableId} does not belong to session $sessionId")
        }
    }

    private fun loadSeatTemplateNames(snapshot: CartSnapshot): Map<Long, String?> {
        val seatIds = snapshot.seats.map { it.seatId }
        if (seatIds.isEmpty()) return emptyMap()
        return eventApi.getSeatPriceTemplateNames(snapshot.sessionId, seatIds)
    }

    private fun loadGaTemplateNames(snapshot: CartSnapshot): Map<Long, String?> {
        val gaIds = snapshot.gaItems.map { it.gaAreaId }
        if (gaIds.isEmpty()) return emptyMap()
        return eventApi.getGaPriceTemplateNames(snapshot.sessionId, gaIds)
    }

    private fun loadTableTemplateNames(snapshot: CartSnapshot): Map<Long, String?> {
        val tableIds = snapshot.tables.map { it.tableId }
        if (tableIds.isEmpty()) return emptyMap()
        return eventApi.getTablePriceTemplateNames(snapshot.sessionId, tableIds)
    }

    private fun calculateServiceFee(amount: BigDecimal): BigDecimal {
        if (serviceFeePercent.signum() <= 0 || amount.signum() <= 0) {
            return BigDecimal.ZERO
        }
        return amount
            .multiply(serviceFeePercent)
            .divide(hundred, 2, RoundingMode.HALF_UP)
    }

    private data class ItemDraft(
        val seatId: Long? = null,
        val gaAreaId: Long? = null,
        val tableId: Long? = null,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val priceTemplateName: String?
    )
}
