package app.venues.organization.api

import app.venues.organization.api.dto.OrganizationDto
import java.util.*

/**
 * Public API for Organization module.
 */
interface OrganizationApi {

    /**
     * Get basic organization information by ID.
     */
    fun getOrganization(id: UUID): OrganizationDto?
}
