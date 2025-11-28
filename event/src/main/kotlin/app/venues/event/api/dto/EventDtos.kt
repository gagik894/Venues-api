package app.venues.event.api.dto

import app.venues.event.domain.EventStatus
import app.venues.event.domain.SessionStatus
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

// ===========================================
// EVENT DTOs
// ===========================================

/**
 * Request DTO for creating/updating events.
 */
data class EventRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 255, message = "Title must not exceed 255 characters")
    val title: String,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,

    @field:Size(max = 500, message = "Image URL must not exceed 500 characters")
    val imgUrl: String? = null,

    val secondaryImgUrls: List<String> = emptyList(),

    val location: String? = null,

    val latitude: Double? = null,

    val longitude: Double? = null,

    val categoryCode: String? = null,

    val tags: Set<String> = emptySet(),

    @field:Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    val currency: String = "AMD",

    val seatingChartId: UUID? = null,

    val status: EventStatus = EventStatus.DRAFT,

    val sessions: List<EventSessionRequest> = emptyList(),

    val translations: List<EventTranslationRequest> = emptyList()
)

/**
 * Response DTO for event details.
 */
data class EventResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val imgUrl: String?,
    val secondaryImgUrls: List<String>,
    val venueId: UUID,
    val venueName: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val categoryId: Long?,
    val categoryName: String?,
    val tags: Set<String>,
    val priceRange: String?,
    val currency: String,
    val seatingChartName: String?,
    val status: EventStatus,
    val createdAt: String,
    val lastModifiedAt: String,
    // Sessions
    val sessions: List<EventSessionResponse> = emptyList(),
    // Statistics
    val sessionCount: Int? = null,
    val upcomingSessionCount: Int? = null,
    // Price Templates
    val priceTemplates: List<PriceTemplateResponse> = emptyList()
)

/**
 * Lightweight response DTO for event lists.
 */
data class EventSummaryResponse(
    val id: UUID,
    val title: String,
    val imgUrl: String?,
    val venueId: UUID,
    val venueName: String,
    val location: String?,
    val categoryName: String?,
    val priceRange: String?,
    val currency: String,
    val status: EventStatus,
    val startDateTime: String?
)

// ===========================================
// EVENT SESSION DTOs
// ===========================================

/**
 * Request DTO for creating/updating event sessions.
 */
data class EventSessionRequest(
    val id: UUID? = null,

    @field:NotNull(message = "Start time is required")
    var startTime: Instant,

    @field:NotNull(message = "End time is required")
    var endTime: Instant,

    @field:Min(value = 0, message = "Tickets count must be non-negative")
    val ticketsCount: Int? = null,

    val priceOverride: BigDecimal? = null,

    @field:Size(max = 100, message = "Price range override must not exceed 100 characters")
    val priceRangeOverride: String? = null,

    val priceTemplateOverrides: List<PriceTemplateOverrideRequest> = emptyList()
)

/**
 * Response DTO for event session.
 */
data class EventSessionResponse(
    val id: UUID,
    val eventId: UUID,
    val startTime: String,
    val endTime: String,
    val ticketsCount: Int?,
    val ticketsSold: Int,
    val remainingTickets: Int?,
    val status: SessionStatus,
    val priceOverride: String?,
    val priceRangeOverride: String?,
    val effectivePriceRange: String?,
    val isBookable: Boolean,
    val createdAt: String
)

// ===========================================
// PRICE TEMPLATE DTOs
// ===========================================

/**
 * Request DTO for price template.
 */
data class PriceTemplateRequest(
    val id: UUID? = null,

    @field:NotBlank(message = "Template name is required")
    @field:Size(max = 100, message = "Template name must not exceed 100 characters")
    val templateName: String,

    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be valid hex code")
    val color: String? = null,

    @field:NotNull(message = "Price is required")
    @field:DecimalMin(value = "0.0", message = "Price must be non-negative")
    var price: BigDecimal,

    @field:Min(value = 0, message = "Display order must be non-negative")
    val displayOrder: Int = 0
)

/**
 * Response DTO for price template.
 */
data class PriceTemplateResponse(
    val id: UUID,
    val templateName: String,
    val color: String?,
    val price: String,
    val isRemovable: Boolean = true // UI hint: false if it's an "Anchor" template
)

/**
 * Request DTO for session price override.
 */
data class PriceTemplateOverrideRequest(
    @field:NotBlank(message = "Template name is required")
    val templateName: String,

    @field:NotNull(message = "Price is required")
    @field:DecimalMin(value = "0.0", message = "Price must be non-negative")
    var price: BigDecimal
)

/**
 * Request DTO for batch assigning price template to seats/tables/GA areas.
 */
data class AssignPriceTemplateRequest(
    @field:NotNull(message = "Template ID is required")
    var templateId: UUID,

    /** Seat IDs to assign the template to. */
    val seatIds: List<Long>? = null,

    /** Table IDs to assign the template to. */
    val tableIds: List<Long>? = null,

    /** GA area IDs to assign the template to. */
    val gaIds: List<Long>? = null
)

// ===========================================
// TRANSLATION DTOs
// ===========================================

/**
 * Request DTO for event translation.
 */
data class EventTranslationRequest(
    @field:NotBlank(message = "Language code is required")
    @field:Pattern(regexp = "^[a-z]{2}$", message = "Language code must be 2 lowercase letters")
    val language: String,

    @field:NotBlank(message = "Translated title is required")
    @field:Size(max = 255, message = "Title must not exceed 255 characters")
    val title: String,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null
)

/**
 * Response DTO for event translation.
 */
data class EventTranslationResponse(
    val id: Long,
    val language: String,
    val title: String,
    val description: String?,
    val createdAt: String,
    val lastModifiedAt: String
)

// ===========================================
// CATEGORY DTOs
// ===========================================

/**
 * Response DTO for event category.
 */
data class EventCategoryResponse(
    val id: Long,
    val code: String,
    val names: Map<String, String>, // language code to name
    val color: String?,
    val icon: String?,
    val displayOrder: Int,
)

