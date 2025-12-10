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
     * Get organization by custom domain (for white-label sites).
     *
     * @param domain The custom domain (e.g., "ticketmaster.am")
     * @return OrganizationDto if found, null otherwise
     */
    fun getOrganizationByDomain(domain: String): OrganizationDto?
}
