package app.venues.platform.api.dto

import java.util.*

data class PlatformSubscriptionResponse(
    val platformId: UUID,
    val eventId: UUID,
    val active: Boolean
)

data class CreateSubscriptionRequest(
    val eventId: UUID
)

data class BulkSubscribeRequest(
    val eventIds: List<UUID>
)

