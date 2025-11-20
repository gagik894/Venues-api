package app.venues.location.service

import app.venues.common.exception.VenuesException
import app.venues.location.api.dto.*
import app.venues.location.domain.City
import app.venues.location.domain.Region
import app.venues.location.repository.CityRepository
import app.venues.location.repository.RegionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for managing reference location data (regions and cities).
 *
 * This service provides operations for:
 * - Querying regions and cities (public, cacheable)
 * - Creating/updating reference data (admin only)
 * - Validating location data integrity
 *
 * Design Principles:
 * - Read operations are optimized and cacheable
 * - Write operations are admin-only with strict validation
 * - Reference data changes should be rare and deliberate
 * - Soft-delete for historical data integrity
 *
 * Caching Strategy:
 * Reference data is ideal for caching:
 * - Regions: Cache for 24 hours (rarely change)
 * - Cities: Cache for 12 hours (rarely change)
 * - Invalidate cache on admin updates
 */
@Service
@Transactional(readOnly = true)
class LocationService(
    private val regionRepository: RegionRepository,
    private val cityRepository: CityRepository
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // REGION OPERATIONS
    // ===========================================

    /**
     * Get all active regions.
     *
     * Returns regions in display order for UI dropdowns and selection lists.
     * This method is highly cacheable.
     *
     * @return List of active regions
     */
    fun getAllActiveRegions(): List<RegionResponse> {
        logger.debug { "Fetching all active regions" }
        return regionRepository.findAllActive().map { RegionResponse.from(it) }
    }

    /**
     * Get all regions (including inactive) for admin purposes.
     *
     * @return List of all regions
     */
    fun getAllRegions(): List<RegionResponse> {
        logger.debug { "Fetching all regions (admin)" }
        return regionRepository.findAll().map { RegionResponse.from(it) }
    }

    /**
     * Get region by ID.
     *
     * @param id Region ID
     * @return Region data
     * @throws VenuesException.ResourceNotFound if region not found
     */
    fun getRegionById(id: Long): RegionResponse {
        logger.debug { "Fetching region by ID: $id" }
        val region = regionRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "Region not found with ID: $id",
                errorCode = "REGION_NOT_FOUND"
            )
        }
        return RegionResponse.from(region)
    }

    /**
     * Get region by code.
     *
     * @param code ISO/government code (e.g., "AM-ER")
     * @return Region data
     * @throws VenuesException.ResourceNotFound if region not found
     */
    fun getRegionByCode(code: String): RegionResponse {
        logger.debug { "Fetching region by code: $code" }
        val region = regionRepository.findByCode(code) ?: throw VenuesException.ResourceNotFound(
            message = "Region not found with code: $code",
            errorCode = "REGION_NOT_FOUND"
        )
        return RegionResponse.from(region)
    }

    /**
     * Create a new region (admin only).
     *
     * @param request Region creation data
     * @return Created region
     * @throws VenuesException.ResourceConflict if code already exists
     */
    @Transactional
    fun createRegion(request: CreateRegionRequest): RegionResponse {
        logger.info { "Creating new region: ${request.code}" }

        // Validate unique code
        if (regionRepository.existsByCode(request.code)) {
            throw VenuesException.ResourceConflict(
                message = "Region code already exists: ${request.code}",
                errorCode = "REGION_CODE_EXISTS"
            )
        }

        val region = Region(
            code = request.code,
            names = request.names,
            displayOrder = request.displayOrder,
            isActive = true
        )

        val saved = regionRepository.save(region)
        logger.info { "Region created: ${saved.id}" }

        return RegionResponse.from(saved)
    }

    /**
     * Update an existing region (admin only).
     *
     * @param id Region ID
     * @param request Update data
     * @return Updated region
     * @throws VenuesException.ResourceNotFound if region not found
     */
    @Transactional
    fun updateRegion(id: Long, request: UpdateRegionRequest): RegionResponse {
        logger.info { "Updating region: $id" }

        val region = regionRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "Region not found with ID: $id",
                errorCode = "REGION_NOT_FOUND"
            )
        }

        // Apply updates
        request.names?.let { region.names = it }
        request.displayOrder?.let { region.displayOrder = it }
        request.isActive?.let { region.isActive = it }

        val saved = regionRepository.save(region)
        logger.info { "Region updated: ${saved.id}" }

        return RegionResponse.from(saved)
    }

    // ===========================================
    // CITY OPERATIONS
    // ===========================================

    /**
     * Get all active cities.
     *
     * Returns cities in display order for UI dropdowns and selection lists.
     * This method is highly cacheable.
     *
     * @return List of active cities
     */
    fun getAllActiveCities(): List<CityResponse> {
        logger.debug { "Fetching all active cities" }
        return cityRepository.findAllActive().map { CityResponse.from(it) }
    }

    /**
     * Get active cities by region.
     *
     * @param regionId Parent region ID
     * @return List of active cities in the region
     */
    fun getCitiesByRegion(regionId: Long): List<CityResponse> {
        logger.debug { "Fetching cities for region: $regionId" }
        return cityRepository.findAllActiveByRegionId(regionId).map { CityResponse.from(it) }
    }

    /**
     * Get city by slug.
     *
     * @param slug City slug
     * @return City data
     * @throws VenuesException.ResourceNotFound if city not found
     */
    fun getCityBySlug(slug: String): CityResponse {
        logger.debug { "Fetching city by slug: $slug" }
        val city = cityRepository.findBySlug(slug) ?: throw VenuesException.ResourceNotFound(
            message = "City not found with slug: $slug",
            errorCode = "CITY_NOT_FOUND"
        )
        return CityResponse.from(city)
    }

    /**
     * Get city by ID.
     *
     * @param id City ID
     * @return City data
     * @throws VenuesException.ResourceNotFound if city not found
     */
    fun getCityById(id: Long): CityResponse {
        logger.debug { "Fetching city by ID: $id" }
        val city = cityRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "City not found with ID: $id",
                errorCode = "CITY_NOT_FOUND"
            )
        }
        return CityResponse.from(city)
    }

    /**
     * Search cities by name (multilingual).
     *
     * @param searchTerm Search string (partial match)
     * @param pageable Pagination parameters
     * @return Page of matching cities
     */
    fun searchCities(searchTerm: String, pageable: Pageable): Page<CityResponse> {
        logger.debug { "Searching cities: $searchTerm" }
        return cityRepository.searchByName(searchTerm, pageable).map { CityResponse.from(it) }
    }

    /**
     * Create a new city (admin only).
     *
     * @param request City creation data
     * @return Created city
     * @throws VenuesException.ResourceNotFound if region not found
     * @throws VenuesException.ResourceConflict if slug already exists
     */
    @Transactional
    fun createCity(request: CreateCityRequest): CityResponse {
        logger.info { "Creating new city: ${request.slug}" }

        // Validate unique slug
        if (cityRepository.existsBySlug(request.slug)) {
            throw VenuesException.ResourceConflict(
                message = "City slug already exists: ${request.slug}",
                errorCode = "CITY_SLUG_EXISTS"
            )
        }

        // Validate region exists
        val region = regionRepository.findById(request.regionId).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "Region not found with ID: ${request.regionId}",
                errorCode = "REGION_NOT_FOUND"
            )
        }

        val city = City(
            region = region,
            slug = request.slug,
            names = request.names,
            officialId = request.officialId,
            displayOrder = request.displayOrder,
            isActive = true
        )

        val saved = cityRepository.save(city)
        logger.info { "City created: ${saved.id}" }

        return CityResponse.from(saved)
    }

    /**
     * Update an existing city (admin only).
     *
     * @param id City ID
     * @param request Update data
     * @return Updated city
     * @throws VenuesException.ResourceNotFound if city or region not found
     */
    @Transactional
    fun updateCity(id: Long, request: UpdateCityRequest): CityResponse {
        logger.info { "Updating city: $id" }

        val city = cityRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "City not found with ID: $id",
                errorCode = "CITY_NOT_FOUND"
            )
        }

        // Apply updates
        request.regionId?.let { regionId ->
            val region = regionRepository.findById(regionId).orElseThrow {
                VenuesException.ResourceNotFound(
                    message = "Region not found with ID: $regionId",
                    errorCode = "REGION_NOT_FOUND"
                )
            }
            city.region = region
        }

        request.names?.let { city.names = it }
        request.officialId?.let { city.officialId = it }
        request.displayOrder?.let { city.displayOrder = it }
        request.isActive?.let { city.isActive = it }

        val saved = cityRepository.save(city)
        logger.info { "City updated: ${saved.id}" }

        return CityResponse.from(saved)
    }

    /**
     * Get lightweight city list for dropdowns.
     *
     * @param lang Language code for names
     * @return List of compact city representations
     */
    fun getCitiesCompact(lang: String = "en"): List<CityCompact> {
        logger.debug { "Fetching compact city list (lang: $lang)" }
        return cityRepository.findAllActive().map { CityCompact.from(it, lang) }
    }
}

