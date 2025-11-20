package app.venues.organization.api.dto

import java.util.*

/**
 * Data Transfer Object for Organization.
 */
data class OrganizationDto(
    val id: UUID,
    val name: String,
    val slug: String,
    val defaultMerchantProfileId: UUID?
)
