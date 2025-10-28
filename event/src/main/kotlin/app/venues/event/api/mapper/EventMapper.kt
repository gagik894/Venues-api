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
     */
    fun toResponse(event: Event, includeStats: Boolean = false): EventResponse {
        return EventResponse(
            id = event.id!!,
            title = event.title,
            description = event.description,
            imgUrl = event.imgUrl,
            secondaryImgUrls = event.secondaryImgUrls.toList(),
            venueId = event.venue.id!!,
            venueName = event.venue.name,
            location = event.location,
            latitude = event.latitude,
            longitude = event.longitude,
            categoryId = event.category?.id,
            categoryName = event.category?.name,
            tags = event.tags.toSet(),
            priceRange = event.priceRange,
            currency = event.currency,
            seatingChartName = event.seatingChart?.name,
            status = event.status,
            createdAt = event.createdAt.toString(),
            lastModifiedAt = event.lastModifiedAt.toString(),
            sessionCount = if (includeStats) event.sessions.size else null,
            upcomingSessionCount = if (includeStats) event.sessions.count { it.status == EventStatus.UPCOMING } else null
        )
    }

    /**
     * Convert EventSession entity to EventSessionResponse DTO.
     */
    fun toSessionResponse(session: EventSession): EventSessionResponse {
        return EventSessionResponse(
            id = session.id!!,
            eventId = session.event.id!!,
            startTime = session.startTime.toString(),
            endTime = session.endTime.toString(),
            ticketsCount = session.ticketsCount,
            ticketsSold = session.ticketsSold,
            remainingTickets = session.getRemainingTickets(),
            status = session.status,
            priceOverride = session.priceOverride?.toString(),
            priceRangeOverride = session.priceRangeOverride,
            effectivePrice = session.getEffectivePrice()?.toString(),
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
            id = template.id!!,
            templateName = template.templateName,
            color = template.color,
            price = template.price.toString(),
            displayOrder = template.displayOrder
        )
    }

    /**
     * Convert EventTranslation entity to EventTranslationResponse DTO.
     */
    fun toTranslationResponse(translation: EventTranslation): EventTranslationResponse {
        return EventTranslationResponse(
            id = translation.id!!,
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
        val translationsMap = category.translations.associate { it.language to it.name }

        return EventCategoryResponse(
            id = category.id!!,
            categoryKey = category.categoryKey,
            name = category.name,
            color = category.color,
            icon = category.icon,
            displayOrder = category.displayOrder,
            translations = translationsMap
        )
    }
}

