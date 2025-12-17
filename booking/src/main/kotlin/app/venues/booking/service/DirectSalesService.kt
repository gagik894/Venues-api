package app.venues.booking.service

import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.DirectSaleItemRequest
import app.venues.booking.api.dto.DirectSaleRequest
import app.venues.booking.domain.Booking
import app.venues.booking.domain.BookingItem
import app.venues.booking.event.BookingConfirmedEvent
import app.venues.booking.repository.BookingRepository
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.venue.api.VenueApi
import app.venues.venue.api.dto.PromoCodeDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * Service for direct sales (staff-initiated bookings).
 *
 * Creates confirmed bookings directly, bypassing the cart flow.
 * Handles inventory reservation and immediate finalization atomically.
 */
@Service
class DirectSalesService(
    private val bookingRepository: BookingRepository,
    private val guestService: GuestService,
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi,
    private val venueApi: VenueApi,
    private val bookingResponseService: BookingResponseService,
    private val bookingFulfillmentService: BookingFulfillmentService,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${app.booking.service-fee-percent:0}")
    private val serviceFeePercent: BigDecimal
) {
    private val logger = KotlinLogging.logger {}
    private val hundred = BigDecimal(100)

    /**
     * Creates a confirmed booking directly from a staff request.
     *
     * Flow:
     * 1. Validate session exists and belongs to venue
     * 2. Resolve item codes to IDs
     * 3. Reserve all items atomically
     * 4. Create booking with PENDING status
     * 5. Finalize inventory (RESERVED -> SOLD)
     * 6. Confirm booking immediately
     *
     * @param request Direct sale request with items
     * @param venueId Venue ID (validated by controller)
     * @param staffId Staff performing the sale
     * @return Confirmed booking response
     */
    @Transactional
    fun createDirectSale(
        request: DirectSaleRequest,
        venueId: UUID,
        staffId: UUID?,
        platformId: UUID? = null,
        salesChannel: app.venues.booking.domain.SalesChannel = app.venues.booking.domain.SalesChannel.DIRECT_SALE,
        confirmBooking: Boolean = true
    ): BookingResponse {
        logger.info { "Staff $staffId creating direct sale for session ${request.sessionId}" }

        // 1. Validate session exists and belongs to venue
        val session = eventApi.getEventSessionInfo(request.sessionId)
            ?: throw VenuesException.ResourceNotFound("Session not found: ${request.sessionId}")

        if (session.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Session does not belong to this venue")
        }

        // 2. Resolve codes and reserve items
        val reservedItems = reserveItems(request.sessionId, request.items)

        // 3. Calculate subtotal first (needed for promo validation)
        val subtotal = reservedItems.sumOf { item ->
            item.unitPrice.multiply(BigDecimal(item.quantity))
        }

        // 4. Validate and calculate promo discount
        val (discount, promoCode) = calculatePromoDiscount(venueId, request.promoCode, subtotal)

        // 5. Calculate final pricing
        val pricing = calculatePricing(subtotal, discount)

        // 6. Create guest
        val guest = guestService.findOrCreateGuest(
            request.customerEmail,
            request.customerName,
            request.customerPhone
        )

        // 7. Build booking entity
        val booking = Booking(
            userId = null,
            guest = guest,
            sessionId = request.sessionId,
            totalPrice = pricing.total,
            currency = session.currency,
            salesChannel = salesChannel,
            platformId = platformId,
            staffId = staffId,
            venueId = venueId
        ).apply {
            discountAmount = discount
            serviceFeeAmount = pricing.serviceFee
            this.promoCode = promoCode?.code
            externalOrderNumber = request.paymentReference
        }

        // 8. Add items to booking
        reservedItems.forEach { item ->
            booking.addItem(
                BookingItem(
                    booking = booking,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    seatId = item.seatId,
                    gaAreaId = item.gaAreaId,
                    tableId = item.tableId,
                    priceTemplateName = item.priceTemplateName
                )
            )
        }

        // 9. Save booking
        val savedBooking = bookingRepository.save(booking)

        // 10. Redeem promo if applicable
        var finalBooking = savedBooking

        if (confirmBooking) {
            // Redeem promo and confirm booking immediately (payment assumed received)
            bookingFulfillmentService.redeemPromoIfNeeded(savedBooking)

            savedBooking.confirm(null)
            finalBooking = bookingRepository.save(savedBooking)

            // Finalize inventory (RESERVED -> SOLD) & Record tickets sold
            bookingFulfillmentService.finalizeBookingInventory(finalBooking)

            // Generate tickets
            bookingFulfillmentService.generateTickets(finalBooking)

            // Publish event for async email sending
            eventPublisher.publishEvent(
                BookingConfirmedEvent(
                    bookingId = finalBooking.id,
                    venueId = venueId,
                    customerEmail = request.customerEmail,
                    customerName = request.customerName,
                    locale = guest.preferredLanguage ?: "en"
                )
            )
        }

        logger.info {
            "Direct sale completed: bookingId=${finalBooking.id}, " +
                    "total=${pricing.total}, items=${reservedItems.size}, staff=$staffId, platform=$platformId, confirmed=$confirmBooking"
        }

        return bookingResponseService.prepareBookingResponse(finalBooking)
    }



    private fun calculatePromoDiscount(
        venueId: UUID,
        promoCodeString: String?,
        subtotal: BigDecimal
    ): Pair<BigDecimal, PromoCodeDto?> {
        if (promoCodeString.isNullOrBlank()) {
            return BigDecimal.ZERO to null
        }

        val promo = venueApi.validatePromoCode(venueId, promoCodeString)
            ?: throw VenuesException.ValidationFailure("Invalid or expired promo code: $promoCodeString")

        // Check minimum order amount
        if (promo.minOrderAmount != null && subtotal < promo.minOrderAmount) {
            throw VenuesException.ValidationFailure(
                "Order subtotal ${subtotal} is below minimum ${promo.minOrderAmount} for this promo code"
            )
        }

        // Calculate discount
        val rawDiscount = when (promo.discountType) {
            "PERCENTAGE" -> subtotal.multiply(promo.discountValue).divide(hundred, 2, RoundingMode.HALF_UP)
            "FIXED_AMOUNT" -> promo.discountValue
            else -> BigDecimal.ZERO
        }

        // Apply max discount cap if present
        val maxDiscount = promo.maxDiscountAmount
        val discount: BigDecimal = if (maxDiscount != null && rawDiscount > maxDiscount) {
            maxDiscount
        } else {
            rawDiscount
        }

        return discount to promo
    }

    private fun reserveItems(
        sessionId: UUID,
        items: List<DirectSaleItemRequest>
    ): List<ReservedItem> {
        val reserved = mutableListOf<ReservedItem>()

        try {
            for (item in items) {
                val seatCode = item.seatCode
                val gaAreaCode = item.gaAreaCode
                val tableCode = item.tableCode

                val count = listOfNotNull(seatCode, gaAreaCode, tableCode).size
                if (count != 1) {
                    throw VenuesException.ValidationFailure(
                        "Exactly one of seatCode, gaAreaCode, or tableCode must be provided"
                    )
                }

                when {
                    seatCode != null -> {
                        val seatInfo = seatingApi.getSeatInfoByCode(seatCode)
                            ?: throw VenuesException.ResourceNotFound("Seat not found: $seatCode")

                        val price = eventApi.reserveSeat(sessionId, seatInfo.id)
                            ?: throw VenuesException.ResourceConflict("Seat is not available: $seatCode")

                        val templateName =
                            eventApi.getSeatPriceTemplateNames(sessionId, listOf(seatInfo.id))[seatInfo.id]

                        reserved.add(
                            ReservedItem(
                                seatId = seatInfo.id,
                                quantity = 1,
                                unitPrice = price,
                                priceTemplateName = templateName
                            )
                        )
                    }

                    gaAreaCode != null -> {
                        val gaInfo = seatingApi.getGaInfoByCode(gaAreaCode)
                            ?: throw VenuesException.ResourceNotFound("GA area not found: $gaAreaCode")

                        val price = eventApi.reserveGa(sessionId, gaInfo.id, item.quantity)
                            ?: throw VenuesException.ResourceConflict(
                                "Insufficient GA capacity: $gaAreaCode"
                            )

                        val templateName = eventApi.getGaPriceTemplateNames(sessionId, listOf(gaInfo.id))[gaInfo.id]

                        reserved.add(
                            ReservedItem(
                                gaAreaId = gaInfo.id,
                                quantity = item.quantity,
                                unitPrice = price,
                                priceTemplateName = templateName
                            )
                        )
                    }

                    tableCode != null -> {
                        val tableInfo = seatingApi.getTableInfoByCode(tableCode)
                            ?: throw VenuesException.ResourceNotFound("Table not found: $tableCode")

                        val price = eventApi.reserveTable(sessionId, tableInfo.id)
                            ?: throw VenuesException.ResourceConflict("Table is not available: $tableCode")

                        val templateName =
                            eventApi.getTablePriceTemplateNames(sessionId, listOf(tableInfo.id))[tableInfo.id]

                        reserved.add(
                            ReservedItem(
                                tableId = tableInfo.id,
                                quantity = 1,
                                unitPrice = price,
                                priceTemplateName = templateName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Rollback reserved items on failure
            releaseReservedItems(sessionId, reserved)
            throw e
        }

        return reserved
    }

    private fun releaseReservedItems(sessionId: UUID, items: List<ReservedItem>) {
        items.forEach { item ->
            try {
                when {
                    item.seatId != null -> eventApi.releaseSeat(sessionId, item.seatId)
                    item.gaAreaId != null -> eventApi.releaseGa(sessionId, item.gaAreaId, item.quantity)
                    item.tableId != null -> eventApi.releaseTable(sessionId, item.tableId)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to release item during rollback: $item" }
            }
        }
    }

    private fun calculatePricing(subtotal: BigDecimal, discount: BigDecimal): DirectSalePricing {
        val discounted = (subtotal.subtract(discount)).max(BigDecimal.ZERO)
        val serviceFee = calculateServiceFee(discounted)
        val total = discounted.add(serviceFee)

        return DirectSalePricing(
            subtotal = subtotal,
            discount = discount,
            serviceFee = serviceFee,
            total = total
        )
    }

    private fun calculateServiceFee(amount: BigDecimal): BigDecimal {
        if (serviceFeePercent.signum() <= 0 || amount.signum() <= 0) {
            return BigDecimal.ZERO
        }
        return amount
            .multiply(serviceFeePercent)
            .divide(hundred, 2, RoundingMode.HALF_UP)
    }



    private data class ReservedItem(
        val seatId: Long? = null,
        val gaAreaId: Long? = null,
        val tableId: Long? = null,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val priceTemplateName: String?
    )

    private data class DirectSalePricing(
        val subtotal: BigDecimal,
        val discount: BigDecimal,
        val serviceFee: BigDecimal,
        val total: BigDecimal
    )
}
