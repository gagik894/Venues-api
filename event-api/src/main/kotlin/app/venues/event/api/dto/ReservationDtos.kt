package app.venues.event.api.dto

import java.math.BigDecimal

/**
 * Result of reserving a seat by business code.
 */
data class SeatCodeReservationDto(
    val seatId: Long,
    val seatCode: String,
    val unitPrice: BigDecimal
)

/**
 * Result of reserving GA tickets by business code.
 */
data class GaCodeReservationDto(
    val gaAreaId: Long,
    val gaCode: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

/**
 * Result of reserving a table by business code.
 */
data class TableCodeReservationDto(
    val tableId: Long,
    val tableCode: String,
    val unitPrice: BigDecimal
)

