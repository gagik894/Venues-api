package app.venues.platform.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.*

/**
 * Represents a platform's subscription to webhooks for a specific event.
 *
 * Platforms only receive inventory updates (seat closed, GA changes) for events
 * they are subscribed to. This prevents data leakage and reduces unnecessary traffic.
 */
@Entity
@Table(
    name = "webhook_subscriptions",
    indexes = [
        Index(name = "idx_webhook_sub_platform", columnList = "platform_id"),
        Index(name = "idx_webhook_sub_event", columnList = "event_id"),
        Index(name = "idx_webhook_sub_unique", columnList = "platform_id, event_id", unique = true)
    ]
)
class WebhookSubscription(
    @Column(name = "platform_id", nullable = false)
    var platformId: UUID,

    @Column(name = "event_id", nullable = false)
    var eventId: UUID
) : AbstractUuidEntity()

