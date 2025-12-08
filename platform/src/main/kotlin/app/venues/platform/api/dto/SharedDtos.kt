package app.venues.platform.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * GA reservation details
 */
data class PlatformGAReservation(
    @field:NotBlank(message = "Level identifier is required")
    var levelIdentifier: String,

    @field:Min(value = 1, message = "Quantity must be at least 1")
    var quantity: Int
)

/**
 * Reserved seat info
 */
data class ReservedSeatInfo(
    val seatIdentifier: String,
    val levelName: String,
    val seatNumber: String?,
    val rowLabel: String?
)

/**
 * Reserved GA info
 */
data class ReservedGAInfo(
    val levelIdentifier: String,
    val levelName: String,
    val quantity: Int
)

/**
 * Reserved table info
 */
data class ReservedTableInfo(
    val tableIdentifier: String,
    val tableName: String
)
