package app.venues.platform.api.dto

import app.venues.platform.domain.WebhookEventType
import app.venues.platform.domain.WebhookStatus
import java.util.*

// ===========================================
// WEBHOOK RESPONSE DTOs
// ===========================================

/**
 * Webhook event response
 */
data class WebhookEventResponse(
    val id: UUID,
    val platformId: UUID,
    val platformName: String,
    val eventType: WebhookEventType,
    val sessionId: UUID,
    val seatIdentifier: String?,
    val levelIdentifier: String?,
    val tableIdentifier: String?,
    val status: WebhookStatus,
    val responseCode: Int?,
    val errorMessage: String?,
    val attemptCount: Int,
    val nextRetryAt: String?,
    val createdAt: String
)

// ===========================================
// WEBHOOK PAYLOAD DTOs (Sent to platforms)
// ===========================================

/**
 * Base webhook payload
 */
interface WebhookPayload {
    val eventType: WebhookEventType
    val timestamp: String
    val sessionId: UUID
}

/**
 * Seat reserved webhook payload
 */
data class SeatReservedPayload(
    override val eventType: WebhookEventType = WebhookEventType.SEAT_RESERVED,
    override val timestamp: String,
    override val sessionId: UUID,
    val seatIdentifier: String,
) : WebhookPayload

/**
 * Seat released webhook payload
 */
data class SeatReleasedPayload(
    override val eventType: WebhookEventType = WebhookEventType.SEAT_RELEASED,
    override val timestamp: String,
    override val sessionId: UUID,
    val seatIdentifier: String,
) : WebhookPayload

/**
 * GA availability changed webhook payload
 */
data class GAAvailabilityChangedPayload(
    override val eventType: WebhookEventType = WebhookEventType.GA_AVAILABILITY_CHANGED,
    override val timestamp: String,
    override val sessionId: UUID,
    val levelIdentifier: String,
    val levelName: String,
    val availableTickets: Int,
    val totalCapacity: Int
) : WebhookPayload

/**
 * Table reserved webhook payload
 */
data class TableReservedPayload(
    override val eventType: WebhookEventType = WebhookEventType.TABLE_RESERVED,
    override val timestamp: String,
    override val sessionId: UUID,
    val tableIdentifier: String,
    val tableName: String
) : WebhookPayload

/**
 * Table released webhook payload
 */
data class TableReleasedPayload(
    override val eventType: WebhookEventType = WebhookEventType.TABLE_RELEASED,
    override val timestamp: String,
    override val sessionId: UUID,
    val tableIdentifier: String,
    val tableName: String
) : WebhookPayload

