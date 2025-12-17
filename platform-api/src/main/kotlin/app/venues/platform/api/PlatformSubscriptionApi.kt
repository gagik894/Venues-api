package app.venues.platform.api

import java.util.*

/**
 * Port interface for managing webhook/platform subscriptions to events.
 *
 * This allows other modules (e.g., event) to declare which platforms should
 * receive webhooks for a given event without depending on platform internals.
 */
interface PlatformSubscriptionApi {
    /**
     * Replace subscriptions for an event with the provided list.
     * Passing an empty list unsubscribes all platforms for that event.
     */
    fun updateEventSubscriptions(eventId: UUID, platformIds: List<UUID>)

    /**
     * Get all platform IDs subscribed to an event.
     */
    fun getEventSubscriptions(eventId: UUID): List<UUID>

    /**
     * Get all active platforms that can be subscribed (for UI selection).
     */
    fun getAvailablePlatforms(): List<AvailablePlatformDto>
}

data class AvailablePlatformDto(
    val id: UUID,
    val name: String,
    val status: String
)

