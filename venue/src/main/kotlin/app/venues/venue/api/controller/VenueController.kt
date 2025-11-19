package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.shared.persistence.util.PageableMapper
import app.venues.shared.security.util.SecurityUtil
import app.venues.venue.api.dto.*
import app.venues.venue.service.VenueAuthService
import app.venues.venue.service.VenueService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * REST Controller for venue management operations.
 *
 * This controller handles all venue-related operations including:
 * - Profile management
 * - Schedule management
 * - Translation management
 * - Photo management
 * - Review management
 * - Promo code management
 * - Follower management
 *
 * Most endpoints require authentication with ROLE_VENUE.
 * Public endpoints (search, view) don't require authentication.
 */
@RestController
@RequestMapping("/api/v1/venues")
@Validated
@Tag(name = "Venue Management", description = "Venue profile and management endpoints")
class VenueController(
    private val venueService: VenueService,
    private val venueAuthService: VenueAuthService,
    private val securityUtil: SecurityUtil
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // ===========================================
    // PUBLIC ENDPOINTS (No authentication required)
    // ===========================================

    /**
     * Get all active venues (public listing).
     */
    @GetMapping
    @Operation(
        summary = "Get all active venues",
        description = "Public endpoint to browse all active venues. Use 'lang' parameter for translations (e.g., ?lang=hy for Armenian)"
    )
    fun getAllVenues(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDirection: String?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<VenueResponse>> {
        logger.debug("Fetching all active venues, language: {}", lang)

        // Whitelist of allowed sort fields for Venue entity
        val allowedSortFields = setOf("createdAt", "name", "city", "category", "id")

        val pageable = PageableMapper.createPageable(
            limit = limit,
            offset = offset,
            sortBy = sortBy,
            sortDirection = sortDirection,
            allowedSortFields = allowedSortFields
        )

        val venues = venueService.getAllActiveVenues(pageable, language = lang)

        return ApiResponse.success(
            data = venues,
            message = "Venues retrieved successfully"
        )
    }

    /**
     * Search venues by name.
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search venues by name",
        description = "Search for venues by name (case-insensitive). Use 'lang' parameter for translations"
    )
    fun searchVenues(
        @RequestParam("q") searchTerm: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<VenueResponse>> {
        logger.debug("Searching venues: {}, language: {}", searchTerm, lang)

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)

        val venues = venueService.searchVenues(searchTerm, pageable, language = lang)

        return ApiResponse.success(
            data = venues,
            message = "Search completed successfully"
        )
    }

    /**
     * Get venues by city slug.
     *
     * Frontend usage: GET /api/v1/venues/city/yerevan?lang=hy
     */
    @GetMapping("/city/{citySlug}")
    @Operation(
        summary = "Get venues by city",
        description = "Get all venues in a specific city using city slug (e.g., 'yerevan', 'gyumri'). Use 'lang' parameter for translations"
    )
    fun getVenuesByCity(
        @PathVariable citySlug: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<VenueResponse>> {
        logger.debug("Fetching venues in city: {}, language: {}", citySlug, lang)

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)

        val venues = venueService.getVenuesByCity(citySlug, pageable, language = lang)

        return ApiResponse.success(
            data = venues,
            message = "Venues retrieved successfully"
        )
    }

    /**
     * Get venues by region code.
     *
     * Frontend usage: GET /api/v1/venues/region/AM-ER?lang=hy
     */
    @GetMapping("/region/{regionCode}")
    @Operation(
        summary = "Get venues by region",
        description = "Get all venues in a specific region using ISO region code (e.g., 'AM-ER' for Yerevan, 'AM-SH' for Shirak). Use 'lang' parameter for translations"
    )
    fun getVenuesByRegion(
        @PathVariable regionCode: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<VenueResponse>> {
        logger.debug("Fetching venues in region: {}, language: {}", regionCode, lang)

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)

        val venues = venueService.getVenuesByRegion(regionCode, pageable, language = lang)

        return ApiResponse.success(
            data = venues,
            message = "Venues retrieved successfully"
        )
    }

    /**
     * Get venues by category.
     */
    @GetMapping("/category/{category}")
    @Operation(
        summary = "Get venues by category",
        description = "Get all venues in a specific category. Use 'lang' parameter for translations"
    )
    fun getVenuesByCategory(
        @PathVariable category: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<VenueResponse>> {
        logger.debug("Fetching venues in category: {}, language: {}", category, lang)

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)

        val venues = venueService.getVenuesByCategory(category, pageable, language = lang)

        return ApiResponse.success(
            data = venues,
            message = "Venues retrieved successfully"
        )
    }

    /**
     * Get venue by ID (public, includes detailed info).
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get venue by ID",
        description = "Get detailed venue information by ID. Use 'lang' parameter for translations"
    )
    fun getVenueById(
        @PathVariable id: UUID,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<VenueDetailedResponse> {
        logger.debug("Fetching venue by ID: {}, language: {}", id, lang)

        val venue = venueService.getVenueDetailed(id)

        return ApiResponse.success(
            data = venue,
            message = "Venue retrieved successfully"
        )
    }

    // ===========================================
    // VENUE PROFILE MANAGEMENT (Requires ROLE_VENUE)
    // ===========================================

    /**
     * Get current venue's profile.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Get current venue profile",
        description = "Get authenticated venue's profile information"
    )
    fun getCurrentVenue(): ApiResponse<VenueResponse> {
        val venueId = securityUtil.getCurrentUserId()
        logger.debug("Fetching current venue profile: {}", venueId)

        val venue = venueService.getVenueById(venueId, includeStats = true)

        return ApiResponse.success(
            data = venue,
            message = "Venue profile retrieved successfully"
        )
    }

    /**
     * Update venue profile.
     */
    @PutMapping("/me")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Update venue profile",
        description = "Update authenticated venue's profile information"
    )
    fun updateVenue(
        @Valid @RequestBody request: VenueUpdateRequest
    ): ApiResponse<VenueResponse> {
        val venueId = securityUtil.getCurrentUserId()
        logger.info("Updating venue profile: {}", venueId)

        val venue = venueService.updateVenue(venueId, request)

        return ApiResponse.success(
            data = venue,
            message = "Venue profile updated successfully"
        )
    }

    // ===========================================
    // SCHEDULE MANAGEMENT
    // ===========================================

    /**
     * Get venue schedules.
     */
    @GetMapping("/{id}/schedules")
    @Operation(
        summary = "Get venue schedules",
        description = "Get operating hours for all days of the week"
    )
    fun getSchedules(
        @PathVariable id: UUID
    ): ApiResponse<List<VenueScheduleResponse>> {
        logger.debug("Fetching schedules for venue: {}", id)

        val schedules = venueService.getSchedules(id)

        return ApiResponse.success(
            data = schedules,
            message = "Schedules retrieved successfully"
        )
    }

    /**
     * Set or update schedule for a specific day.
     */
    @PutMapping("/me/schedules")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Set venue schedule",
        description = "Set operating hours for a specific day"
    )
    fun setSchedule(
        @Valid @RequestBody request: VenueScheduleRequest
    ): ApiResponse<VenueScheduleResponse> {
        val venueId = securityUtil.getCurrentUserId()
        logger.info("Setting schedule for venue {} on {}", venueId, request.dayOfWeek)

        val schedule = venueService.setSchedule(venueId, request)

        return ApiResponse.success(
            data = schedule,
            message = "Schedule updated successfully"
        )
    }

    // ===========================================
    // TRANSLATION MANAGEMENT
    // ===========================================

    /**
     * Get venue translations.
     */
    @GetMapping("/{id}/translations")
    @Operation(
        summary = "Get venue translations",
        description = "Get all translations for a venue"
    )
    fun getTranslations(
        @PathVariable id: UUID
    ): ApiResponse<List<VenueTranslationResponse>> {
        logger.debug("Fetching translations for venue: {}", id)

        val translations = venueService.getTranslations(id)

        return ApiResponse.success(
            data = translations,
            message = "Translations retrieved successfully"
        )
    }

    /**
     * Add or update translation.
     */
    @PutMapping("/me/translations")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Set venue translation",
        description = "Add or update translation for a specific language"
    )
    fun setTranslation(
        @Valid @RequestBody request: VenueTranslationRequest
    ): ApiResponse<VenueTranslationResponse> {
        val venueId = securityUtil.getCurrentUserId()
        logger.info("Setting translation for venue {} in language {}", venueId, request.language)

        val translation = venueService.setTranslation(venueId, request)

        return ApiResponse.success(
            data = translation,
            message = "Translation saved successfully"
        )
    }

    /**
     * Delete translation.
     */
    @DeleteMapping("/me/translations/{language}")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Delete venue translation",
        description = "Delete translation for a specific language"
    )
    fun deleteTranslation(
        @PathVariable language: String
    ): ApiResponse<Unit> {
        val venueId = securityUtil.getCurrentUserId()
        logger.info("Deleting translation for venue {} in language {}", venueId, language)

        venueService.deleteTranslation(venueId, language)

        return ApiResponse.success(
            message = "Translation deleted successfully"
        )
    }

    // ===========================================
    // PHOTO MANAGEMENT
    // ===========================================

    /**
     * Get venue photos.
     */
    @GetMapping("/{id}/photos")
    @Operation(
        summary = "Get venue photos",
        description = "Get all photos for a venue"
    )
    fun getPhotos(
        @PathVariable id: UUID
    ): ApiResponse<List<VenuePhotoResponse>> {
        logger.debug("Fetching photos for venue: {}", id)

        val photos = venueService.getPhotos(id)

        return ApiResponse.success(
            data = photos,
            message = "Photos retrieved successfully"
        )
    }

    /**
     * Add photo to venue.
     */
    @PostMapping("/me/photos")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Add venue photo",
        description = "Upload a new photo to the venue"
    )
    fun addPhoto(
        @Valid @RequestBody request: VenuePhotoRequest
    ): ApiResponse<VenuePhotoResponse> {
        val venueId = securityUtil.getCurrentUserId()
        val userId = securityUtil.getCurrentUserId()
        logger.info("Adding photo to venue {} by user {}", venueId, userId)

        val photo = venueService.addPhoto(venueId, userId, request)

        return ApiResponse.success(
            data = photo,
            message = "Photo added successfully",
        )
    }

    /**
     * Delete photo.
     */
    @DeleteMapping("/me/photos/{photoId}")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Delete venue photo",
        description = "Delete a photo from the venue"
    )
    fun deletePhoto(
        @PathVariable photoId: Long
    ): ApiResponse<Unit> {
        val venueId = securityUtil.getCurrentUserId()
        val userId = securityUtil.getCurrentUserId()
        logger.info("Deleting photo {} from venue {}", photoId, venueId)

        venueService.deletePhoto(venueId, photoId, userId)

        return ApiResponse.success(
            message = "Photo deleted successfully"
        )
    }

    // ===========================================
    // REVIEW MANAGEMENT
    // ===========================================

    /**
     * Get venue reviews.
     */
    @GetMapping("/{id}/reviews")
    @Operation(
        summary = "Get venue reviews",
        description = "Get all reviews for a venue"
    )
    fun getReviews(
        @PathVariable id: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<VenueReviewResponse>> {
        logger.debug("Fetching reviews for venue: {}", id)

        val allowedSortFields = setOf("createdAt", "rating", "id")
        val pageable = PageableMapper.createPageable(
            limit = limit,
            offset = offset,
            sortBy = "createdAt",
            sortDirection = "DESC",
            allowedSortFields = allowedSortFields
        )

        val reviews = venueService.getReviews(id, pageable)

        return ApiResponse.success(
            data = reviews,
            message = "Reviews retrieved successfully"
        )
    }

    /**
     * Add or update review (requires USER authentication).
     */
    @PutMapping("/{id}/reviews")
    @PreAuthorize("hasRole('USER')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Add or update review",
        description = "Add or update your review for a venue"
    )
    fun addOrUpdateReview(
        @PathVariable id: UUID,
        @Valid @RequestBody request: VenueReviewRequest
    ): ApiResponse<VenueReviewResponse> {
        val userId = securityUtil.getCurrentUserId()
        logger.info("Adding/updating review for venue {} by user {}", id, userId)

        val review = venueService.addOrUpdateReview(id, userId, request)

        return ApiResponse.success(
            data = review,
            message = "Review saved successfully"
        )
    }

    /**
     * Delete review (requires USER authentication).
     */
    @DeleteMapping("/{id}/reviews")
    @PreAuthorize("hasRole('USER')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Delete review",
        description = "Delete your review for a venue"
    )
    fun deleteReview(
        @PathVariable id: UUID
    ): ApiResponse<Unit> {
        val userId = securityUtil.getCurrentUserId()
        logger.info("Deleting review for venue {} by user {}", id, userId)

        venueService.deleteReview(id, userId)

        return ApiResponse.success(
            message = "Review deleted successfully"
        )
    }

    // ===========================================
    // PROMO CODE MANAGEMENT
    // ===========================================

    /**
     * Get venue promo codes.
     */
    @GetMapping("/me/promo-codes")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Get venue promo codes",
        description = "Get all active promo codes for the venue"
    )
    fun getPromoCodes(): ApiResponse<List<VenuePromoCodeResponse>> {
        val venueId = securityUtil.getCurrentUserId()
        logger.debug("Fetching promo codes for venue: {}", venueId)

        val promoCodes = venueService.getPromoCodes(venueId)

        return ApiResponse.success(
            data = promoCodes,
            message = "Promo codes retrieved successfully"
        )
    }

    /**
     * Create promo code.
     */
    @PostMapping("/me/promo-codes")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create promo code",
        description = "Create a new promo code for the venue"
    )
    fun createPromoCode(
        @Valid @RequestBody request: VenuePromoCodeRequest
    ): ApiResponse<VenuePromoCodeResponse> {
        val venueId = securityUtil.getCurrentUserId()
        logger.info("Creating promo code for venue: {}", venueId)

        val promoCode = venueService.createPromoCode(venueId, request)

        return ApiResponse.success(
            data = promoCode,
            message = "Promo code created successfully",
        )
    }

    /**
     * Deactivate promo code.
     */
    @DeleteMapping("/me/promo-codes/{codeId}")
    @PreAuthorize("hasRole('VENUE')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Deactivate promo code",
        description = "Deactivate a promo code"
    )
    fun deactivatePromoCode(
        @PathVariable codeId: UUID
    ): ApiResponse<Unit> {
        val venueId = securityUtil.getCurrentUserId()
        logger.info("Deactivating promo code {} for venue {}", codeId, venueId)

        venueService.deactivatePromoCode(venueId, codeId)

        return ApiResponse.success(
            message = "Promo code deactivated successfully"
        )
    }

    // ===========================================
    // FOLLOWER MANAGEMENT
    // ===========================================

    /**
     * Follow a venue (requires USER authentication).
     */
    @PostMapping("/{id}/follow")
    @PreAuthorize("hasRole('USER')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Follow venue",
        description = "Follow a venue to receive updates"
    )
    fun followVenue(
        @PathVariable id: UUID
    ): ApiResponse<Unit> {
        val userId = securityUtil.getCurrentUserId()
        logger.info("User {} following venue {}", userId, id)

        venueService.followVenue(id, userId)

        return ApiResponse.success(
            message = "You are now following this venue"
        )
    }

    /**
     * Unfollow a venue (requires USER authentication).
     */
    @DeleteMapping("/{id}/follow")
    @PreAuthorize("hasRole('USER')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Unfollow venue",
        description = "Unfollow a venue"
    )
    fun unfollowVenue(
        @PathVariable id: UUID
    ): ApiResponse<Unit> {
        val userId = securityUtil.getCurrentUserId()
        logger.info("User {} unfollowing venue {}", userId, id)

        venueService.unfollowVenue(id, userId)

        return ApiResponse.success(
            message = "You have unfollowed this venue"
        )
    }

    /**
     * Check if current user is following a venue.
     */
    @GetMapping("/{id}/follow")
    @PreAuthorize("hasRole('USER')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Check if following",
        description = "Check if you are following this venue"
    )
    fun isFollowing(
        @PathVariable id: UUID
    ): ApiResponse<Map<String, Boolean>> {
        val userId = securityUtil.getCurrentUserId()
        val isFollowing = venueService.isFollowing(id, userId)

        return ApiResponse.success(
            data = mapOf("isFollowing" to isFollowing)
        )
    }
}

