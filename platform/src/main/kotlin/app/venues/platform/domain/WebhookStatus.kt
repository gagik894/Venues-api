package app.venues.platform.domain

/**
 * Webhook delivery status
 */
enum class WebhookStatus {
    /**
     * Webhook is pending delivery or retry
     */
    PENDING,

    /**
     * Webhook was successfully delivered
     */
    DELIVERED,

    /**
     * Webhook delivery failed after max retries
     */
    FAILED
}
