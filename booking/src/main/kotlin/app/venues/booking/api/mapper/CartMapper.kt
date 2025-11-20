package app.venues.booking.api.mapper

import app.venues.booking.api.dto.CartGAItemResponse
import app.venues.booking.api.dto.CartSeatResponse
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.api.dto.CartTableResponse
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
     * @param code Seat identifier (from seating module)
     * @param seatNumber Seat number (from seating module)
     * @param rowLabel Row label (from seating module)
     * @param levelName Level name (from seating module)
     * @param price Seat price
     * @param priceTemplateName Price template name
     */
    fun toCartSeatResponse(
        code: String,
        seatNumber: String?,
        rowLabel: String?,
        levelName: String,
        price: BigDecimal,
        priceTemplateName: String?
    ): CartSeatResponse {
        return CartSeatResponse(
            code = code,
            number = seatNumber,
            rowLabel = rowLabel,
            levelName = levelName,
            price = price.toString(),
            priceTemplateName = priceTemplateName
        )
    }

    /**
     * Convert CartItem to response DTO
     *
     * @param code Level identifier (from seating module)
     * @param levelName Level name (from seating module)
     * @param unitPrice Unit price per ticket
     * @param priceTemplateName Price template name
     */
    fun toCartGAItemResponse(
        quantity: Int,
        code: String?,
        levelName: String,
        unitPrice: BigDecimal,
        priceTemplateName: String?
    ): CartGAItemResponse {
        val totalPrice = unitPrice.multiply(BigDecimal(quantity))

        return CartGAItemResponse(
            code = code,
            name = levelName,
            quantity = quantity,
            unitPrice = unitPrice.toString(),
            totalPrice = totalPrice.toString(),
            priceTemplateName = priceTemplateName
        )
    }

    /**
     * Convert CartTable to response DTO
     *
     * @param code Table code (from seating module)
     * @param tableName Table name (from seating module)
     * @param price Total table price
     */
    fun toCartTableResponse(
        code: String,
        tableName: String,
        price: BigDecimal
    ): CartTableResponse {
        return CartTableResponse(
            code = code,
            number = tableName,
            price = price
        )
    }

    /**
     * Build complete cart summary
     */
    fun toCartSummary(
        token: UUID,
        seats: List<CartSeatResponse>,
        gaItems: List<CartGAItemResponse>,
        tables: List<CartTableResponse> = emptyList(),
        totalPrice: BigDecimal,
        currency: String,
        expiresAt: String,
        sessionId: UUID,
        eventTitle: String
    ): CartSummaryResponse {
        return CartSummaryResponse(
            token = token,
            seats = seats,
            gaItems = gaItems,
            tables = tables,
            totalPrice = totalPrice.toString(),
            currency = currency,
            expiresAt = expiresAt,
            sessionId = sessionId,
            eventTitle = eventTitle
        )
    }
}

