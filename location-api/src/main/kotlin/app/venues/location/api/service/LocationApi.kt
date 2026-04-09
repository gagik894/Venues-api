package app.venues.location.api.service

import app.venues.location.api.dto.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Public API contract for Location module.
 *
 * Provides access to reference data for regions and cities.
 */
interface LocationApi {

    /**
     * Get all active regions.
     */
    fun getAllActiveRegions(lang: String = "en"): List<RegionResponse>

    /**
     * Get all regions (including inactive) for admin purposes.
     */
    fun getAllRegions(lang: String = "en"): List<RegionResponse>

    /**
     * Get region by ID.
     */
    fun getRegionById(id: Long, lang: String = "en"): RegionResponse

    /**
     * Get region by code (e.g., "AM-ER").
     */
    fun getRegionByCode(code: String, lang: String = "en"): RegionResponse

    /**
     * Create a new region (admin only).
     */
    fun createRegion(request: CreateRegionRequest): RegionResponse

    /**
     * Update an existing region (admin only).
     */
    fun updateRegion(code: String, request: UpdateRegionRequest): RegionResponse

    /**
     * Get all active cities.
     */
    fun getAllActiveCities(lang: String = "en"): List<CityResponse>

    /**
     * Get active cities by region code.
     */
    fun getCitiesByRegionCode(regionCode: String, lang: String = "en"): List<CityResponse>

    /**
     * Get city by slug.
     */
    fun getCityBySlug(slug: String, lang: String = "en"): CityResponse

    /**
     * Get city by ID.
     */
    fun getCityById(id: Long, lang: String = "en"): CityResponse

    /**
     * Search cities by name (multilingual).
     */
    fun searchCities(searchTerm: String, pageable: Pageable, lang: String = "en"): Page<CityResponse>

    /**
     * Create a new city (admin only).
     */
    fun createCity(request: CreateCityRequest): CityResponse

    /**
     * Update an existing city (admin only).
     */
    fun updateCity(slug: String, request: UpdateCityRequest): CityResponse

    /**
     * Get lightweight city list for dropdowns.
     */
    fun getCitiesCompact(lang: String = "en"): List<CityCompact>
}
