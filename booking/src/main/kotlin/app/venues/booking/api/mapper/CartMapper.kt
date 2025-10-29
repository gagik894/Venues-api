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
 *
 * Requires external seat/level data to maintain strict module boundaries.
 */
@Component
class CartMapper {

    /**
     * Convert CartSeat to response DTO
     *
     * @param cartSeat The cart seat entity
     * @param seatIdentifier Seat identifier (from seating module)
     * @param seatNumber Seat number (from seating module)
     * @param rowLabel Row label (from seating module)
     * @param levelName Level name (from seating module)
     * @param levelIdentifier Level identifier (from seating module)
     * @param price Seat price
     * @param priceTemplateName Price template name
     */
    fun toCartSeatResponse(
        cartSeat: CartSeat,
        seatIdentifier: String,
        seatNumber: String?,
        rowLabel: String?,
        levelName: String,
        levelIdentifier: String?,
        price: BigDecimal,
        priceTemplateName: String?
    ): CartSeatResponse {
        return CartSeatResponse(
            seatIdentifier = seatIdentifier,
            seatNumber = seatNumber,
            rowLabel = rowLabel,
            levelName = levelName,
            levelIdentifier = levelIdentifier,
            price = price.toString(),
            priceTemplateName = priceTemplateName
        )
    }

    /**
     * Convert CartItem to response DTO
     *
     * @param cartItem The cart item entity
     * @param levelIdentifier Level identifier (from seating module)
     * @param levelName Level name (from seating module)
     * @param unitPrice Unit price per ticket
     * @param priceTemplateName Price template name
     */
    fun toCartGAItemResponse(
        cartItem: CartItem,
        levelIdentifier: String?,
        levelName: String,
        unitPrice: BigDecimal,
        priceTemplateName: String?
    ): CartGAItemResponse {
        val totalPrice = unitPrice.multiply(BigDecimal(cartItem.quantity))

        return CartGAItemResponse(
            levelIdentifier = levelIdentifier,
            levelName = levelName,
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

