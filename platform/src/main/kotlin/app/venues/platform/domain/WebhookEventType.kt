package app.venues.platform.domain

/**
 * Webhook event types
 */
enum class WebhookEventType {
    SEAT_CLOSED,
    SEAT_OPENED,
    GA_AVAILABILITY_CHANGED,
    TABLE_CLOSED,
    TABLE_OPENED
}
