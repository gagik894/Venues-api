package app.venues.location.service

import app.venues.common.exception.VenuesException
import app.venues.location.api.dto.*
import app.venues.location.api.service.LocationApi
import app.venues.location.domain.City
import app.venues.location.domain.Region
import app.venues.location.repository.CityRepository
import app.venues.location.repository.RegionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
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
) : LocationApi {
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
    @Cacheable("regions", unless = "#result == null or #result.isEmpty()")
    override fun getAllActiveRegions(lang: String): List<RegionResponse> {
        logger.debug { "Fetching all active regions (lang: $lang)" }
        return regionRepository.findAllActive().map { it.toResponse(lang) }
    }

    /**
     * Get all regions (including inactive) for admin purposes.
     *
     * @return List of all regions
     */
    @Cacheable("regionsAll", unless = "#result == null or #result.isEmpty()")
    override fun getAllRegions(lang: String): List<RegionResponse> {
        logger.debug { "Fetching all regions (admin, lang: $lang)" }
        return regionRepository.findAll().map { it.toResponse(lang) }
    }

    /**
     * Get region by ID.
     *
     * @param id Region ID
     * @return Region data
     * @throws VenuesException.ResourceNotFound if region not found
     */
    override fun getRegionById(id: Long, lang: String): RegionResponse {
        logger.debug { "Fetching region by ID: $id, lang: $lang" }
        val region = regionRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "Region not found with ID: $id",
                errorCode = "REGION_NOT_FOUND"
            )
        }
        return region.toResponse(lang)
    }

    /**
     * Get region by code.
     *
     * @param code ISO/government code (e.g., "AM-ER")
     * @return Region data
     * @throws VenuesException.ResourceNotFound if region not found
     */
    override fun getRegionByCode(code: String, lang: String): RegionResponse {
        logger.debug { "Fetching region by code: $code, lang: $lang" }
        val region = regionRepository.findByCode(code) ?: throw VenuesException.ResourceNotFound(
            message = "Region not found with code: $code",
            errorCode = "REGION_NOT_FOUND"
        )
        return region.toResponse(lang)
    }

    /**
     * Create a new region (admin only).
     *
     * @param request Region creation data
     * @return Created region
     * @throws VenuesException.ResourceConflict if code already exists
     */
    @Transactional
    @CacheEvict(cacheNames = ["regions", "regionsAll", "cities", "citiesByRegion"], allEntries = true)
    override fun createRegion(request: CreateRegionRequest): RegionResponse {
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

        return saved.toResponse()
    }

    /**
     * Update an existing region (admin only).
     *
     * @param code Region Code
     * @param request Update data
     * @return Updated region
     * @throws VenuesException.ResourceNotFound if region not found
     */
    @Transactional
    @CacheEvict(cacheNames = ["regions", "regionsAll", "cities", "citiesByRegion"], allEntries = true)
    override fun updateRegion(code: String, request: UpdateRegionRequest): RegionResponse {
        logger.info { "Updating region: $code" }

        val region = regionRepository.findByCode(code) ?: throw VenuesException.ResourceNotFound(
            message = "Region not found with code: $code",
            errorCode = "REGION_NOT_FOUND"
        )

        // Apply updates
        request.names?.let { region.names = it }
        request.displayOrder?.let { region.displayOrder = it }
        request.isActive?.let { region.isActive = it }

        val saved = regionRepository.save(region)
        logger.info { "Region updated: ${saved.code}" }

        return saved.toResponse()
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
    @Cacheable("cities", unless = "#result == null or #result.isEmpty()")
    override fun getAllActiveCities(lang: String): List<CityResponse> {
        logger.debug { "Fetching all active cities (lang: $lang)" }
        return cityRepository.findAllActive().map { it.toResponse(lang) }
    }

    /**
     * Get active cities by region code.
     */
    @Cacheable(cacheNames = ["citiesByRegion"], key = "#regionCode", unless = "#result == null or #result.isEmpty()")
    override fun getCitiesByRegionCode(regionCode: String, lang: String): List<CityResponse> {
        logger.debug { "Fetching cities for region code: $regionCode, lang: $lang" }
        val region = regionRepository.findByCode(regionCode) ?: throw VenuesException.ResourceNotFound(
            "Region not found with code: $regionCode",
            "REGION_NOT_FOUND"
        )
        return cityRepository.findAllActiveByRegion(region).map { it.toResponse(lang) }
    }

    /**
     * Get city by slug.
     *
     * @param slug City slug
     * @return City data
     * @throws VenuesException.ResourceNotFound if city not found
     */
    override fun getCityBySlug(slug: String, lang: String): CityResponse {
        logger.debug { "Fetching city by slug: $slug, lang: $lang" }
        val city = cityRepository.findBySlug(slug) ?: throw VenuesException.ResourceNotFound(
            message = "City not found with slug: $slug",
            errorCode = "CITY_NOT_FOUND"
        )
        return city.toResponse(lang)
    }

    /**
     * Get city by ID.
     *
     * @param id City ID
     * @return City data
     * @throws VenuesException.ResourceNotFound if city not found
     */
    override fun getCityById(id: Long, lang: String): CityResponse {
        logger.debug { "Fetching city by ID: $id, lang: $lang" }
        val city = cityRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound(
                message = "City not found with ID: $id",
                errorCode = "CITY_NOT_FOUND"
            )
        }
        return city.toResponse(lang)
    }

    /**
     * Search cities by name (multilingual).
     *
     * @param searchTerm Search string (partial match)
     * @param pageable Pagination parameters
     * @return Page of matching cities
     */
    override fun searchCities(searchTerm: String, pageable: Pageable, lang: String): Page<CityResponse> {
        logger.debug { "Searching cities: $searchTerm, lang: $lang" }
        return cityRepository.searchByName(searchTerm, pageable).map { it.toResponse(lang) }
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
    @CacheEvict(cacheNames = ["cities", "citiesByRegion"], allEntries = true)
    override fun createCity(request: CreateCityRequest): CityResponse {
        logger.info { "Creating new city: ${request.slug}" }

        // Validate unique slug
        if (cityRepository.existsBySlug(request.slug)) {
            throw VenuesException.ResourceConflict(
                message = "City slug already exists: ${request.slug}",
                errorCode = "CITY_SLUG_EXISTS"
            )
        }

        // Validate region exists by code
        val region = regionRepository.findByCode(request.regionCode) ?: throw VenuesException.ResourceNotFound(
            message = "Region not found with code: ${request.regionCode}",
            errorCode = "REGION_NOT_FOUND"
        )

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

        return saved.toResponse()
    }

    /**
     * Update an existing city (admin only).
     *
     * @param slug City slug
     * @param request Update data
     * @return Updated city
     * @throws VenuesException.ResourceNotFound if city or region not found
     */
    @Transactional
    @CacheEvict(cacheNames = ["cities", "citiesByRegion"], allEntries = true)
    override fun updateCity(slug: String, request: UpdateCityRequest): CityResponse {
        logger.info { "Updating city: $slug" }

        val city = cityRepository.findBySlug(slug) ?: throw VenuesException.ResourceNotFound(
            message = "City not found with slug: $slug",
            errorCode = "CITY_NOT_FOUND"
        )

        // Apply updates
        request.regionCode?.let { regionCode ->
            val region = regionRepository.findByCode(regionCode) ?: throw VenuesException.ResourceNotFound(
                message = "Region not found with code: $regionCode",
                errorCode = "REGION_NOT_FOUND"
            )
            city.region = region
        }

        request.names?.let { city.names = it }
        request.officialId?.let { city.officialId = it }
        request.displayOrder?.let { city.displayOrder = it }
        request.isActive?.let { city.isActive = it }

        val saved = cityRepository.save(city)
        logger.info { "City updated: ${saved.id}" }

        return saved.toResponse()
    }

    /**
     * Get lightweight city list for dropdowns.
     *
     * @param lang Language code for names
     * @return List of compact city representations
     */
    override fun getCitiesCompact(lang: String): List<CityCompact> {
        logger.debug { "Fetching compact city list (lang: $lang)" }
        return cityRepository.findAllActive().map { it.toCompact(lang) }
    }
}

