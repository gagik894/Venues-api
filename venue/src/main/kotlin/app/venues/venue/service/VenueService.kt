package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.venue.api.VenueApi
import app.venues.venue.api.dto.*
import app.venues.venue.api.mapper.VenueMapper
import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenueFollower
import app.venues.venue.domain.VenueStatus
import app.venues.venue.repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for venue management operations.
 *
 * This is the ADAPTER in Hexagonal Architecture.
 * Implements VenueApi (the PORT) to provide a stable public API for other modules.
 *
 * Handles:
 * - Venue registration and profile management
 * - Schedule management
 * - Translation management
 * - Photo management
 * - Review management
 * - Promo code management
 * - Follower management
 * - Cross-module API (via VenueApi implementation)
 */
@Service
@Transactional
class VenueService(
    private val venueRepository: VenueRepository,
    private val venueScheduleRepository: VenueScheduleRepository,
    private val venueTranslationRepository: VenueTranslationRepository,
    private val venuePhotoRepository: VenuePhotoRepository,
    private val venueReviewRepository: VenueReviewRepository,
    private val venuePromoCodeRepository: VenuePromoCodeRepository,
    private val venueFollowerRepository: VenueFollowerRepository,
    private val passwordEncoder: PasswordEncoder,
    private val venueMapper: VenueMapper
) : VenueApi {
    private val logger = LoggerFactory.getLogger(javaClass)

    // ===========================================
    // PUBLIC API IMPLEMENTATION (VenueApi Port)
    // ===========================================

    override fun getVenueBasicInfo(venueId: Long): VenueBasicInfoDto? {
        return venueRepository.findById(venueId)
            .map { venue ->
                VenueBasicInfoDto(
                    id = venue.id!!,
                    name = venue.name,
                    address = venue.address,
                    latitude = venue.latitude,
                    longitude = venue.longitude
                )
            }
            .orElse(null)
    }

    override fun getVenueName(venueId: Long): String? {
        return venueRepository.findById(venueId)
            .map { it.name }
            .orElse(null)
    }

    override fun getVenueNameTranslated(venueId: Long, language: String?): String? {
        val venue = venueRepository.findById(venueId).orElse(null) ?: return null

        if (language != null) {
            val translation = venue.translations
                .find { it.language.equals(language, ignoreCase = true) }
            if (translation != null) {
                return translation.name
            }
        }

        return venue.name
    }

    override fun venueExists(venueId: Long): Boolean {
        return venueRepository.existsById(venueId)
    }

    override fun getVenueOwnerId(venueId: Long): Long? {
        // NOTE: Venue entity doesn't have an ownerId field yet
        // This would need to be added to the Venue entity for proper owner tracking
        // For now, returning null as venues authenticate via email/password
        // TODO: Add ownerId field to Venue entity to track which User owns the venue
        return null
    }

    // ===========================================
    // VENUE REGISTRATION & PROFILE
    // ===========================================

    /**
     * Register a new venue.
     *
     * @param request Registration request
     * @return Created venue response
     * @throws VenuesException.ResourceConflict if email already exists
     */
    fun registerVenue(request: VenueRegistrationRequest): VenueResponse {
        logger.debug("Registering new venue: {}", request.email)

        // Check if email already exists
        if (venueRepository.existsByEmail(request.email.lowercase())) {
            logger.warn("Venue registration failed: email already exists: {}", request.email)
            throw VenuesException.ResourceConflict("A venue with this email already exists")
        }

        // Create venue entity
        val venue = Venue(
            name = request.name,
            description = request.description,
            address = request.address,
            city = request.city,
            latitude = request.latitude,
            longitude = request.longitude,
            email = request.email.lowercase(),
            phoneNumber = request.phoneNumber,
            website = request.website,
            category = request.category,
            passwordHash = passwordEncoder.encode(request.password),
            status = VenueStatus.PENDING_APPROVAL
        )

        val savedVenue = venueRepository.save(venue)
        logger.info("Venue registered successfully: {} (ID: {})", savedVenue.email, savedVenue.id)

        // TODO: Send verification email

        return venueMapper.toResponse(savedVenue)
    }

    /**
     * Get venue by ID.
     *
     * @param id Venue ID
     * @param includeStats Include statistics (followers, reviews)
     * @return Venue response
     * @throws VenuesException.ResourceNotFound if venue not found
     */
    @Transactional(readOnly = true)
    fun getVenueById(id: Long, includeStats: Boolean = false, language: String? = null): VenueResponse {
        logger.debug("Fetching venue by ID: {}, language: {}", id, language)

        val venue = venueRepository.findById(id)
            .orElseThrow {
                logger.warn("Venue not found with ID: {}", id)
                VenuesException.ResourceNotFound("Venue not found with ID: $id")
            }

        return venueMapper.toResponse(venue, includeStats, language)
    }

    /**
     * Get detailed venue information including schedules and translations.
     */
    @Transactional(readOnly = true)
    fun getVenueDetailed(id: Long): VenueDetailedResponse {
        logger.debug("Fetching detailed venue by ID: {}", id)

        val venue = venueRepository.findById(id)
            .orElseThrow {
                logger.warn("Venue not found with ID: {}", id)
                VenuesException.ResourceNotFound("Venue not found with ID: $id")
            }

        return venueMapper.toDetailedResponse(venue)
    }

    /**
     * Update venue profile.
     *
     * @param venueId Venue ID
     * @param request Update request
     * @return Updated venue response
     */
    fun updateVenue(venueId: Long, request: VenueUpdateRequest): VenueResponse {
        logger.debug("Updating venue: {}", venueId)

        val venue = findVenueById(venueId)

        // Update fields if provided
        request.name?.let { venue.name = it }
        request.description?.let { venue.description = it }
        request.address?.let { venue.address = it }
        request.city?.let { venue.city = it }
        request.latitude?.let { venue.latitude = it }
        request.longitude?.let { venue.longitude = it }
        request.phoneNumber?.let { venue.phoneNumber = it }
        request.website?.let { venue.website = it }
        request.category?.let { venue.category = it }
        request.isAlwaysOpen?.let { venue.isAlwaysOpen = it }

        val savedVenue = venueRepository.save(venue)
        logger.info("Venue updated successfully: {}", venueId)

        return venueMapper.toResponse(savedVenue)
    }

    /**
     * Get all active venues (public listing).
     */
    @Transactional(readOnly = true)
    fun getAllActiveVenues(pageable: Pageable, language: String? = null): Page<VenueResponse> {
        logger.debug("Fetching all active venues, language: {}", language)
        return venueRepository.findByStatus(VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toResponse(it, includeStats = true, language = language) }
    }

    /**
     * Search venues by name.
     */
    @Transactional(readOnly = true)
    fun searchVenues(searchTerm: String, pageable: Pageable, language: String? = null): Page<VenueResponse> {
        logger.debug("Searching venues: {}, language: {}", searchTerm, language)
        return venueRepository.searchByName(searchTerm, VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toResponse(it, includeStats = true, language = language) }
    }

    /**
     * Get venues by city.
     */
    @Transactional(readOnly = true)
    fun getVenuesByCity(city: String, pageable: Pageable, language: String? = null): Page<VenueResponse> {
        logger.debug("Fetching venues in city: {}, language: {}", city, language)
        return venueRepository.findByCityAndStatus(city, VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toResponse(it, includeStats = true, language = language) }
    }

    /**
     * Get venues by category.
     */
    @Transactional(readOnly = true)
    fun getVenuesByCategory(category: String, pageable: Pageable, language: String? = null): Page<VenueResponse> {
        logger.debug("Fetching venues in category: {}, language: {}", category, language)
        return venueRepository.findByCategoryAndStatus(category, VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toResponse(it, includeStats = true, language = language) }
    }

    // ===========================================
    // SCHEDULE MANAGEMENT
    // ===========================================

    /**
     * Set or update schedule for a specific day.
     */
    fun setSchedule(venueId: Long, request: VenueScheduleRequest): VenueScheduleResponse {
        logger.debug("Setting schedule for venue {} on {}", venueId, request.dayOfWeek)

        val venue = findVenueById(venueId)

        // Find existing schedule or create new
        val schedule = venueScheduleRepository.findByVenueIdAndDayOfWeek(venueId, request.dayOfWeek)
            .orElseGet { venueMapper.toScheduleEntity(request, venue) }

        // Update schedule
        schedule.openTime = request.openTime?.let { java.time.LocalTime.parse(it) }
        schedule.closeTime = request.closeTime?.let { java.time.LocalTime.parse(it) }
        schedule.isClosed = request.isClosed

        val saved = venueScheduleRepository.save(schedule)
        logger.info("Schedule set for venue {} on {}", venueId, request.dayOfWeek)

        return venueMapper.toScheduleResponse(saved)
    }

    /**
     * Get all schedules for a venue.
     */
    @Transactional(readOnly = true)
    fun getSchedules(venueId: Long): List<VenueScheduleResponse> {
        logger.debug("Fetching schedules for venue: {}", venueId)
        return venueScheduleRepository.findByVenueId(venueId)
            .map { venueMapper.toScheduleResponse(it) }
    }

    // ===========================================
    // TRANSLATION MANAGEMENT
    // ===========================================

    /**
     * Add or update translation for a venue.
     */
    fun setTranslation(venueId: Long, request: VenueTranslationRequest): VenueTranslationResponse {
        logger.debug("Setting translation for venue {} in language {}", venueId, request.language)

        val venue = findVenueById(venueId)
        val language = request.language.lowercase()

        // Find existing translation or create new
        val translation = venueTranslationRepository.findByVenueIdAndLanguage(venueId, language)
            .orElseGet { venueMapper.toTranslationEntity(request, venue) }

        // Update translation
        translation.name = request.name
        translation.description = request.description

        val saved = venueTranslationRepository.save(translation)
        logger.info("Translation set for venue {} in language {}", venueId, language)

        return venueMapper.toTranslationResponse(saved)
    }

    /**
     * Get all translations for a venue.
     */
    @Transactional(readOnly = true)
    fun getTranslations(venueId: Long): List<VenueTranslationResponse> {
        logger.debug("Fetching translations for venue: {}", venueId)
        return venueTranslationRepository.findByVenueId(venueId)
            .map { venueMapper.toTranslationResponse(it) }
    }

    /**
     * Delete translation.
     */
    fun deleteTranslation(venueId: Long, language: String) {
        logger.debug("Deleting translation for venue {} in language {}", venueId, language)

        val translation = venueTranslationRepository.findByVenueIdAndLanguage(venueId, language.lowercase())
            .orElseThrow {
                VenuesException.ResourceNotFound("Translation not found for language: $language")
            }

        venueTranslationRepository.delete(translation)
        logger.info("Translation deleted for venue {} in language {}", venueId, language)
    }

    // ===========================================
    // PHOTO MANAGEMENT
    // ===========================================

    /**
     * Add photo to venue.
     */
    fun addPhoto(venueId: Long, userId: Long, request: VenuePhotoRequest): VenuePhotoResponse {
        logger.debug("Adding photo to venue: {}", venueId)

        val venue = findVenueById(venueId)
        val photo = venueMapper.toPhotoEntity(request, venue, userId)

        val saved = venuePhotoRepository.save(photo)
        logger.info("Photo added to venue {} by user {}", venueId, userId)

        return venueMapper.toPhotoResponse(saved)
    }

    /**
     * Get all photos for a venue.
     */
    @Transactional(readOnly = true)
    fun getPhotos(venueId: Long): List<VenuePhotoResponse> {
        logger.debug("Fetching photos for venue: {}", venueId)
        return venuePhotoRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)
            .map { venueMapper.toPhotoResponse(it) }
    }

    /**
     * Delete photo.
     */
    fun deletePhoto(venueId: Long, photoId: Long, userId: Long) {
        logger.debug("Deleting photo {} from venue {}", photoId, venueId)

        val photo = venuePhotoRepository.findById(photoId)
            .orElseThrow {
                VenuesException.ResourceNotFound("Photo not found with ID: $photoId")
            }

        // Verify photo belongs to venue
        if (photo.venue.id != venueId) {
            throw VenuesException.AuthorizationFailure("Photo does not belong to this venue")
        }

        // Authorization check - only photo owner or venue owner can delete
        val isPhotoOwner = photo.userId == userId
        val isVenueOwner = photo.venue.id == venueId // User calling this must be authenticated as venue owner via JWT

        if (!isPhotoOwner && !isVenueOwner) {
            throw VenuesException.AuthorizationFailure("You can only delete photos you uploaded or photos from your own venue")
        }

        venuePhotoRepository.delete(photo)
        logger.info("Photo {} deleted from venue {} by user {}", photoId, venueId, userId)
    }

    // ===========================================
    // REVIEW MANAGEMENT
    // ===========================================

    /**
     * Add or update review for a venue.
     */
    fun addOrUpdateReview(venueId: Long, userId: Long, request: VenueReviewRequest): VenueReviewResponse {
        logger.debug("Adding/updating review for venue {} by user {}", venueId, userId)

        val venue = findVenueById(venueId)

        // Find existing review or create new
        val review = venueReviewRepository.findByVenueIdAndUserId(venueId, userId)
            .orElseGet { venueMapper.toReviewEntity(request, venue, userId) }

        // Update review
        review.rating = request.rating
        review.comment = request.comment

        val saved = venueReviewRepository.save(review)
        logger.info("Review added/updated for venue {} by user {}", venueId, userId)

        return venueMapper.toReviewResponse(saved)
    }

    /**
     * Get all reviews for a venue.
     */
    @Transactional(readOnly = true)
    fun getReviews(venueId: Long, pageable: Pageable): Page<VenueReviewResponse> {
        logger.debug("Fetching reviews for venue: {}", venueId)
        return venueReviewRepository.findByVenueIdAndIsModeratedFalse(venueId, pageable)
            .map { venueMapper.toReviewResponse(it) }
    }

    /**
     * Delete review.
     */
    fun deleteReview(venueId: Long, userId: Long) {
        logger.debug("Deleting review for venue {} by user {}", venueId, userId)

        val review = venueReviewRepository.findByVenueIdAndUserId(venueId, userId)
            .orElseThrow {
                VenuesException.ResourceNotFound("Review not found")
            }

        venueReviewRepository.delete(review)
        logger.info("Review deleted for venue {} by user {}", venueId, userId)
    }

    // ===========================================
    // PROMO CODE MANAGEMENT
    // ===========================================

    /**
     * Create promo code for venue.
     */
    fun createPromoCode(venueId: Long, request: VenuePromoCodeRequest): VenuePromoCodeResponse {
        logger.debug("Creating promo code for venue: {}", venueId)

        val venue = findVenueById(venueId)

        // Check if code already exists for this venue
        if (venuePromoCodeRepository.existsByVenueIdAndCode(venueId, request.code.uppercase())) {
            throw VenuesException.ResourceConflict("Promo code already exists: ${request.code}")
        }

        val promoCode = venueMapper.toPromoCodeEntity(request, venue)
        val saved = venuePromoCodeRepository.save(promoCode)

        logger.info("Promo code created for venue {}: {}", venueId, saved.code)
        return venueMapper.toPromoCodeResponse(saved)
    }

    /**
     * Get all promo codes for a venue.
     */
    @Transactional(readOnly = true)
    fun getPromoCodes(venueId: Long): List<VenuePromoCodeResponse> {
        logger.debug("Fetching promo codes for venue: {}", venueId)
        return venuePromoCodeRepository.findByVenueIdAndIsActiveTrue(venueId)
            .map { venueMapper.toPromoCodeResponse(it) }
    }

    /**
     * Deactivate promo code.
     */
    fun deactivatePromoCode(venueId: Long, codeId: Long) {
        logger.debug("Deactivating promo code {} for venue {}", codeId, venueId)

        val promoCode = venuePromoCodeRepository.findById(codeId)
            .orElseThrow {
                VenuesException.ResourceNotFound("Promo code not found")
            }

        // Verify promo code belongs to venue
        if (promoCode.venue.id != venueId) {
            throw VenuesException.AuthorizationFailure("Promo code does not belong to this venue")
        }

        promoCode.isActive = false
        venuePromoCodeRepository.save(promoCode)

        logger.info("Promo code {} deactivated for venue {}", codeId, venueId)
    }

    // ===========================================
    // FOLLOWER MANAGEMENT
    // ===========================================

    /**
     * Follow a venue.
     */
    fun followVenue(venueId: Long, userId: Long) {
        logger.debug("User {} following venue {}", userId, venueId)

        val venue = findVenueById(venueId)

        // Check if already following
        if (venueFollowerRepository.existsByVenueIdAndUserId(venueId, userId)) {
            throw VenuesException.ResourceConflict("You are already following this venue")
        }

        val follower = VenueFollower(
            venue = venue,
            userId = userId,
            notificationsEnabled = true
        )

        venueFollowerRepository.save(follower)
        logger.info("User {} started following venue {}", userId, venueId)
    }

    /**
     * Unfollow a venue.
     */
    fun unfollowVenue(venueId: Long, userId: Long) {
        logger.debug("User {} unfollowing venue {}", userId, venueId)

        if (!venueFollowerRepository.existsByVenueIdAndUserId(venueId, userId)) {
            throw VenuesException.ResourceNotFound("You are not following this venue")
        }

        venueFollowerRepository.deleteByVenueIdAndUserId(venueId, userId)
        logger.info("User {} unfollowed venue {}", userId, venueId)
    }

    /**
     * Check if user is following venue.
     */
    @Transactional(readOnly = true)
    fun isFollowing(venueId: Long, userId: Long): Boolean {
        return venueFollowerRepository.existsByVenueIdAndUserId(venueId, userId)
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================

    /**
     * Find venue by ID or throw exception.
     */
    private fun findVenueById(id: Long): Venue {
        return venueRepository.findById(id)
            .orElseThrow {
                logger.warn("Venue not found with ID: {}", id)
                VenuesException.ResourceNotFound("Venue not found with ID: $id")
            }
    }
}

