package app.venues.venue.api.mapper

import app.venues.location.domain.City
import app.venues.location.repository.CityRepository
import app.venues.venue.api.dto.*
import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenueCategory
import app.venues.venue.domain.VenueSchedule
import app.venues.venue.domain.VenueTranslation
import app.venues.venue.repository.VenueCategoryRepository
import org.springframework.stereotype.Component

/**
 * Maps between Venue domain entities and DTOs.
 * Handles localization and data formatting for API responses.
 */
@Component
class VenueMapper(
    private val cityRepository: CityRepository,
    private val categoryRepository: VenueCategoryRepository
) {

    /**
     * Converts Venue entity to public response DTO.
     *
     * @param venue Venue entity
     * @param lang Language code for localization (defaults to "en")
     * @param includeStats Whether to include follower/rating statistics
     * @return VenueResponse for public API
     */
    fun toPublicResponse(
        venue: Venue,
        lang: String = "en",
        includeStats: Boolean = false
    ): VenueResponse {
        return VenueResponse(
            id = venue.id,
            slug = venue.slug,
            name = venue.getName(lang),
            description = venue.translations
                .firstOrNull { it.language == lang }?.description
                ?: venue.description,
            logoUrl = venue.logoUrl,
            coverImageUrl = venue.coverImageUrl,

            citySlug = venue.city.slug,
            cityName = venue.city.getName(lang),

            categoryCode = venue.category?.code,
            categoryName = venue.category?.getName(lang),
            categoryColor = venue.category?.color,

            phoneNumber = venue.phoneNumber,
            website = venue.website,
            status = venue.status,

            followerCount = if (includeStats) 0L else null, // TODO: implement follower count
            averageRating = if (includeStats) null else null // TODO: implement rating calculation
        )
    }

    /**
     * Converts Venue entity to detailed response DTO.
     *
     * @param venue Venue entity
     * @param lang Language code for localization
     * @return VenueDetailResponse with full information
     */
    fun toDetailResponse(venue: Venue, lang: String = "en"): VenueDetailResponse {
        return VenueDetailResponse(
            id = venue.id,
            slug = venue.slug,
            name = venue.getName(lang),
            description = venue.translations
                .firstOrNull { it.language == lang }?.description
                ?: venue.description,
            logoUrl = venue.logoUrl,
            coverImageUrl = venue.coverImageUrl,

            address = venue.address,
            citySlug = venue.city.slug,
            cityName = venue.city.getName(lang),
            latitude = venue.latitude,
            longitude = venue.longitude,
            timeZone = venue.timeZone,

            categoryCode = venue.category?.code,
            categoryName = venue.category?.getName(lang),
            categoryColor = venue.category?.color,
            categoryIcon = venue.category?.icon,

            phoneNumber = venue.phoneNumber,
            website = venue.website,
            contactEmail = venue.contactEmail,
            socialLinks = venue.socialLinks,

            isAlwaysOpen = venue.isAlwaysOpen,
            customDomain = venue.customDomain,
            status = venue.status,

            translations = venue.translations.map(::toTranslationDto),
            schedules = emptyList(), // TODO: load schedules

            followerCount = 0L, // TODO: implement
            averageRating = null, // TODO: implement
            reviewCount = 0L, // TODO: implement

            createdAt = venue.createdAt,
            lastModifiedAt = venue.lastModifiedAt
        )
    }

    /**
     * Converts Venue entity to admin response DTO (includes sensitive data).
     *
     * @param venue Venue entity
     * @param lang Language code for localization
     * @return VenueAdminResponse with full administrative information
     */
    fun toAdminResponse(venue: Venue, lang: String = "en"): VenueAdminResponse {
        return VenueAdminResponse(
            id = venue.id,
            slug = venue.slug,
            name = venue.name,
            legalName = venue.legalName,
            taxId = venue.taxId,
            description = venue.description,

            address = venue.address,
            citySlug = venue.city.slug,
            cityName = venue.city.getName(lang),
            latitude = venue.latitude,
            longitude = venue.longitude,
            timeZone = venue.timeZone,

            categoryCode = venue.category?.code,
            categoryName = venue.category?.getName(lang),

            phoneNumber = venue.phoneNumber,
            website = venue.website,
            contactEmail = venue.contactEmail,
            socialLinks = venue.socialLinks,

            ownershipType = venue.ownershipType,
            notificationEmails = venue.notificationEmails,

            logoUrl = venue.logoUrl,
            coverImageUrl = venue.coverImageUrl,
            customDomain = venue.customDomain,
            isAlwaysOpen = venue.isAlwaysOpen,

            status = venue.status,

            createdAt = venue.createdAt,
            lastModifiedAt = venue.lastModifiedAt
        )
    }

    /**
     * Creates a new Venue entity from creation request.
     *
     * @param request CreateVenueRequest with venue data
     * @param city City entity (must be pre-fetched)
     * @param category VenueCategory entity (optional, pre-fetched)
     * @return New Venue entity (not yet persisted)
     */
    fun toEntity(
        request: CreateVenueRequest,
        city: City,
        category: VenueCategory?
    ): Venue {
        return Venue(
            name = request.name,
            slug = request.slug,
            description = request.description,

            address = request.address,
            city = city,

            legalName = request.legalName,
            taxId = request.taxId,

            latitude = request.latitude,
            longitude = request.longitude,
            timeZone = request.timeZone,

            category = category,

            phoneNumber = request.phoneNumber,
            website = request.website,
            contactEmail = request.contactEmail,

            ownershipType = request.ownershipType,

            organizationId = request.organizationId,
        )
    }

    /**
     * Updates existing Venue entity from update request.
     * Only updates fields that are non-null in the request.
     *
     * @param venue Existing venue entity to update
     * @param request UpdateVenueRequest with changes
     * @param city Optional new city (if cityId provided)
     * @param category Optional new category (if categoryCode provided)
     */
    fun updateEntity(
        venue: Venue,
        request: UpdateVenueRequest,
        city: City? = null,
        category: VenueCategory? = null
    ) {
        request.name?.let { venue.name = it }
        request.description?.let { venue.description = it }
        request.legalName?.let { venue.legalName = it }
        request.taxId?.let { venue.taxId = it }

        request.address?.let { venue.address = it }
        city?.let { venue.city = it }

        request.latitude?.let { venue.latitude = it }
        request.longitude?.let { venue.longitude = it }
        request.timeZone?.let { venue.timeZone = it }

        if (category !== null || request.categoryCode != null) {
            venue.category = category
        }

        request.phoneNumber?.let { venue.phoneNumber = it }
        request.website?.let { venue.website = it }
        request.contactEmail?.let { venue.contactEmail = it }

        request.socialLinks?.let { venue.socialLinks = it }
        request.notificationEmails?.let { venue.notificationEmails = it }

        request.logoUrl?.let { venue.logoUrl = it }
        request.coverImageUrl?.let { venue.coverImageUrl = it }
        request.customDomain?.let { venue.customDomain = it }
        request.isAlwaysOpen?.let { venue.isAlwaysOpen = it }

        request.ownershipType?.let { venue.ownershipType = it }
    }

    /**
     * Converts VenueTranslation entity to DTO.
     */
    fun toTranslationDto(translation: VenueTranslation): VenueTranslationDto {
        return VenueTranslationDto(
            language = translation.language,
            name = translation.name,
            description = translation.description
        )
    }

    /**
     * Converts VenueSchedule entity to DTO.
     */
    fun toScheduleDto(schedule: VenueSchedule): VenueScheduleDto {
        return VenueScheduleDto(
            dayOfWeek = schedule.dayOfWeek.toString(),
            openTime = schedule.openTime?.toString(),
            closeTime = schedule.closeTime?.toString(),
            isClosed = schedule.isClosed
        )
    }

    /**
     * Converts VenueCategory entity to DTO.
     */
    fun toCategoryDto(category: VenueCategory, lang: String = "en"): VenueCategoryDto {
        return VenueCategoryDto(
            code = category.code,
            name = category.getName(lang),
            color = category.color,
            icon = category.icon,
            displayOrder = category.displayOrder
        )
    }
}

