package app.venues.seating.domain

/**
 * Utility responsible for deterministic seat code generation.
 * Keeps formatting logic consistent between entity constructors and services.
 */
object SeatCodeFormatter {
    private val NON_ALPHANUMERIC = Regex("[^A-Z0-9]+")

    fun buildSeatCode(zone: ChartZone, row: String, seatNumber: String): String {
        val zonePrefix = buildZonePrefix(zone)
        return "${zonePrefix}_ROW-${sanitize(row)}_SEAT-${sanitize(seatNumber)}"
    }

    fun buildZonePrefix(zone: ChartZone): String {
        val parentPrefix = zone.parentZone?.let { buildZonePrefix(it) }
        return if (parentPrefix.isNullOrBlank()) zone.code else "${parentPrefix}_${zone.code}"
    }

    fun sanitize(value: String): String {
        val normalized = value.trim().uppercase().replace(NON_ALPHANUMERIC, "")
        return normalized.ifBlank { "UNASSIGNED" }
    }
}
