package app.venues.event.api.dto

import java.util.*

/**
 * Lightweight event summary DTO for cross-module use.
 *
 * Used by EventApi port for venue-scoped event listings.
 * Intentionally minimal to avoid domain dependencies.
 */
data class EventSummaryDto(
    val id: UUID,
    val title: String,
    val imgUrl: String?,
    val venueId: UUID,
    val venueName: String,
    val location: String?,
    val categoryName: String?,
    val priceRange: String?,
    val currency: String,
    val status: String,  // String instead of EventStatus enum to avoid domain dependency
    val startDateTime: String?
)
