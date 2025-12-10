package app.venues.location.api.dto

import app.venues.location.domain.City
import app.venues.location.domain.Region
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Response DTO for Region data.
 *
 * @property code ISO/government code (e.g., "AM-ER")
 * @property names Multilingual names
 * @property displayOrder Optional display order
 * @property isActive Active status
 */
data class RegionResponse(
    val code: String,
    val names: Map<String, String>,
    val name: String,
    val displayOrder: Int?,
    val isActive: Boolean
) {
    companion object {
        fun from(region: Region, lang: String = "en"): RegionResponse {
            return RegionResponse(
                code = region.code,
                names = region.names,
                name = region.getName(lang),
                displayOrder = region.displayOrder,
                isActive = region.isActive
            )
        }
    }
}

/**
 * Request DTO for creating a new region (admin only).
 *
 * @property code ISO/government code (unique, 2-10 characters)
 * @property names Multilingual names (at least "hy" and "en" required)
 * @property displayOrder Optional display order
 */
data class CreateRegionRequest(
    @field:NotBlank(message = "Region code is required")
    @field:Size(min = 2, max = 10, message = "Code must be 2-10 characters")
    @field:Pattern(regexp = "^[A-Z0-9-]+$", message = "Code must be uppercase alphanumeric with hyphens")
    val code: String,

    @field:Size(min = 2, message = "At least 2 language names required (hy, en)")
    val names: Map<String, String>,

    val displayOrder: Int? = null
) {
    init {
        require(names.containsKey("hy")) { "Armenian name (hy) is required" }
        require(names.containsKey("en")) { "English name (en) is required" }
    }
}

/**
 * Request DTO for updating a region (admin only).
 *
 * @property names Updated multilingual names
 * @property displayOrder Updated display order
 * @property isActive Updated active status
 */
data class UpdateRegionRequest(
    val names: Map<String, String>? = null,
    val displayOrder: Int? = null,
    val isActive: Boolean? = null
)

/**
 * Response DTO for City data.
 *
 * @property slug URL-friendly slug
 * @property names Multilingual names
 * @property region Parent region (compact representation)
 * @property officialId Optional cadastre ID
 * @property displayOrder Optional display order
 * @property isActive Active status
 */
data class CityResponse(
    val slug: String,
    val names: Map<String, String>,
    val name: String,
    val region: RegionCompact,
    val officialId: String?,
    val displayOrder: Int?,
    val isActive: Boolean
) {
    companion object {
        fun from(city: City, lang: String = "en"): CityResponse {
            return CityResponse(
                slug = city.slug,
                names = city.names,
                name = city.getName(lang),
                region = RegionCompact(
                    code = city.region.code,
                    name = city.region.getName(lang)
                ),
                officialId = city.officialId,
                displayOrder = city.displayOrder,
                isActive = city.isActive
            )
        }
    }
}

/**
 * Compact region representation (for embedding in city responses).
 *
 * @property code Region code
 * @property name Region name (single language)
 */
data class RegionCompact(
    val code: String,
    val name: String
)

/**
 * Request DTO for creating a new city (admin only).
 *
 * @property regionCode Parent region code (mandatory)
 * @property slug URL-friendly slug (unique, lowercase)
 * @property names Multilingual names (at least "hy" and "en" required)
 * @property officialId Optional cadastre ID
 * @property displayOrder Optional display order
 */
data class CreateCityRequest(
    val regionCode: String,

    @field:NotBlank(message = "City slug is required")
    @field:Size(min = 2, max = 100, message = "Slug must be 2-100 characters")
    @field:Pattern(
        regexp = "^[a-z0-9-]+$",
        message = "Slug must be lowercase alphanumeric with hyphens"
    )
    val slug: String,

    @field:Size(min = 2, message = "At least 2 language names required (hy, en)")
    val names: Map<String, String>,

    @field:Size(max = 50, message = "Official ID must not exceed 50 characters")
    val officialId: String? = null,

    val displayOrder: Int? = null
) {
    init {
        require(names.containsKey("hy")) { "Armenian name (hy) is required" }
        require(names.containsKey("en")) { "English name (en) is required" }
    }
}

/**
 * Request DTO for updating a city (admin only).
 *
 * @property regionCode Updated parent region ID
 * @property names Updated multilingual names
 * @property officialId Updated cadastre ID
 * @property displayOrder Updated display order
 * @property isActive Updated active status
 */
data class UpdateCityRequest(
    val regionCode: String? = null,
    val names: Map<String, String>? = null,
    val officialId: String? = null,
    val displayOrder: Int? = null,
    val isActive: Boolean? = null
)

/**
 * Lightweight city representation for dropdowns and lists.
 *
 * @property slug City slug
 * @property name City name (single language)
 * @property regionName Parent region name
 */
data class CityCompact(
    val slug: String,
    val name: String,
    val regionName: String
) {
    companion object {
        fun from(city: City, lang: String = "en"): CityCompact {
            return CityCompact(
                slug = city.slug,
                name = city.getName(lang),
                regionName = city.region.getName(lang)
            )
        }
    }
}

