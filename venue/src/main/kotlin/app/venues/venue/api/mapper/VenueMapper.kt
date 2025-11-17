package app.venues.venue.api.mapper

import app.venues.venue.api.dto.*
import app.venues.venue.domain.*
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Mapper for converting between Venue entities and DTOs.
 *
 * Handles bidirectional mapping for all venue-related objects.
 * Includes proper type conversions for timestamps and monetary values.
 */
@Component
class VenueMapper {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ===========================================
    // VENUE MAPPERS
    // ===========================================

    /**
     * Convert Venue entity to VenueResponse DTO
     *
     * @param venue Venue entity to convert
     * @param includeStats Whether to include statistics (follower count, reviews, etc.)
     * @param language Optional language code for translations (e.g., "hy", "ru", "en")
     */
    fun toResponse(venue: Venue, includeStats: Boolean = false, language: String? = null): VenueResponse {
        // Apply translation if requested language exists
        val translation = language?.let { lang ->
            venue.translations.find { it.language.equals(lang, ignoreCase = true) }
        }

        return VenueResponse(
            id = venue.id,
            name = translation?.name ?: venue.name,
            description = translation?.description ?: venue.description,
            imageUrl = venue.imageUrl,
            address = venue.address,
            city = venue.city,
            latitude = venue.latitude,
            longitude = venue.longitude,
            email = null, //TODO: add email in model
            phoneNumber = venue.phoneNumber,
            website = venue.website,
            customDomain = venue.customDomain,
            category = venue.category,
            isAlwaysOpen = venue.isAlwaysOpen,
            verified = venue.verified,
            official = venue.official,
            status = venue.status,
            followerCount = if (includeStats) venue.followers.size.toLong() else null,
            reviewCount = if (includeStats) venue.reviews.filter { !it.isModerated }.size.toLong() else null,
            averageRating = if (includeStats && venue.reviews.isNotEmpty()) {
                venue.reviews.filter { !it.isModerated }.map { it.rating }.average()
            } else null
        )
    }

    /**
     * Convert Venue entity to VenueDetailedResponse DTO
     */
    fun toDetailedResponse(venue: Venue): VenueDetailedResponse {
        return VenueDetailedResponse(
            venue = toResponse(venue, includeStats = true),
            schedules = venue.schedules.map { toScheduleResponse(it) },
            translations = venue.translations.map { toTranslationResponse(it) },
            photos = venue.photos.map { toPhotoResponse(it) }
        )
    }

    // ===========================================
    // SCHEDULE MAPPERS
    // ===========================================

    /**
     * Convert VenueSchedule entity to response DTO
     */
    fun toScheduleResponse(schedule: VenueSchedule): VenueScheduleResponse {
        return VenueScheduleResponse(
            id = schedule.id!!,
            dayOfWeek = schedule.dayOfWeek,
            openTime = schedule.openTime?.format(timeFormatter),
            closeTime = schedule.closeTime?.format(timeFormatter),
            isClosed = schedule.isClosed
        )
    }

    /**
     * Convert schedule request DTO to entity
     */
    fun toScheduleEntity(request: VenueScheduleRequest, venue: Venue): VenueSchedule {
        return VenueSchedule(
            venue = venue,
            dayOfWeek = request.dayOfWeek,
            openTime = request.openTime?.let { LocalTime.parse(it, timeFormatter) },
            closeTime = request.closeTime?.let { LocalTime.parse(it, timeFormatter) },
            isClosed = request.isClosed
        )
    }

    // ===========================================
    // TRANSLATION MAPPERS
    // ===========================================

    /**
     * Convert VenueTranslation entity to response DTO
     */
    fun toTranslationResponse(translation: VenueTranslation): VenueTranslationResponse {
        return VenueTranslationResponse(
            id = translation.id!!,
            language = translation.language,
            name = translation.name,
            description = translation.description,
            createdAt = translation.createdAt,
            lastModifiedAt = translation.lastModifiedAt
        )
    }

    /**
     * Convert translation request DTO to entity
     */
    fun toTranslationEntity(request: VenueTranslationRequest, venue: Venue): VenueTranslation {
        return VenueTranslation(
            venue = venue,
            language = request.language.lowercase(),
            name = request.name,
            description = request.description
        )
    }

    // ===========================================
    // PHOTO MAPPERS
    // ===========================================

    /**
     * Convert VenuePhoto entity to response DTO
     */
    fun toPhotoResponse(photo: VenuePhoto): VenuePhotoResponse {
        return VenuePhotoResponse(
            id = photo.id!!,
            url = photo.url,
            caption = photo.caption,
            displayOrder = photo.displayOrder,
            userId = photo.userId,
            createdAt = photo.createdAt,
        )
    }

    /**
     * Convert photo request DTO to entity
     */
    fun toPhotoEntity(request: VenuePhotoRequest, venue: Venue, userId: UUID): VenuePhoto {
        return VenuePhoto(
            venue = venue,
            userId = userId,
            url = request.url,
            caption = request.caption,
            displayOrder = request.displayOrder
        )
    }

    // ===========================================
    // REVIEW MAPPERS
    // ===========================================

    /**
     * Convert VenueReview entity to response DTO
     */
    fun toReviewResponse(review: VenueReview): VenueReviewResponse {
        return VenueReviewResponse(
            id = review.id!!,
            userId = review.userId,
            rating = review.rating,
            comment = review.comment,
            createdAt = review.createdAt,
            lastModifiedAt = review.lastModifiedAt,
            isModerated = review.isModerated
        )
    }

    /**
     * Convert review request DTO to entity
     */
    fun toReviewEntity(request: VenueReviewRequest, venue: Venue, userId: UUID): VenueReview {
        return VenueReview(
            venue = venue,
            userId = userId,
            rating = request.rating,
            comment = request.comment
        )
    }

    // ===========================================
    // PROMO CODE MAPPERS
    // ===========================================

    /**
     * Convert VenuePromoCode entity to response DTO
     */
    fun toPromoCodeResponse(promoCode: VenuePromoCode): VenuePromoCodeResponse {
        return VenuePromoCodeResponse(
            id = promoCode.id,
            code = promoCode.code,
            description = promoCode.description,
            discountType = promoCode.discountType,
            discountValue = promoCode.discountValue.toString(),
            minOrderAmount = promoCode.minOrderAmount.toString(),
            maxDiscountAmount = promoCode.maxDiscountAmount.toString(),
            maxUsageCount = promoCode.maxUsageCount,
            currentUsageCount = promoCode.currentUsageCount,
            expiresAt = promoCode.expiresAt,
            isActive = promoCode.isActive,
            createdAt = promoCode.createdAt
        )
    }

    /**
     * Convert promo code request DTO to entity
     */
    fun toPromoCodeEntity(request: VenuePromoCodeRequest, venue: Venue): VenuePromoCode {
        return VenuePromoCode(
            venue = venue,
            code = request.code.uppercase(),
            description = request.description,
            discountType = request.discountType,
            discountValue = request.discountValue,
            minOrderAmount = request.minOrderAmount,
            maxDiscountAmount = request.maxDiscountAmount,
            maxUsageCount = request.maxUsageCount,
            expiresAt = request.expiresAt
        )
    }
}

