package app.venues.platform.api.dto

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
