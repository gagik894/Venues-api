package app.venues.event.api.mapper

import app.venues.event.api.dto.*
import app.venues.event.domain.*
import org.springframework.stereotype.Component

/**
 * Mapper for converting between Event entities and DTOs.
 *
 * Handles bidirectional mapping for all event-related objects.
 */
@Component
class EventMapper {

    /**
     * Convert Event entity to EventResponse DTO.
     *
     * @param event Event entity to convert
     * @param venueName Venue name (must be fetched from venue module)
     * @param seatingChartName Optional seating chart name (must be fetched from seating module)
     * @param includeStats Whether to include statistics (session counts)
     * @param language Optional language code for translations (e.g., "hy", "ru", "en")
     */
    fun toResponse(
        event: Event,
        venueName: String,
        seatingChartName: String? = null,
        includeStats: Boolean = false,
        language: String? = null
    ): EventResponse {
        // Apply event translation if requested language exists
        val translation = language?.let { lang ->
            event.translations.find { it.language.equals(lang, ignoreCase = true) }
        }

        // Apply category translation if language is specified
        val categoryName = event.category?.getName(language ?: "en")

        return EventResponse(
            id = event.id,
            title = translation?.title ?: event.title,
            description = translation?.description ?: event.description,
            imgUrl = event.imgUrl,
            secondaryImgUrls = event.secondaryImgUrls.toList(),
            venueId = event.venueId,
            venueName = venueName,
            location = event.location,
            latitude = event.latitude,
            longitude = event.longitude,
            categoryId = event.category?.id,
            categoryName = categoryName,
            tags = event.tags.toSet(),
            priceRange = event.priceRange,
            currency = event.currency,
            seatingChartName = seatingChartName,
            status = event.status,
            createdAt = event.createdAt.toString(),
            lastModifiedAt = event.lastModifiedAt.toString(),
            // Automatically populate sessions
            sessions = event.sessions.map { toSessionResponse(it) },
            sessionCount = if (includeStats) event.sessions.size else null,
            upcomingSessionCount = if (includeStats) event.sessions.count { it.status == EventStatus.UPCOMING } else null
        )
    }

    /**
     * Convert EventSession entity to EventSessionResponse DTO.
     */
    fun toSessionResponse(session: EventSession): EventSessionResponse {
        return EventSessionResponse(
            id = session.id,
            eventId = session.event.id,
            startTime = session.startTime.toString(),
            endTime = session.endTime.toString(),
            ticketsCount = session.ticketsCount,
            ticketsSold = session.ticketsSold,
            remainingTickets = session.getRemainingTickets(),
            status = session.status,
            priceOverride = session.priceOverride?.toString(),
            priceRangeOverride = session.priceRangeOverride,
            effectivePriceRange = session.getEffectivePriceRange(),
            isBookable = session.isBookable(),
            createdAt = session.createdAt.toString()
        )
    }

    /**
     * Convert EventPriceTemplate entity to PriceTemplateResponse DTO.
     */
    fun toPriceTemplateResponse(template: EventPriceTemplate): PriceTemplateResponse {
        return PriceTemplateResponse(
            id = template.id,
            templateName = template.templateName,
            color = template.color,
            price = template.price.toString()
        )
    }

    /**
     * Convert EventTranslation entity to EventTranslationResponse DTO.
     */
    fun toTranslationResponse(translation: EventTranslation): EventTranslationResponse {
        return EventTranslationResponse(
            id = translation.id ?: throw IllegalArgumentException("Translation ID cannot be null"),
            language = translation.language,
            title = translation.title,
            description = translation.description,
            createdAt = translation.createdAt.toString(),
            lastModifiedAt = translation.lastModifiedAt.toString()
        )
    }

    /**
     * Convert EventCategory entity to EventCategoryResponse DTO.
     */
    fun toCategoryResponse(category: EventCategory): EventCategoryResponse {
        return EventCategoryResponse(
            id = category.id ?: throw IllegalArgumentException("Category ID must not be null"),
            code = category.code,
            names = category.names,
            color = category.color,
            icon = category.icon,
            displayOrder = category.displayOrder,
        )
    }
}

