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

    /**
     * List active organizations (paginated via limit/offset).
     */
    fun listOrganizations(
        limit: Int? = null,
        offset: Int? = null,
        includeInactive: Boolean = false
    ): List<OrganizationDto>

    /**
     * Get organization by custom domain (for white-label sites).
     *
     * @param domain The custom domain (e.g., "ticketmaster.am")
     * @return OrganizationDto if found, null otherwise
     */
    fun getOrganizationByDomain(domain: String): OrganizationDto?
}
