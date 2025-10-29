package app.venues.booking.api.mapper

import app.venues.booking.api.dto.CartGAItemResponse
import app.venues.booking.api.dto.CartSeatResponse
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.domain.CartItem
import app.venues.booking.domain.CartSeat
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

/**
 * Mapper for converting between Cart entities and DTOs.
 */
@Component
class CartMapper {

    /**
     * Convert CartSeat to response DTO
     */
    fun toCartSeatResponse(cartSeat: CartSeat, price: BigDecimal, priceTemplateName: String?): CartSeatResponse {
        return CartSeatResponse(
            seatIdentifier = cartSeat.seat.seatIdentifier,
            seatNumber = cartSeat.seat.seatNumber,
            rowLabel = cartSeat.seat.rowLabel,
            levelName = cartSeat.seat.level.levelName,
            levelIdentifier = cartSeat.seat.level.levelIdentifier,
            price = price.toString(),
            priceTemplateName = priceTemplateName
        )
    }

    /**
     * Convert CartItem to response DTO
     */
    fun toCartGAItemResponse(
        cartItem: CartItem,
        unitPrice: BigDecimal,
        priceTemplateName: String?
    ): CartGAItemResponse {
        val totalPrice = unitPrice.multiply(BigDecimal(cartItem.quantity))

        return CartGAItemResponse(
            levelIdentifier = cartItem.level.levelIdentifier,
            levelName = cartItem.level.levelName,
            quantity = cartItem.quantity,
            unitPrice = unitPrice.toString(),
            totalPrice = totalPrice.toString(),
            priceTemplateName = priceTemplateName
        )
    }

    /**
     * Build complete cart summary
     */
    fun toCartSummary(
        token: UUID,
        seats: List<CartSeatResponse>,
        gaItems: List<CartGAItemResponse>,
        totalPrice: BigDecimal,
        currency: String,
        expiresAt: String,
        sessionId: Long,
        eventTitle: String
    ): CartSummaryResponse {
        return CartSummaryResponse(
            token = token,
            seats = seats,
            gaItems = gaItems,
            totalPrice = totalPrice.toString(),
            currency = currency,
            expiresAt = expiresAt,
            sessionId = sessionId,
            eventTitle = eventTitle
        )
    }
}

