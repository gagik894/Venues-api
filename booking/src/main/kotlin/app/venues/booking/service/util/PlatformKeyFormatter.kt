package app.venues.booking.service.util

import java.util.*

/**
 * Normalizes platform labels for analytics responses.
 *
 * PLATFORM channel is keyed by platformId when present, otherwise we fall back to the channel name.
 */
object PlatformKeyFormatter {
    fun format(channel: String, platformId: UUID?): String {
        return if (channel == "PLATFORM" && platformId != null) {
            platformId.toString()
        } else {
            channel
        }
    }
}
