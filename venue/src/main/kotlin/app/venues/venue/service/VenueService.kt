package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.location.repository.CityRepository
import app.venues.venue.api.dto.*
import app.venues.venue.api.mapper.VenueMapper
import app.venues.venue.domain.VenueStatus
import app.venues.venue.repository.VenueCategoryRepository
import app.venues.venue.repository.VenueRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for venue management operations.
 * Implements business logic for venue CRUD, search, and filtering.
 *
 * Key Principles:
 * - Uses slugs (not UUIDs) for public API operations
 * - All public responses localized via lang parameter
 * - Uses category codes (not IDs) for filtering
 * - Validates all inputs before persistence
 */
@Service
@Transactional(readOnly = true)
class VenueService(
    private val venueRepository: VenueRepository,
    private val cityRepository: CityRepository,
    private val categoryRepository: VenueCategoryRepository,
    private val venueMapper: VenueMapper
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // PUBLIC API
    // ===========================================

    /**
     * Gets venue by ID (public endpoint).
     *
     * @param id Venue UUID
     * @param lang Language code for localization
     * @return Detailed venue information
     * @throws VenuesException.ResourceNotFound if venue not found or not active
     */
    fun getVenue(id: UUID, lang: String = "en"): VenueDetailResponse {
        logger.debug { "Fetching venue by ID: $id, lang: $lang" }

        val venue = venueRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "Venue not found",
                errorCode = "VENUE_NOT_FOUND"
            )
        }

        if (venue.status != VenueStatus.ACTIVE) {
            throw VenuesException.ResourceNotFound(
                message = "Venue not available",
                errorCode = "VENUE_NOT_AVAILABLE"
            )
        }

        return venueMapper.toDetailResponse(venue, lang)
    }

    /**
     * Gets venue by slug (supplementary SEO-friendly endpoint).
     * Redirects to UUID-based response.
     *
     * @param slug Venue slug
     * @param lang Language code for localization
     * @return Detailed venue information
     * @throws VenuesException.ResourceNotFound if venue not found or not active
     */
    fun getVenueBySlug(slug: String, lang: String = "en"): VenueDetailResponse {
        logger.debug { "Fetching venue by slug: $slug, lang: $lang" }

        val venue = venueRepository.findBySlug(slug)
            ?: throw VenuesException.ResourceNotFound(
                message = "Venue not found",
                errorCode = "VENUE_NOT_FOUND"
            )

        if (venue.status != VenueStatus.ACTIVE) {
            throw VenuesException.ResourceNotFound(
                message = "Venue not available",
                errorCode = "VENUE_NOT_AVAILABLE"
            )
        }

        return venueMapper.toDetailResponse(venue, lang)
    }

    /**
     * Lists active venues with pagination.
     *
     * @param pageable Pagination parameters
     * @param lang Language code for localization
     * @return Page of venues
     */
    fun listVenues(pageable: Pageable, lang: String = "en"): Page<VenueResponse> {
        logger.debug { "Listing active venues, lang: $lang" }

        return venueRepository.findByStatus(VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toPublicResponse(it, lang, includeStats = true) }
    }

    /**
     * Searches venues by name.
     *
     * @param query Search query
     * @param pageable Pagination parameters
     * @param lang Language code for localization
     * @return Page of matching venues
     */
    fun searchVenues(
        query: String,
        pageable: Pageable,
        lang: String = "en"
    ): Page<VenueResponse> {
        logger.debug { "Searching venues: query=$query, lang=$lang" }

        return venueRepository.searchByName(query, VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toPublicResponse(it, lang, includeStats = true) }
    }

    /**
     * Lists venues by city slug.
     *
     * @param citySlug City slug (e.g., "yerevan")
     * @param pageable Pagination parameters
     * @param lang Language code for localization
     * @return Page of venues in the city
     */
    fun listVenuesByCity(
        citySlug: String,
        pageable: Pageable,
        lang: String = "en"
    ): Page<VenueResponse> {
        logger.debug { "Listing venues by city: $citySlug, lang: $lang" }

        return venueRepository.findByCitySlugAndStatus(citySlug, VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toPublicResponse(it, lang, includeStats = true) }
    }

    /**
     * Lists venues by region code.
     *
     * @param regionCode ISO region code (e.g., "AM-ER")
     * @param pageable Pagination parameters
     * @param lang Language code for localization
     * @return Page of venues in the region
     */
    fun listVenuesByRegion(
        regionCode: String,
        pageable: Pageable,
        lang: String = "en"
    ): Page<VenueResponse> {
        logger.debug { "Listing venues by region: $regionCode, lang: $lang" }

        return venueRepository.findByRegionCodeAndStatus(regionCode, VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toPublicResponse(it, lang, includeStats = true) }
    }

    /**
     * Lists venues by category code.
     *
     * @param categoryCode Category code (e.g., "OPERA", "MUSEUM")
     * @param pageable Pagination parameters
     * @param lang Language code for localization
     * @return Page of venues in the category
     */
    fun listVenuesByCategory(
        categoryCode: String,
        pageable: Pageable,
        lang: String = "en"
    ): Page<VenueResponse> {
        logger.debug { "Listing venues by category: $categoryCode, lang: $lang" }

        return venueRepository.findByCategoryCodeAndStatus(categoryCode, VenueStatus.ACTIVE, pageable)
            .map { venueMapper.toPublicResponse(it, lang, includeStats = true) }
    }

    // ===========================================
    // ADMIN/OWNER API
    // ===========================================

    /**
     * Creates a new venue (admin/owner only).
     *
     * @param request Venue creation data
     * @return Created venue (admin response)
     * @throws VenuesException.ResourceConflict if slug exists
     * @throws VenuesException.ValidationFailure if city/category invalid
     */
    @Transactional
    fun createVenue(request: CreateVenueRequest): VenueAdminResponse {
        logger.info { "Creating venue: slug=${request.slug}" }

        // Validate slug uniqueness
        if (venueRepository.existsBySlug(request.slug)) {
            throw VenuesException.ResourceConflict(
                message = "Venue slug already exists",
                errorCode = "SLUG_EXISTS"
            )
        }

        // Fetch and validate city
        val city = cityRepository.findById(request.cityId).orElseThrow {
            VenuesException.ValidationFailure(
                message = "Invalid city ID",
                errorCode = "INVALID_CITY"
            )
        }

        // Fetch and validate category (optional)
        val category = request.categoryCode?.let { code ->
            categoryRepository.findByCodeAndIsActive(code, true)
                ?: throw VenuesException.ValidationFailure(
                    message = "Invalid category code",
                    errorCode = "INVALID_CATEGORY"
                )
        }

        val venue = venueMapper.toEntity(request, city, category)
        val saved = venueRepository.save(venue)

        logger.info { "Venue created: id=${saved.id}, slug=${saved.slug}" }
        return venueMapper.toAdminResponse(saved)
    }

    /**
     * Updates venue by UUID (owner/admin only).
     * Best practice: Always use UUIDs for CRUD operations on user-generated content.
     *
     * @param id Venue UUID
     * @param request Update data
     * @return Updated venue (admin response)
     * @throws VenuesException.ResourceNotFound if venue not found
     * @throws VenuesException.ValidationFailure if city/category invalid
     */
    @Transactional
    fun updateVenue(id: UUID, request: UpdateVenueRequest): VenueAdminResponse {
        logger.info { "Updating venue: id=$id" }

        val venue = venueRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "Venue not found",
                errorCode = "VENUE_NOT_FOUND"
            )
        }

        // Fetch new city if provided
        val city = request.cityId?.let { cityId ->
            cityRepository.findById(cityId).orElseThrow {
                VenuesException.ValidationFailure(
                    message = "Invalid city ID",
                    errorCode = "INVALID_CITY"
                )
            }
        }

        // Fetch new category if provided
        val category = request.categoryCode?.let { code ->
            categoryRepository.findByCodeAndIsActive(code, true)
                ?: throw VenuesException.ValidationFailure(
                    message = "Invalid category code",
                    errorCode = "INVALID_CATEGORY"
                )
        }

        venueMapper.updateEntity(venue, request, city, category)
        val saved = venueRepository.save(venue)

        logger.info { "Venue updated: id=${saved.id}, slug=${saved.slug}" }
        return venueMapper.toAdminResponse(saved)
    }

    /**
     * Gets venue by ID (admin/internal use).
     *
     * @param id Venue UUID
     * @param lang Language code for localization
     * @return Venue admin response
     * @throws VenuesException.ResourceNotFound if not found
     */
    fun getVenueByIdAdmin(id: UUID, lang: String = "en"): VenueAdminResponse {
        logger.debug { "Fetching venue by ID (admin): $id" }

        val venue = venueRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "Venue not found",
                errorCode = "VENUE_NOT_FOUND"
            )
        }

        return venueMapper.toAdminResponse(venue, lang)
    }

    /**
     * Lists all venues (admin only, includes non-active).
     *
     * @param pageable Pagination parameters
     * @param lang Language code for localization
     * @return Page of all venues
     */
    fun listAllVenues(pageable: Pageable, lang: String = "en"): Page<VenueAdminResponse> {
        logger.debug { "Listing all venues (admin)" }

        return venueRepository.findAllNonDeleted(pageable)
            .map { venueMapper.toAdminResponse(it, lang) }
    }

    /**
     * Activates venue (admin only).
     *
     * @param id Venue UUID
     * @return Updated venue
     */
    @Transactional
    fun activateVenue(id: UUID): VenueAdminResponse {
        logger.info { "Activating venue: $id" }

        val venue = findVenueById(id)
        venue.activate()
        val saved = venueRepository.save(venue)

        logger.info { "Venue activated: ${saved.id}" }
        return venueMapper.toAdminResponse(saved)
    }

    /**
     * Suspends venue (admin only).
     *
     * @param id Venue UUID
     * @return Updated venue
     */
    @Transactional
    fun suspendVenue(id: UUID): VenueAdminResponse {
        logger.info { "Suspending venue: $id" }

        val venue = findVenueById(id)
        venue.suspend()
        val saved = venueRepository.save(venue)

        logger.info { "Venue suspended: ${saved.id}" }
        return venueMapper.toAdminResponse(saved)
    }

    /**
     * Soft-deletes venue (admin only).
     *
     * @param id Venue UUID
     */
    @Transactional
    fun deleteVenue(id: UUID) {
        logger.info { "Deleting venue: $id" }

        val venue = findVenueById(id)
        venue.delete()
        venueRepository.save(venue)

        logger.info { "Venue deleted: $id" }
    }

    // ===========================================
    // VENUE CATEGORIES
    // ===========================================

    /**
     * Lists all active venue categories.
     *
     * @param lang Language code for localization
     * @return List of categories
     */
    fun listCategories(lang: String = "en"): List<VenueCategoryDto> {
        logger.debug { "Listing venue categories, lang: $lang" }

        return categoryRepository.findAllActive()
            .map { venueMapper.toCategoryDto(it, lang) }
    }

    // ===========================================
    // PRIVATE HELPERS
    // ===========================================

    private fun findVenueById(id: UUID) =
        venueRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "Venue not found",
                errorCode = "VENUE_NOT_FOUND"
            )
        }
}

